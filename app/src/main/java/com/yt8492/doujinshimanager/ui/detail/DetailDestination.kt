package com.yt8492.doujinshimanager.ui.detail

import com.yt8492.doujinshimanager.ui.lib.Destination
import kotlinx.serialization.Serializable

@Serializable
data class DetailDestination(
    val id: String
) : Destination()
