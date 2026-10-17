package com.yt8492.doujinshimanager.ui.circledetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.converter.BindingModelConverter
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError
import com.yt8492.doujinshimanager.ui.lib.DoujinshiPager
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CircleDetailViewModel(
    private val id: CircleId,
    private val circleRepository: CircleRepository,
    private val doujinshiRepository: DoujinshiRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CircleDetailUiState.init(id))
    val uiState: StateFlow<CircleDetailUiState> = _uiState.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private var circleLoadJob: Job? = null
    private var circleLoaded = false

    private val pager = DoujinshiPager(viewModelScope, doujinshiRepository, ioDispatcher) { state ->
        _uiState.update {
            it.copy(
                bindingModel = it.bindingModel.copy(
                    list = state.list.map(BindingModelConverter::convertToDoujinshiBindingModel),
                    page = state.page,
                    hasNextPage = state.hasNextPage,
                ),
                refreshing = state.refreshing,
                loadingMore = state.loadingMore,
                loadError = state.error,
                hasLoaded = it.hasLoaded || (circleLoaded && !state.refreshing && state.error == null),
            )
        }
    }

    init { load() }

    fun load() {
        if (_uiState.value.saving) return
        circleLoadJob?.cancel()
        circleLoaded = false
        pager.cancel()
        _uiState.update { it.copy(refreshing = true, loadingMore = false, loadError = null) }
        circleLoadJob = viewModelScope.launch {
            try {
                val circle = withContext(ioDispatcher) { circleRepository.get(id) }
                coroutineContext.ensureActive()
                _uiState.update {
                    it.copy(bindingModel = it.bindingModel.copy(
                        circle = circle,
                        inputCircleName = if (it.bindingModel.isEditMode) it.bindingModel.inputCircleName else circle.name,
                    ))
                }
                circleLoaded = true
                pager.refresh(DoujinshiSearchSpec(circle = id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                _uiState.update { it.copy(refreshing = false, loadError = DoujinshiLoadError.REFRESH) }
            }
        }
    }

    fun onInputName(value: String) {
        if (!_uiState.value.canSave) return
        _uiState.update {
            it.copy(
                bindingModel = it.bindingModel.copy(
                    inputCircleName = value,
                ),
                saveError = false,
            )
        }
    }

    fun onClickSave() {
        val state = _uiState.value
        if (!state.canSave) return
        val current = state.bindingModel
        if (current.circle.name == current.inputCircleName) {
            _uiState.update {
                it.copy(bindingModel = it.bindingModel.copy(isEditMode = false), saveError = false)
            }
            return
        }
        // Reserve the write before launch so callbacks in the same frame cannot save twice.
        _uiState.update { it.copy(saving = true, saveError = false) }
        circleLoadJob?.cancel()
        pager.cancel()
        viewModelScope.launch {
            val circle = Circle(
                id = id,
                name = current.inputCircleName,
            )
            try {
                withContext(ioDispatcher) { circleRepository.update(circle) }
                _uiState.update {
                    it.copy(
                        bindingModel = it.bindingModel.copy(
                            circle = circle,
                            inputCircleName = circle.name,
                            isEditMode = false,
                            list = it.bindingModel.list.map { doujinshi ->
                                doujinshi.copy(circleName = circle.name)
                            },
                        ),
                        saving = false,
                    )
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                _uiState.update { it.copy(saving = false, saveError = true) }
            }
        }
    }

    fun onClickEdit() {
        if (!_uiState.value.canEdit) return
        _uiState.update {
            it.copy(
                bindingModel = it.bindingModel.copy(
                    isEditMode = true,
                )
            )
        }
    }

    fun loadMore() {
        if (_uiState.value.canLoadMore) pager.loadMore()
    }

    fun retry() {
        if (_uiState.value.saving) return
        when (_uiState.value.loadError) {
            DoujinshiLoadError.REFRESH -> load()
            DoujinshiLoadError.LOAD_MORE -> pager.retry()
            null -> Unit
        }
    }

    fun onClickItem(item: DoujinshiBindingModel) {
        _destination.value = DetailDestination(item.id.value)
    }

    fun onClickBack() {
        _destination.value = PopBackDestination
    }

    fun completeNavigation() {
        _destination.value = null
    }
}
