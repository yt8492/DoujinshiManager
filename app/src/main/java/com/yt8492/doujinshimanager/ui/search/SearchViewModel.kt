package com.yt8492.doujinshimanager.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.converter.BindingModelConverter
import com.yt8492.doujinshimanager.ui.converter.DoujinshiSearchSpecConverter
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.DoujinshiPager
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

class SearchViewModel(
    initialSearchSpec: DoujinshiSearchSpec,
    private val doujinshiRepository: DoujinshiRepository,
    private val circleRepository: CircleRepository,
    private val authorRepository: AuthorRepository,
    private val tagRepository: TagRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {
    private val _bindingModel = MutableStateFlow(SearchBindingModel.empty().copy(
        title = initialSearchSpec.keyword.orEmpty(),
        circle = initialSearchSpec.circle?.let { Circle(it, "") },
        authors = initialSearchSpec.authors.map { com.yt8492.doujinshimanager.shared.domain.model.Author(it, "") },
        tags = initialSearchSpec.tags.map { com.yt8492.doujinshimanager.shared.domain.model.Tag(it, "") },
        event = initialSearchSpec.event?.let { Event(it, "", null) },
        pubTerm = initialSearchSpec.pubTerm,
        sortKey = initialSearchSpec.sortKey,
    ))
    val bindingModel: StateFlow<SearchBindingModel> = _bindingModel.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private var suggestJob: Job? = null

    private val pager = DoujinshiPager(viewModelScope, doujinshiRepository) { state ->
        _bindingModel.update {
            it.copy(
                searchResult = state.list.map(BindingModelConverter::convertToDoujinshiBindingModel),
                page = state.page,
                hasNextPage = state.hasNextPage,
                refreshing = state.refreshing,
                loadingMore = state.loadingMore,
                loadError = state.error,
            )
        }
    }

    init {
        pager.refresh(initialSearchSpec)
        viewModelScope.launch {
            val circle = initialSearchSpec.circle?.let { circleRepository.get(it) }
            val authors = initialSearchSpec.authors.map { authorRepository.get(it) }
            val tags = initialSearchSpec.tags.map { tagRepository.get(it) }
            val event = initialSearchSpec.event?.let { eventRepository.get(it) }
            _bindingModel.update { current ->
                current.copy(
                    circle = circle?.takeIf { it.id == current.circle?.id } ?: current.circle,
                    authors = current.authors.map { item -> authors.find { it.id == item.id } ?: item },
                    tags = current.tags.map { item -> tags.find { it.id == item.id } ?: item },
                    event = event?.takeIf { it.id == current.event?.id } ?: current.event,
                )
            }
        }
    }

    fun refresh() = pager.refresh()

    fun retry() = pager.retry()

    fun onInputTitle(title: String) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    title = title,
                )
            }
            search(new)
        }
    }

    fun toggleShowSearchOption() {
        _bindingModel.update {
            it.copy(
                isShowSearchOptions = !it.isShowSearchOptions,
            )
        }
    }

    fun onInputCircle(circle: String) {
        _bindingModel.update {
            it.copy(
                inputCircle = circle,
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedCircles = circleRepository.fuzzyFind(circle),
                )
            }
        }
    }

    fun onSelectCircle(circle: Circle) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    circle = circle,
                    inputCircle = "",
                    suggestedCircles = listOf(),
                )
            }
            search(new)
        }
    }

    fun onDeleteCircle() {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    circle = null,
                )
            }
            search(new)
        }
    }

    fun onInputAuthor(author: String) {
        _bindingModel.update {
            it.copy(
                inputAuthor = author
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedAuthors = authorRepository.fuzzyFind(author),
                )
            }
        }
    }

    fun onSelectAuthor(author: Author) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    authors = it.authors + author,
                    inputAuthor = "",
                    suggestedAuthors = listOf(),
                )
            }
            search(new)
        }
    }

    fun onDeleteAuthor(author: Author) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    authors = it.authors - author,
                )
            }
            search(new)
        }
    }

    fun onInputTag(tag: String) {
        _bindingModel.update {
            it.copy(
                inputTag = tag
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedTags = tagRepository.fuzzyFind(tag),
                )
            }
        }
    }

    fun onSelectTag(tag: Tag) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    tags = it.tags + tag,
                    inputTag = "",
                    suggestedTags = listOf(),
                )
            }
            search(new)
        }
    }

    fun onDeleteTag(tag: Tag) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    tags = it.tags - tag,
                )
            }
            search(new)
        }
    }

    fun onInputEvent(event: String) {
        _bindingModel.update {
            it.copy(
                inputEvent = event,
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedEvents = eventRepository.fuzzyFind(event),
                )
            }
        }
    }

    fun onSelectEvent(event: Event) {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    event = event,
                    inputEvent = "",
                    suggestedEvents = listOf(),
                )
            }
            search(new)
        }
    }

    fun onDeleteEvent() {
        viewModelScope.launch {
            val new = _bindingModel.updateAndGet {
                it.copy(
                    event = null,
                )
            }
            search(new)
        }
    }

    fun onClickItem(item: DoujinshiBindingModel) {
        _destination.value = DetailDestination(item.id.value)
    }

    fun loadMore() = pager.loadMore()

    fun onDismiss() {
        suggestJob?.cancel()
        suggestJob = null
        _bindingModel.update {
            it.copy(
                suggestedCircles = listOf(),
                suggestedAuthors = listOf(),
                suggestedTags = listOf(),
                suggestedEvents = listOf(),
            )
        }
    }

    fun onBackPress() {
        _destination.value = PopBackDestination
    }

    fun onCompleteNavigation() {
        _destination.value = null
    }

    private fun search(bindingModel: SearchBindingModel) {
        pager.refresh(DoujinshiSearchSpecConverter.convertToSearchSpec(bindingModel), reset = true)
    }
}
