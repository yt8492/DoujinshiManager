package com.yt8492.doujinshimanager.shared.domain.model

import kotlinx.datetime.LocalDate

data class Doujinshi(
    val title: String,
    val circle: Circle?,
    val authors: List<Author>,
    val tags: List<Tag>,
    val event: Event?,
    val pubDate: LocalDate?,
)
