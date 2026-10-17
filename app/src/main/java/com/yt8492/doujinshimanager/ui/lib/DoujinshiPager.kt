package com.yt8492.doujinshimanager.ui.lib

import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DoujinshiLoadError { REFRESH, LOAD_MORE }

internal data class DoujinshiPageState(
    val list: List<Doujinshi> = emptyList(),
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: DoujinshiLoadError? = null,
)

/** Serializes page requests and discards results superseded by a refresh or filter change.
 * Call from the main thread, as with the owning ViewModel's UI actions.
 */
internal class DoujinshiPager(
    private val scope: CoroutineScope,
    private val repository: DoujinshiRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onChange: (DoujinshiPageState) -> Unit,
) {
    private var state = DoujinshiPageState()
    private var searchSpec = DoujinshiSearchSpec()
    private var request: Job? = null
    private var generation = 0
    private var refreshLastPage = 0

    /** Invalidates in-flight reads while preserving the successfully loaded range. */
    fun cancel() {
        generation++
        request?.cancel()
        request = null
        publish(state.copy(refreshing = false, loadingMore = false, error = null))
    }

    fun refresh(spec: DoujinshiSearchSpec = searchSpec, reset: Boolean = false) {
        generation++
        val token = generation
        request?.cancel()
        searchSpec = spec
        refreshLastPage = if (reset) 0 else state.page
        publish(if (reset) DoujinshiPageState(refreshing = true) else state.copy(
            refreshing = true, loadingMore = false, error = null,
        ))
        val lastPage = refreshLastPage
        request = scope.launch {
            try {
                // Fetch the whole loaded range before publishing it atomically.
                val result = withContext(ioDispatcher) {
                    repository.search(spec, page = 0, size = (lastPage + 1) * PAGE_SIZE)
                }
                currentCoroutineContext().ensureActive()
                if (token != generation) return@launch
                publish(DoujinshiPageState(
                    list = result.list.distinctBy { it.id },
                    page = lastPage,
                    hasNextPage = result.hasNextPage,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) publish(state.copy(refreshing = false, error = DoujinshiLoadError.REFRESH))
            }
        }
    }

    fun loadMore() {
        if (state.refreshing || state.loadingMore || !state.hasNextPage || state.error != null) return
        val token = generation
        val nextPage = state.page + 1
        val spec = searchSpec
        // Reserve synchronously, before launch, so rapid calls cannot request the same page twice.
        publish(state.copy(loadingMore = true))
        request = scope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    repository.search(spec, page = nextPage, size = PAGE_SIZE)
                }
                currentCoroutineContext().ensureActive()
                if (token != generation) return@launch
                publish(state.copy(
                    list = (state.list + result.list).distinctBy { it.id },
                    page = nextPage,
                    hasNextPage = result.hasNextPage,
                    loadingMore = false,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) publish(state.copy(loadingMore = false, error = DoujinshiLoadError.LOAD_MORE))
            }
        }
    }

    fun retry() {
        when (state.error) {
            DoujinshiLoadError.REFRESH -> refresh()
            DoujinshiLoadError.LOAD_MORE -> {
                publish(state.copy(error = null))
                loadMore()
            }
            null -> Unit
        }
    }

    private fun publish(value: DoujinshiPageState) {
        state = value
        onChange(value)
    }

    private companion object { const val PAGE_SIZE = 30 }
}
