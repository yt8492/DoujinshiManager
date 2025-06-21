package com.yt8492.doujinshimanager.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPage(
    navController: NavController,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    
    LaunchedEffect(destination) {
        destination?.let {
            it.navigate(navController)
            viewModel.onCompleteNavigation()
        }
    }
    
    SettingTemplate(
        onClickBack = { navController.popBackStack() },
        onClickExportData = viewModel::onClickExportData,
        onClickImportData = viewModel::onClickImportData,
    )
}
