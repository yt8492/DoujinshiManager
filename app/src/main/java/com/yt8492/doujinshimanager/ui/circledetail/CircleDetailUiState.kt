package com.yt8492.doujinshimanager.ui.circledetail

import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError

data class CircleDetailUiState(
    val bindingModel: CircleDetailBindingModel,
    val loadingMore: Boolean,
    val refreshing: Boolean = false,
    val loadError: DoujinshiLoadError? = null,
) {
    companion object {
        fun init(
            id: CircleId,
        ): CircleDetailUiState {
            return CircleDetailUiState(
                bindingModel = CircleDetailBindingModel(
                    circle = Circle(
                        id = id,
                        name = "",
                    ),
                    inputCircleName = "",
                    list = listOf(),
                    page = 0,
                    hasNextPage = false,
                    isEditMode = false,
                ),
                loadingMore = false,
            )
        }
    }
}
