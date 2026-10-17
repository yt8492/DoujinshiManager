package com.yt8492.doujinshimanager.ui.circledetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CircleDetailPage(
    id: CircleId,
    navController: NavController,
    viewModel: CircleDetailViewModel = koinViewModel {
        parametersOf(id)
    },
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val destination = viewModel.destination.collectAsStateWithLifecycle().value
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.load()
    }
    LaunchedEffect(destination) {
        if (destination != null) {
            destination.navigate(navController)
            viewModel.completeNavigation()
        }
    }
    CircleDetailTemplate(
        uiState = uiState,
        loadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onClickEdit = viewModel::onClickEdit,
        onInputName = viewModel::onInputName,
        onClickSave = viewModel::onClickSave,
        onClickItem = viewModel::onClickItem,
        onClickBack = viewModel::onClickBack,
    )
}
