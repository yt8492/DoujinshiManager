package com.yt8492.doujinshimanager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.ui.detail.DetailDestination
import com.yt8492.doujinshimanager.ui.detail.DetailPage
import com.yt8492.doujinshimanager.ui.register.RegisterDestination
import com.yt8492.doujinshimanager.ui.register.RegisterPage
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme
import com.yt8492.doujinshimanager.ui.top.TopDestination
import com.yt8492.doujinshimanager.ui.top.TopPage

@Composable
fun MainApp() {
    DoujinshiManagerTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = TopDestination,
        ) {
            composable<TopDestination> {
                TopPage(navController)
            }
            composable<RegisterDestination> {
                RegisterPage(navController)
            }
            composable<DetailDestination> {
                val destination: DetailDestination = it.toRoute()
                DetailPage(
                    id = DoujinshiId(destination.id),
                    navController = navController,
                )
            }
        }
    }
}
