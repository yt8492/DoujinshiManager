package com.yt8492.doujinshimanager.ui.top

import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError

data class TopUiState(
    val bindingModel: TopBindingModel,
    val refreshing: Boolean,
    val loadingMore: Boolean,
    val loadError: DoujinshiLoadError? = null,
) {
    companion object {
        fun init(): TopUiState {
            return TopUiState(
                bindingModel = TopBindingModel(
                    list = emptyList(),
                    page = 0,
                    hasNextPage = false,
                ),
                refreshing = true,
                loadingMore = false,
            )
        }
    }
}
