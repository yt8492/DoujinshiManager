package com.yt8492.doujinshimanager.shared.infra.converter

import com.yt8492.doujinshimanager.database.Authors
import com.yt8492.doujinshimanager.database.Circles
import com.yt8492.doujinshimanager.database.Events
import com.yt8492.doujinshimanager.database.Tags
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.AuthorId
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.EventId
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.model.TagId
import kotlinx.datetime.LocalDate

object DBConverter {
    fun convertToModel(dto: Authors): Author {
        return Author(
            id = AuthorId(dto.id),
            name = dto.name,
        )
    }

    fun convertToModel(dto: Circles): Circle {
        return Circle(
            id = CircleId(dto.id),
            name = dto.name,
        )
    }

    fun convertToModel(dto: Events): Event {
        return Event(
            id = EventId(dto.id),
            name = dto.name,
            term = if (dto.start_at != null && dto.end_at != null) {
                Period(
                    start = LocalDate.parse(dto.start_at),
                    end = LocalDate.parse(dto.end_at),
                )
            } else {
                null
            },
        )
    }

    fun convertToModel(dto: Tags): Tag {
        return Tag(
            id = TagId(dto.id),
            name = dto.name,
        )
    }
}