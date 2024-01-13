package com.yt8492.doujinshimanager.shared.domain.model

data class DoujinshiSearchSpec(
    val keyword: String = "",
    val circleId: CircleId? = null,
    val authors: List<AuthorId> = emptyList(),
    val tags: List<TagId> = emptyList(),
    val event: EventId? = null,
    val pubTerm: Period? = null,
    val sortKey: DoujinshiSortKey = DoujinshiSortKey.EVENT_DESC,
)
