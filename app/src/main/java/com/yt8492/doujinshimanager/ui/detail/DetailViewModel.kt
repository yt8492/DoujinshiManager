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
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    id: DoujinshiId,
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _doujinshi = MutableStateFlow<Doujinshi?>(null)
    val doujinshi: StateFlow<Doujinshi?> = _doujinshi.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            _doujinshi.value = doujinshiRepository.get(id)
        }
    }

    fun onClickCircle(circle: Circle) {

    }

    fun onClickAuthor(author: Author) {

    }

    fun onClickTag(tag: Tag) {

    }

    fun onClickEvent(event: Event) {

    }

    fun onBackPress() {
        _destination.value = PopBackDestination
    }

    fun onCompleteNavigation() {
        _destination.value = null
    }
}
