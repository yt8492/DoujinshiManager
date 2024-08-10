package com.yt8492.doujinshimanager.ui.edit

import com.yt8492.doujinshimanager.ui.lib.Destination
import kotlinx.serialization.Serializable

@Serializable
data class EditDestination(
    val id: String,
) : Destination()
