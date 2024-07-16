package com.yt8492.doujinshimanager.shared.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.LocalDate

data class Doujinshi(
    val id: DoujinshiId,
    val title: String,
    val circle: Circle?,
    val authors: List<Author>,
    val tags: List<Tag>,
    val event: Event?,
    val pubDate: LocalDate?,
    val imagePaths: List<String>,
) {
    companion object {
        fun create(
            title: String,
            circle: Circle?,
            authors: List<Author>,
            tags: List<Tag>,
            event: Event?,
            pubDate: LocalDate?,
            imagePaths: List<String>,
        ): Doujinshi {
            return Doujinshi(
                id = DoujinshiId(uuid4().toString()),
                title = title,
                circle = circle,
                authors = authors,
                tags = tags,
                event = event,
                pubDate = pubDate,
                imagePaths = imagePaths,
            )
        }
    }
}
