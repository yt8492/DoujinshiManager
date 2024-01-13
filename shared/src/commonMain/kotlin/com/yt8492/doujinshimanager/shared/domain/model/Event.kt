package com.yt8492.doujinshimanager.shared.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.LocalDate

data class Event(
    val id: EventId,
    val name: String,
    val term: Period?,
) {
    companion object {
        fun create(
            name: String,
            term: Period?
        ): Event {
            return Event(
                id = EventId(uuid4().toString()),
                name = name,
                term = term,
            )
        }

        fun create(
            name: String,
            date: LocalDate,
        ): Event {
            return create(
                name = name,
                term = Period(date, date),
            )
        }
    }
}
