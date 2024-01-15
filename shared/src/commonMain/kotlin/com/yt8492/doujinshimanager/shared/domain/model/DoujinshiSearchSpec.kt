package com.yt8492.doujinshimanager.shared.domain.model

data class DoujinshiSearchSpec(
    val keyword: String? = null,
    val circle: CircleId? = null,
    val event: EventId? = null,
    val authors: List<AuthorId> = emptyList(),
    val tags: List<TagId> = emptyList(),
    val pubTerm: Period? = null,
    val sortKey: DoujinshiSortKey = DoujinshiSortKey.EVENT_DESC,
)
