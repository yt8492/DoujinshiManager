package com.yt8492.doujinshimanager.ui.circledetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.component.DoujinshiListItem
import com.yt8492.doujinshimanager.ui.component.PaginationEffect
import com.yt8492.doujinshimanager.ui.component.PaginationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailTemplate(
    uiState: CircleDetailUiState,
    loadMore: () -> Unit,
    onClickEdit: () -> Unit,
    onInputName: (String) -> Unit,
    onClickSave: () -> Unit,
    onClickItem: (DoujinshiBindingModel) -> Unit,
    onClickBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val bindingModel = uiState.bindingModel
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (bindingModel.isEditMode) {
                        OutlinedTextField(
                            value = bindingModel.inputCircleName,
                            onValueChange = onInputName,
                            enabled = uiState.canSave,
                            isError = uiState.saveError,
                            supportingText = if (uiState.saveError) {
                                { Text("サークル名の保存に失敗しました") }
                            } else {
                                null
                            },
                        )
                    } else {
                        Text(text = bindingModel.circle.name)
                    }
                },
                actions = {
                    if (bindingModel.isEditMode) {
                        IconButton(
                            onClick = onClickSave,
                            enabled = uiState.canSave,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "保存",
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onClickEdit,
                            enabled = uiState.canEdit,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "編集",
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClickBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        PaginationEffect(
            listState, bindingModel.list.size, bindingModel.hasNextPage,
            uiState.canLoadMore, loadMore,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            items(bindingModel.list, key = { it.id.value }) {
                DoujinshiListItem(
                    item = it,
                    onClick = onClickItem,
                )
            }
            item(key = "pagination-status") {
                PaginationStatus(uiState.refreshing || uiState.loadingMore, uiState.loadError, onRetry)
            }
        }
    }
}
