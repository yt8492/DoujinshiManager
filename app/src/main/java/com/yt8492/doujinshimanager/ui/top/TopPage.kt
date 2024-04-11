package com.yt8492.doujinshimanager.ui.top

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun TopPage(
    viewModel: TopViewModel = koinViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) {
        viewModel.load()
    }
    TopTemplate(
        bindingModel = uiState.bindingModel,
        loadMore = viewModel::loadMore,
        onClickItem = viewModel::onClickItem,
        onClickSearch = viewModel::onClickSearch,
        onClickAddDoujinshi = viewModel::onClickAddDoujinshi,
    )
}
