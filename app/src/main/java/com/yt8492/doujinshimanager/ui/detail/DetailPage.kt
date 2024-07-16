package com.yt8492.doujinshimanager.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailPage(
    id: DoujinshiId,
    navController: NavController,
    viewModel: DetailViewModel = koinViewModel {
        parametersOf(id)
    },
) {
    val doujinshi by viewModel.doujinshi.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        destination?.let {
            it.navigate(navController)
            viewModel.onCompleteNavigation()
        }
    }
    BackHandler {
        viewModel.onBackPress()
    }
    DetailTemplate(
        doujinshi = doujinshi,
        onBackPress = viewModel::onBackPress,
        onClickCircle = viewModel::onClickCircle,
        onClickAuthor = viewModel::onClickAuthor,
        onClickTag = viewModel::onClickTag,
        onClickEvent = viewModel::onClickEvent,
    )
}
