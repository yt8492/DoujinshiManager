package com.yt8492.doujinshimanager.ui.search

import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSortKey
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError

data class SearchBindingModel(
    val title: String,
    val circle: Circle?,
    val authors: List<Author>,
    val tags: List<Tag>,
    val event: Event?,
    val inputCircle: String,
    val inputAuthor: String,
    val inputTag: String,
    val inputEvent: String,
    val suggestedCircles: List<Circle>,
    val suggestedAuthors: List<Author>,
    val suggestedTags: List<Tag>,
    val suggestedEvents: List<Event>,
    val isShowSearchOptions: Boolean,
    val searchResult: List<DoujinshiBindingModel>,
    val page: Int,
    val hasNextPage: Boolean,
    val pubTerm: Period? = null,
    val sortKey: DoujinshiSortKey = DoujinshiSortKey.EVENT_DESC,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val loadError: DoujinshiLoadError? = null,
) {
    companion object {
        fun empty(): SearchBindingModel {
            return SearchBindingModel(
                title = "",
                circle = null,
                authors = emptyList(),
                tags = emptyList(),
                event = null,
                inputCircle = "",
                inputAuthor = "",
                inputTag = "",
                inputEvent = "",
                suggestedCircles = emptyList(),
                suggestedAuthors = emptyList(),
                suggestedTags = emptyList(),
                suggestedEvents = emptyList(),
                isShowSearchOptions = false,
                searchResult = listOf(),
                page = 0,
                hasNextPage = false,
            )
        }
    }
}
