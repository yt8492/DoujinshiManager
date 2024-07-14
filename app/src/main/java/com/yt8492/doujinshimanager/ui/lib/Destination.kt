package com.yt8492.doujinshimanager.ui.lib

import androidx.navigation.NavController

abstract class Destination {
    open fun navigate(navController: NavController) {
        navController.navigate(this)
    }
}
