package com.yt8492.doujinshimanager.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.ui.circledetail.CircleDetailDestination
import com.yt8492.doujinshimanager.ui.edit.EditDestination
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import com.yt8492.doujinshimanager.ui.search.SearchDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import io.github.aakira.napier.Napier

class DetailViewModel(
    private val id: DoujinshiId,
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _doujinshi = MutableStateFlow<Doujinshi?>(null)
    val doujinshi: StateFlow<Doujinshi?> = _doujinshi.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val result = doujinshiRepository.get(id)
                currentCoroutineContext().ensureActive()
                _doujinshi.value = result
                if (result == null) _destination.value = PopBackDestination
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Failed to refresh doujinshi", e)
            }
        }
    }

    fun onClickCircle(circle: Circle) {
        _destination.value = CircleDetailDestination(circle.id.value)
    }

    fun onClickAuthor(author: Author) {
        _destination.value = SearchDestination(
            authorIds = listOf(author.id.value)
        )
    }

    fun onClickTag(tag: Tag) {
        _destination.value = SearchDestination(
            tagIds = listOf(tag.id.value)
        )
    }

    fun onClickEvent(event: Event) {
        _destination.value = SearchDestination(
            eventId = event.id.value
        )
    }

    fun onBackPress() {
        _destination.value = PopBackDestination
    }

    fun onClickEdit() {
        _destination.value = EditDestination(id.value)
    }

    fun onCompleteNavigation() {
        _destination.value = null
    }
}
