package com.yt8492.doujinshimanager.shared.domain.repository

import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.model.TagId

interface TagRepository {
    suspend fun fuzzyFind(tagName: String): List<Tag>
    suspend fun findByName(tagName: String): Tag?
    suspend fun get(id: TagId): Tag
    suspend fun save(tag: Tag)
}
