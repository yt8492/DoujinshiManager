package com.yt8492.doujinshimanager.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class RegisterViewModel(
    private val circleRepository: CircleRepository,
    private val authorRepository: AuthorRepository,
    private val tagRepository: TagRepository,
    private val eventRepository: EventRepository,
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _bindingModel = MutableStateFlow(RegisterBindingModel.empty())
    val bindingModel: StateFlow<RegisterBindingModel> = _bindingModel.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private var suggestJob: Job? = null

    fun onInputTitle(value: String) {
        _bindingModel.update {
            it.copy(
                title = value,
            )
        }
    }

    fun onFocusCircle() {
        suggestCircles()
    }

    fun onInputCircle(value: String) {
        _bindingModel.update {
            it.copy(
                inputCircle = value,
            )
        }
        suggestCircles(value)
    }

    private fun suggestCircles(input: String = "") {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedCircles = circleRepository.fuzzyFind(input),
                )
            }
        }
    }

    fun onSelectCircle(circle: Circle) {
        _bindingModel.update {
            it.copy(
                circle = circle,
                inputCircle = "",
                suggestedCircles = emptyList(),
            )
        }
    }

    fun onDeleteCircle() {
        _bindingModel.update {
            it.copy(
                circle = null,
            )
        }
    }

    fun onFocusAuthor() {
        suggestAuthors()
    }

    fun onInputAuthor(value: String) {
        _bindingModel.update {
            it.copy(
                inputAuthor = value,
            )
        }
        suggestAuthors(value)
    }

    private fun suggestAuthors(input: String = "") {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedAuthors = authorRepository.fuzzyFind(input),
                )
            }
        }
    }

    fun onEnterAuthor() {
        val authorName = _bindingModel.value.inputAuthor
        viewModelScope.launch {
            val author = authorRepository.findByName(authorName)
               ?: Author.create(authorName).also {
                   authorRepository.save(it)
               }
            _bindingModel.update {
                it.copy(
                    authors = it.authors + author,
                    inputAuthor = "",
                    suggestedAuthors = emptyList(),
                )
            }
        }
    }

    fun onSelectAuthor(author: Author) {
        if (_bindingModel.value.authors.any { it == author }) {
            return
        }
        _bindingModel.update {
            it.copy(
                authors = it.authors + author,
                inputAuthor = "",
                suggestedAuthors = emptyList(),
            )
        }
    }

    fun onDeleteAuthor(author: Author) {
        _bindingModel.update {
            it.copy(
                authors = it.authors - author,
            )
        }
    }

    fun onFocusTag() {
        suggestTags()
    }

    fun onInputTag(value: String) {
        _bindingModel.update {
            it.copy(
                inputTag = value,
            )
        }
        suggestTags(value)
    }

    private fun suggestTags(input: String = "") {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedTags = tagRepository.fuzzyFind(input),
                )
            }
        }
    }

    fun onEnterTag() {
        val tagName = _bindingModel.value.inputTag
        viewModelScope.launch {
            val tag = tagRepository.findByName(tagName)
                ?: Tag.create(tagName).also {
                    tagRepository.save(it)
                }
            _bindingModel.update {
                it.copy(
                    tags = it.tags + tag,
                    inputTag = "",
                    suggestedTags = emptyList(),
                )
            }
        }
    }

    fun onSelectTag(tag: Tag) {
        if (_bindingModel.value.tags.any { it == tag }) {
            return
        }
        _bindingModel.update {
            it.copy(
                tags = it.tags + tag,
                inputTag = "",
                suggestedTags = emptyList(),
            )
        }
    }

    fun onDeleteTag(tag: Tag) {
        _bindingModel.update {
            it.copy(
                tags = it.tags - tag,
            )
        }
    }

    fun onFocusEvent() {
        suggestEvents()
    }

    fun onInputEvent(value: String) {
        _bindingModel.update {
            it.copy(
                inputEvent = value,
            )
        }
        suggestEvents(value)
    }

    private fun suggestEvents(input: String = "") {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _bindingModel.update {
                it.copy(
                    suggestedEvents = eventRepository.fuzzyFind(input)
                )
            }
        }
    }

    fun onEnterEventName() {
        val eventName = _bindingModel.value.inputEvent
        viewModelScope.launch {
            val event = eventRepository.findByName(eventName)
            if (event == null) {
                _bindingModel.update {
                    it.copy(
                        isShowEventDatePicker = true,
                    )
                }
            } else {
                _bindingModel.update {
                    it.copy(
                        event = event,
                        inputEvent = "",
                    )
                }
            }
        }
    }

    fun onEnterEventDate(start: LocalDate?, end: LocalDate?) {
        val eventName = _bindingModel.value.inputEvent
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
                it.copy(
                    event = event,
                    inputEvent = "",
                    isShowEventDatePicker = false,
                )
            }
        }
    }

    fun onSelectEvent(event: Event) {
        _bindingModel.update {
            it.copy(
                event = event,
                inputEvent = "",
                suggestedEvents = listOf(),
            )
        }
    }

    fun onDeleteEvent() {
        _bindingModel.update {
            it.copy(
                event = null,
            )
        }
    }

    fun onSelectPubDate(value: LocalDate?) {
        _bindingModel.update {
            it.copy(
                pubDate = value,
            )
        }
    }

    fun onDeletePubDate() {
        _bindingModel.update {
            it.copy(
                pubDate = null,
            )
        }
    }

    fun onSelectImages(paths: List<String>) {
        _bindingModel.update {
            it.copy(
                imagePaths = it.imagePaths + paths,
            )
        }
    }

    fun onDeleteImage(path: String) {
        _bindingModel.update {
            it.copy(
                imagePaths = it.imagePaths - path,
            )
        }
    }

    fun onClickRegister() {
        suggestJob?.cancel()
        suggestJob = null
        val current = _bindingModel.value
        viewModelScope.launch {
            val circle = current.circle
                ?: circleRepository.findByName(current.inputCircle)
                ?: Circle.create(current.inputCircle).also {
                    circleRepository.save(it)
                }
            val doujinshi = Doujinshi.create(
                title = current.title,
                circle = circle,
                authors = current.authors,
                tags = current.tags,
                event = current.event,
                pubDate = current.pubDate,
                imagePaths = current.imagePaths,
            )
            doujinshiRepository.save(doujinshi)
            _destination.value = PopBackDestination
        }
    }

    fun onDismiss() {
        suggestJob?.cancel()
        suggestJob = null
        _bindingModel.update {
            it.copy(
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
