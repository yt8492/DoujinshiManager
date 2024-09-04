package com.yt8492.doujinshimanager.shared.domain.repository

import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId

interface CircleRepository {
    suspend fun fuzzyFind(circleName: String): List<Circle>
    suspend fun findByName(circleName: String): Circle?
    suspend fun get(id: CircleId): Circle
    suspend fun save(circle: Circle)
    suspend fun update(circle: Circle)
}
