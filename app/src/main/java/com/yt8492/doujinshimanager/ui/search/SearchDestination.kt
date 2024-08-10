package com.yt8492.doujinshimanager.ui.search

import com.yt8492.doujinshimanager.ui.lib.Destination
import kotlinx.serialization.Serializable

@Serializable
data class SearchDestination(
    val title: String? = null,
    val circleId: String? = null,
    val authorIds: List<String> = listOf(),
    val tagIds: List<String> = listOf(),
    val eventId: String? = null,
    val pubDate: String? = null,
) : Destination()
