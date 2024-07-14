package com.yt8492.doujinshimanager.ui.register

import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import kotlinx.datetime.LocalDate

data class RegisterBindingModel(
    val title: String,
    val circle: Circle?,
    val authors: List<Author>,
    val tags: List<Tag>,
    val event: Event?,
    val pubDate: LocalDate?,
    val inputCircle: String,
    val inputAuthor: String,
    val inputTag: String,
    val inputEvent: String,
    val suggestedCircles: List<Circle>,
    val suggestedAuthors: List<Author>,
    val suggestedTags: List<Tag>,
    val suggestedEvents: List<Event>,
    val isShowEventDatePicker: Boolean,
) {
    companion object {
        fun empty(): RegisterBindingModel {
            return RegisterBindingModel(
                title = "",
                circle = null,
                authors = emptyList(),
                tags = emptyList(),
                event = null,
                pubDate = null,
                inputCircle = "",
                inputAuthor = "",
                inputTag = "",
                inputEvent = "",
                suggestedCircles = emptyList(),
                suggestedAuthors = emptyList(),
                suggestedTags = emptyList(),
                suggestedEvents = emptyList(),
                isShowEventDatePicker = false,
            )
        }
    }
}
