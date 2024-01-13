package com.yt8492.doujinshimanager.shared.domain.model

import com.benasher44.uuid.uuid4

data class Author(
    val id: AuthorId,
    val name: String,
) {
    companion object {
        fun create(name: String): Author {
            return Author(
                id = AuthorId(uuid4().toString()),
                name = name,
            )
        }
    }
}
