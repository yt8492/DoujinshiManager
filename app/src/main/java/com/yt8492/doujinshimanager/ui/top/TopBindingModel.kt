package com.yt8492.doujinshimanager.ui.top

import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel

data class TopBindingModel(
    val list: List<DoujinshiBindingModel>,
    val page: Int,
    val hasNextPage: Boolean,
)
