package com.yt8492.doujinshimanager.shared.domain.model

import com.benasher44.uuid.uuid4

data class Tag(
    val id: TagId,
    val name: String,
) {
    companion object {
        fun create(name: String): Tag {
            return Tag(
                id = TagId(uuid4().toString()),
                name = name,
            )
        }
    }
}
