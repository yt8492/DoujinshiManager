package com.yt8492.doujinshimanager.shared.domain.repository

import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.EventId

interface EventRepository {
    suspend fun fuzzyFind(eventName: String): List<Event>
    suspend fun findByName(eventName: String): Event?
    suspend fun get(id: EventId): Event
    suspend fun save(event: Event)
}
