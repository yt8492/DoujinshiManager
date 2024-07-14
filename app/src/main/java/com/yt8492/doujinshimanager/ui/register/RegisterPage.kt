package com.yt8492.doujinshimanager.ui.register

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@Composable
fun RegisterPage(
    navController: NavController,
    viewModel: RegisterViewModel = koinViewModel(),
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
    RegisterTemplate(
        bindingModel = bindingModel,
        onInputTitle = viewModel::onInputTitle,
        onInputCircle = viewModel::onInputCircle,
        onSelectCircle = viewModel::onSelectCircle,
        onDeleteCircle = viewModel::onDeleteCircle,
        onInputAuthor = viewModel::onInputAuthor,
        onEnterAuthor = viewModel::onEnterAuthor,
        onSelectAuthor = viewModel::onSelectAuthor,
        onDeleteAuthor = viewModel::onDeleteAuthor,
        onInputTag = viewModel::onInputTag,
        onEnterTag = viewModel::onEnterTag,
        onSelectTag = viewModel::onSelectTag,
        onDeleteTag = viewModel::onDeleteTag,
        onInputEvent = viewModel::onInputEvent,
        onEnterEventName = viewModel::onEnterEventName,
        onEnterEventDate = viewModel::onEnterEventDate,
        onSelectEvent = viewModel::onSelectEvent,
        onDeleteEvent = viewModel::onDeleteEvent,
        onSelectPubDate = viewModel::onSelectPubDate,
        onDeletePubDate = viewModel::onDeletePubDate,
        onClickRegister = viewModel::onClickRegister,
        onDismiss = viewModel::onDismiss,
        onBackPress = viewModel::onBackPress,
    )
}
