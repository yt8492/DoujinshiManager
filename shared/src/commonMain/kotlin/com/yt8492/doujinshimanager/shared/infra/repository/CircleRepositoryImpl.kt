package com.yt8492.doujinshimanager.shared.infra.repository

import com.yt8492.doujinshimanager.database.CircleQueries
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.infra.converter.DBConverter

class CircleRepositoryImpl(
    private val queries: CircleQueries,
) : CircleRepository {
    override suspend fun fuzzyFind(circleName: String): List<Circle> {
        return queries.search("$circleName%")
            .executeAsList()
            .map {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun findByName(circleName: String): Circle? {
        return queries.search(circleName)
            .executeAsOneOrNull()
            ?.let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun get(id: CircleId): Circle {
        return queries.find(id.value)
            .executeAsOne()
            .let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun save(circle: Circle) {
        queries.insert(circle.id.value, circle.name)
    }
}
