package com.yt8492.doujinshimanager.ui.top

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchResult
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.converter.BindingModelConverter
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.register.RegisterDestination
import com.yt8492.doujinshimanager.ui.search.SearchDestination
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TopViewModel(
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopUiState.init())
    val uiState: StateFlow<TopUiState> = _uiState.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    refreshing = true,
                )
            }
            val result = doujinshiRepository.search()
            _uiState.update {
                it.copy(
                    bindingModel = TopBindingModel(
                        list = result.list.map {
                            BindingModelConverter.convertToDoujinshiBindingModel(it)
                        },
                        page = 0,
                        hasNextPage = result.hasNextPage,
                    ),
                    refreshing = false
                )
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val page = _uiState.value.bindingModel.page
            _uiState.update {
                it.copy(
                    loadingMore = true,
                )
            }
            val result = doujinshiRepository.search(
                page = page + 1,
            )
            _uiState.update {
                it.copy(
                    bindingModel = TopBindingModel(
                        list = it.bindingModel.list + result.list.map {
                            BindingModelConverter.convertToDoujinshiBindingModel(it)
                        },
                        page = it.bindingModel.page + 1,
                        hasNextPage = result.hasNextPage,
                    ),
                    loadingMore = false,
                )
            }
        }
    }

    fun onClickItem(item: DoujinshiBindingModel) {
        _destination.value = DetailDestination(item.id.value)
    }

    fun onClickSearch() {
        _destination.value = SearchDestination
    }

    fun onClickAddDoujinshi() {
        _destination.value = RegisterDestination
    }

    fun onCompleteNavigation() {
        _destination.value = null
    }
}
