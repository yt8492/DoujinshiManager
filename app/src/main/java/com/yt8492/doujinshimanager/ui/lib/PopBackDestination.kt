package com.yt8492.doujinshimanager.ui.lib

import androidx.navigation.NavController

object PopBackDestination : Destination() {
    override fun navigate(navController: NavController) {
        navController.popBackStack()
    }
}
