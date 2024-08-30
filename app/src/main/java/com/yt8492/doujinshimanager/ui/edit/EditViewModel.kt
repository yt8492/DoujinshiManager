package com.yt8492.doujinshimanager.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import com.yt8492.doujinshimanager.ui.converter.BindingModelConverter
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class EditViewModel(
    private val id: DoujinshiId,
    private val circleRepository: CircleRepository,
    private val authorRepository: AuthorRepository,
    private val tagRepository: TagRepository,
    private val eventRepository: EventRepository,
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _bindingModel = MutableStateFlow<EditBindingModel?>(null)
    val bindingModel: StateFlow<EditBindingModel?> = _bindingModel.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private var suggestJob: Job? = null
    
    init {
        viewModelScope.launch {
            val doujinshi = doujinshiRepository.get(id)
            _bindingModel.value = BindingModelConverter.convertToEditBindingModel(doujinshi)
        }
    }

    fun onInputTitle(value: String) {
        _bindingModel.update {
            it?.copy(
                title = value,
            )
        }
    }

    fun onInputCircle(value: String) {
        _bindingModel.update {
            it?.copy(
                inputCircle = value,
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it?.copy(
                    suggestedCircles = circleRepository.fuzzyFind(value),
                )
            }
        }
    }

    fun onSelectCircle(circle: Circle) {
        _bindingModel.update {
            it?.copy(
                circle = circle,
                inputCircle = "",
                suggestedCircles = emptyList(),
            )
        }
    }

    fun onDeleteCircle() {
        _bindingModel.update {
            it?.copy(
                circle = null,
            )
        }
    }

    fun onInputAuthor(value: String) {
        _bindingModel.update {
            it?.copy(
                inputAuthor = value,
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it?.copy(
                    suggestedAuthors = authorRepository.fuzzyFind(value),
                )
            }
        }
    }

    fun onEnterAuthor() {
        val authorName = _bindingModel.value?.inputAuthor ?: return
        viewModelScope.launch {
            val author = authorRepository.findByName(authorName)
               ?: Author.create(authorName).also {
                   authorRepository.save(it)
               }
            _bindingModel.update {
                it?.copy(
                    authors = it.authors + author,
                    inputAuthor = "",
                    suggestedAuthors = emptyList(),
                )
            }
        }
    }

    fun onSelectAuthor(author: Author) {
        val authors = _bindingModel.value?.authors ?: return
        if (authors.any { it == author }) {
            return
        }
        _bindingModel.update {
            it?.copy(
                authors = it.authors + author,
                inputAuthor = "",
                suggestedAuthors = emptyList(),
            )
        }
    }

    fun onDeleteAuthor(author: Author) {
        _bindingModel.update {
            it?.copy(
                authors = it.authors - author,
            )
        }
    }

    fun onInputTag(value: String) {
        _bindingModel.update {
            it?.copy(
                inputTag = value,
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it?.copy(
                    suggestedTags = tagRepository.fuzzyFind(value),
                )
            }
        }
    }

    fun onEnterTag() {
        val tagName = _bindingModel.value?.inputTag ?: return
        viewModelScope.launch {
            val tag = tagRepository.findByName(tagName)
                ?: Tag.create(tagName).also {
                    tagRepository.save(it)
                }
            _bindingModel.update {
                it?.copy(
                    tags = it.tags + tag,
                    inputTag = "",
                    suggestedTags = emptyList(),
                )
            }
        }
    }

    fun onSelectTag(tag: Tag) {
        val tags = _bindingModel.value?.tags ?: return
        if (tags.any { it == tag }) {
            return
        }
        _bindingModel.update {
            it?.copy(
                tags = it.tags + tag,
                inputTag = "",
                suggestedTags = emptyList(),
            )
        }
    }

    fun onDeleteTag(tag: Tag) {
        _bindingModel.update {
            it?.copy(
                tags = it.tags - tag,
            )
        }
    }

    fun onInputEvent(value: String) {
        _bindingModel.update {
            it?.copy(
                inputEvent = value,
            )
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it?.copy(
                    suggestedEvents = eventRepository.fuzzyFind(value)
                )
            }
        }
    }

    fun onEnterEventName() {
        val eventName = _bindingModel.value?.inputEvent ?: return
        viewModelScope.launch {
            val event = eventRepository.findByName(eventName)
            if (event == null) {
                _bindingModel.update {
                    it?.copy(
                        isShowEventDatePicker = true,
                    )
                }
            } else {
                _bindingModel.update {
                    it?.copy(
                        event = event,
                        inputEvent = "",
                    )
                }
            }
        }
    }

    fun onEnterEventDate(start: LocalDate?, end: LocalDate?) {
        val eventName = _bindingModel.value?.inputEvent ?: return
        val event = when {
            start != null && start == end -> Event.create(
                name = eventName,
                date = start,
            )
            start != null && end != null -> Event.create(
                name = eventName,
                term = Period(start, end),
            )
            else -> Event.create(
                name = eventName,
                term = null,
            )
        }
        viewModelScope.launch {
            eventRepository.save(event)
            _bindingModel.update {
                it?.copy(
                    event = event,
                    inputEvent = "",
                    isShowEventDatePicker = false,
                )
            }
        }
    }

    fun onSelectEvent(event: Event) {
        _bindingModel.update {
            it?.copy(
                event = event,
                inputEvent = "",
                suggestedEvents = listOf(),
            )
        }
    }

    fun onDeleteEvent() {
        _bindingModel.update {
            it?.copy(
                event = null,
            )
        }
    }

    fun onSelectPubDate(value: LocalDate?) {
        _bindingModel.update {
            it?.copy(
                pubDate = value,
            )
        }
    }

    fun onDeletePubDate() {
        _bindingModel.update {
            it?.copy(
                pubDate = null,
            )
        }
    }

    fun onSelectImages(paths: List<String>) {
        _bindingModel.update {
            it?.copy(
                imagePaths = it.imagePaths + paths,
            )
        }
    }

    fun onDeleteImage(path: String) {
        _bindingModel.update {
            it?.copy(
                imagePaths = it.imagePaths - path,
            )
        }
    }

    fun onClickSave() {
        suggestJob?.cancel()
        suggestJob = null
        val current = _bindingModel.value ?: return
        viewModelScope.launch {
            val circle = current.circle
                ?: circleRepository.findByName(current.inputCircle)
                ?: Circle.create(current.inputCircle).also {
                    circleRepository.save(it)
                }
            val doujinshi = Doujinshi(
                id = current.id,
                title = current.title,
                circle = circle,
                authors = current.authors,
                tags = current.tags,
                event = current.event,
                pubDate = current.pubDate,
                imagePaths = current.imagePaths,
                createdAt = current.createdAt,
            )
            doujinshiRepository.update(doujinshi)
            _destination.value = PopBackDestination
        }
    }

    fun onDismiss() {
        suggestJob?.cancel()
        suggestJob = null
        _bindingModel.update {
            it?.copy(
                suggestedCircles = listOf(),
                suggestedAuthors = listOf(),
                suggestedTags = listOf(),
                suggestedEvents = listOf(),
                isShowEventDatePicker = false,
            )
        }
    }

    fun onBackPress() {
        suggestJob?.cancel()
        suggestJob = null
        _destination.value = PopBackDestination
    }

    fun onCompleteNavigation() {
        _destination.value = null
    }
}
