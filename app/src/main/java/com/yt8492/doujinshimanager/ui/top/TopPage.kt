package com.yt8492.doujinshimanager.ui.top

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@Composable
fun TopPage(
    navController: NavController,
    viewModel: TopViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        viewModel.load()
    }
    LaunchedEffect(destination) {
        destination?.let {
            it.navigate(navController)
            viewModel.onCompleteNavigation()
        }
    }
    TopTemplate(
        bindingModel = uiState.bindingModel,
        loadMore = viewModel::loadMore,
        onClickItem = viewModel::onClickItem,
        onClickSearch = viewModel::onClickSearch,
        onClickAddDoujinshi = viewModel::onClickAddDoujinshi,
    )
}
