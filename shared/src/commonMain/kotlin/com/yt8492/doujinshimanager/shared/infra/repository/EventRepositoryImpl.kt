package com.yt8492.doujinshimanager.shared.infra.repository

import com.yt8492.doujinshimanager.database.EventQueries
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.EventId
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.infra.converter.DBConverter

class EventRepositoryImpl(
    private val queries: EventQueries,
) : EventRepository {
    override suspend fun fuzzyFind(eventName: String): List<Event> {
        return queries.search("$eventName%")
            .executeAsList()
            .map {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun findByName(eventName: String): Event? {
        return queries.search(eventName)
            .executeAsOneOrNull()
            ?.let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun get(id: EventId): Event {
        return queries.find(id.value)
            .executeAsOne()
            .let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun save(event: Event) {
        queries.insert(
            id = event.id.value,
            name = event.name,
            start_at = event.term?.start?.toString(),
            end_at = event.term?.end?.toString(),
        )
    }
}
