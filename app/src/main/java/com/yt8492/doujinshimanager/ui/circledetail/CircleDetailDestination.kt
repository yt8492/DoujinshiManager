package com.yt8492.doujinshimanager.ui.circledetail

import com.yt8492.doujinshimanager.ui.lib.Destination
import kotlinx.serialization.Serializable

@Serializable
data class CircleDetailDestination(
    val circleId: String,
) : Destination()
