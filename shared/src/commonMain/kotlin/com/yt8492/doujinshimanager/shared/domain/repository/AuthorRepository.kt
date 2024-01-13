package com.yt8492.doujinshimanager.shared.domain.repository

import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.AuthorId

interface AuthorRepository {
    suspend fun fuzzyFind(authorName: String): List<Author>
    suspend fun get(id: AuthorId): Author
    suspend fun save(author: Author)
}