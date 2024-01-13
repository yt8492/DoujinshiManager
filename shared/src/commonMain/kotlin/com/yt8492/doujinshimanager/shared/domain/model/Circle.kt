package com.yt8492.doujinshimanager.shared.domain.model

import com.benasher44.uuid.uuid4

data class Circle(
    val id: CircleId,
    val name: String,
) {
    companion object {
        fun create(name: String): Circle {
            return Circle(
                id = CircleId(uuid4().toString()),
                name = name,
            )
        }
    }
}
