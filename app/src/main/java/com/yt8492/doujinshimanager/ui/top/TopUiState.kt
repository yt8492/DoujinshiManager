package com.yt8492.doujinshimanager.ui.top

data class TopUiState(
    val bindingModel: TopBindingModel,
    val refreshing: Boolean,
    val loadingMore: Boolean,
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
