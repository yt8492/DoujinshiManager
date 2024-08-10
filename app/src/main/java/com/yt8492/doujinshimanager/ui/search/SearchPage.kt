package com.yt8492.doujinshimanager.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SearchPage(
    searchSpec: DoujinshiSearchSpec,
    navController: NavController,
    viewModel: SearchViewModel = koinViewModel {
        parametersOf(searchSpec)
    },
) {
    val bindingModel by viewModel.bindingModel.collectAsStateWithLifecycle()
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
    SearchTemplate(
        bindingModel = bindingModel,
        onInputTitle = viewModel::onInputTitle,
        toggleShowSearchOption = viewModel::toggleShowSearchOption,
        onInputCircle = viewModel::onInputCircle,
        onSelectCircle = viewModel::onSelectCircle,
        onDeleteCircle = viewModel::onDeleteCircle,
        onInputAuthor = viewModel::onInputAuthor,
        onSelectAuthor = viewModel::onSelectAuthor,
        onDeleteAuthor = viewModel::onDeleteAuthor,
        onInputTag = viewModel::onInputTag,
        onSelectTag = viewModel::onSelectTag,
        onDeleteTag = viewModel::onDeleteTag,
        onInputEvent = viewModel::onInputEvent,
        onSelectEvent = viewModel::onSelectEvent,
        onDeleteEvent = viewModel::onDeleteEvent,
        onClickItem = viewModel::onClickItem,
        loadMore = viewModel::loadMore,
        onDismiss = viewModel::onDismiss,
        onBackPress = viewModel::onBackPress,
    )
}
