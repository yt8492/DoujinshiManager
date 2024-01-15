package com.yt8492.doujinshimanager.shared.infra.repository

import com.yt8492.doujinshimanager.database.TagQueries
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.model.TagId
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import com.yt8492.doujinshimanager.shared.infra.converter.DBConverter

class TagRepositoryImpl(
    private val queries: TagQueries,
) : TagRepository {
    override suspend fun fuzzyFind(tagName: String): List<Tag> {
        return queries.search("$tagName%")
            .executeAsList()
            .map {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun get(id: TagId): Tag {
        return queries.find(id.value)
            .executeAsOne()
            .let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun save(tag: Tag) {
        queries.insert(
            id = tag.id.value,
            name = tag.name,
        )
    }
}
