package com.yt8492.doujinshimanager.shared.domain.model

data class DoujinshiSearchResult(
    val list: List<Doujinshi>,
    val hasNextPage: Boolean,
)
