package com.yt8492.doujinshimanager.ui.edit

import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import kotlinx.datetime.LocalDate

data class EditBindingModel(
    val id: DoujinshiId,
    val title: String,
    val circle: Circle?,
    val authors: List<Author>,
    val tags: List<Tag>,
    val event: Event?,
    val pubDate: LocalDate?,
    val imagePaths: List<String>,
    val inputCircle: String,
    val inputAuthor: String,
    val inputTag: String,
    val inputEvent: String,
    val suggestedCircles: List<Circle>,
    val suggestedAuthors: List<Author>,
    val suggestedTags: List<Tag>,
    val suggestedEvents: List<Event>,
    val isShowEventDatePicker: Boolean,
)
