package com.yt8492.doujinshimanager.shared.infra.repository

import com.yt8492.doujinshimanager.database.AuthorQueries
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.AuthorId
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.infra.converter.DBConverter

class AuthorRepositoryImpl(
    private val queries: AuthorQueries
) : AuthorRepository {
    override suspend fun fuzzyFind(authorName: String): List<Author> {
        return queries.search("$authorName%")
            .executeAsList()
            .map {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun findByName(authorName: String): Author? {
        return queries.search(authorName)
            .executeAsOneOrNull()
            ?.let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun get(id: AuthorId): Author {
        return queries.find(id.value)
            .executeAsOne()
            .let {
                DBConverter.convertToModel(it)
            }
    }

    override suspend fun save(author: Author) {
        queries.insert(author.id.value, author.name)
    }
}
