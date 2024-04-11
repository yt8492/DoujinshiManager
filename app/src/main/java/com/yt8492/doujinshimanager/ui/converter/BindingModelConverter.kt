package com.yt8492.doujinshimanager.ui.converter

import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel

object BindingModelConverter {
    fun convertToDoujinshiBindingModel(
        doujinshi: Doujinshi,
    ): DoujinshiBindingModel {
        return DoujinshiBindingModel(
            id = doujinshi.id,
            title = doujinshi.title,
            circleName = doujinshi.circle?.name ?: ""
        )
    }
}
