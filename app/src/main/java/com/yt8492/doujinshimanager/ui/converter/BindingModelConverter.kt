package com.yt8492.doujinshimanager.ui.converter

import android.net.Uri
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.edit.EditBindingModel

object BindingModelConverter {
    fun convertToDoujinshiBindingModel(
        doujinshi: Doujinshi,
    ): DoujinshiBindingModel {
        return DoujinshiBindingModel(
            id = doujinshi.id,
            title = doujinshi.title,
            circleName = doujinshi.circle?.name ?: "",
            image = doujinshi.imagePaths.firstOrNull()?.let {
                Uri.parse(it)
            }
        )
    }

    fun convertToEditBindingModel(
        doujinshi: Doujinshi,
    ): EditBindingModel {
        return EditBindingModel(
            id = doujinshi.id,
            title = doujinshi.title,
            circle = doujinshi.circle,
            authors = doujinshi.authors,
            tags = doujinshi.tags,
            event = doujinshi.event,
            pubDate = doujinshi.pubDate,
            imagePaths = doujinshi.imagePaths,
            inputCircle = "",
            inputAuthor = "",
            inputTag = "",
            inputEvent = "",
            suggestedCircles = listOf(),
            suggestedAuthors = listOf(),
            suggestedTags = listOf(),
            suggestedEvents = listOf(),
            isShowEventDatePicker = false,
        )
    }
}
