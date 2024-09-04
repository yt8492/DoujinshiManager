package com.yt8492.doujinshimanager.ui.circledetail

import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel

data class CircleDetailBindingModel(
    val circle: Circle,
    val inputCircleName: String,
    val list: List<DoujinshiBindingModel>,
    val page: Int,
    val hasNextPage: Boolean,
    val isEditMode: Boolean,
)
