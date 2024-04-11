package com.yt8492.doujinshimanager.ui.bindingmodel

import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId

data class DoujinshiBindingModel(
    val id: DoujinshiId,
    val title: String,
    val circleName: String,
)
