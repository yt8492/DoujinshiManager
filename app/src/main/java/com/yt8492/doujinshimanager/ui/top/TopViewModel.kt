package com.yt8492.doujinshimanager.ui.top

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.converter.BindingModelConverter
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.lib.Destination
import com.yt8492.doujinshimanager.ui.lib.DoujinshiPager
import com.yt8492.doujinshimanager.ui.register.RegisterDestination
import com.yt8492.doujinshimanager.ui.search.SearchDestination
import com.yt8492.doujinshimanager.ui.setting.SettingDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TopViewModel(
    private val doujinshiRepository: DoujinshiRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopUiState.init())
    val uiState: StateFlow<TopUiState> = _uiState.asStateFlow()

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination.asStateFlow()

    private val pager = DoujinshiPager(viewModelScope, doujinshiRepository) { state ->
        _uiState.update {
            it.copy(
                bindingModel = TopBindingModel(
                    list = state.list.map(BindingModelConverter::convertToDoujinshiBindingModel),
                    page = state.page,
                    hasNextPage = state.hasNextPage,
                ),
                refreshing = state.refreshing,
                loadingMore = state.loadingMore,
                loadError = state.error,
            )
        }
    }

    fun load() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun retry() = pager.retry()

    fun onClickItem(item: DoujinshiBindingModel) {
        _destination.value = DetailDestination(item.id.value)
    }

    fun onClickSearch() {
        _destination.value = SearchDestination()
    }

    fun onClickAddDoujinshi() {
        _destination.value = RegisterDestination
    }

    fun onClickSetting() {
        _destination.value = SettingDestination
    }

    fun onCompleteNavigation() {
        _destination.value = null
    }
}
