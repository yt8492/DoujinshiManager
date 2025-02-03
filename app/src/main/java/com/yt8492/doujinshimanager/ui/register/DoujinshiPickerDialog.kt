package com.yt8492.doujinshimanager.ui.register

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yt8492.doujinshimanager.ui.search.SearchTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoujinshiPickerDialog(
    viewModel: DoujinshiPickerViewModel,
) {
    val bindingModel by viewModel.bindingModel.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = viewModel::onDismissDialog
    ) {
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
            onDismiss = viewModel::onDismissSuggest,
            onBackPress = viewModel::onDismissSuggest,
        )
    }
}
