package com.yt8492.doujinshimanager.ui.circledetail

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.component.DoujinshiListItem

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
) {
    val bindingModel = uiState.bindingModel
    val pullToRefreshState = rememberPullToRefreshState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (bindingModel.isEditMode) {
                        OutlinedTextField(
                            value = bindingModel.inputCircleName,
                            onValueChange = onInputName,
                        )
                    } else {
                        Text(text = bindingModel.circle.name)
                    }
                },
                actions = {
                    if (bindingModel.isEditMode) {
                        IconButton(
                            onClick = onClickSave,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "保存",
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onClickEdit,
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
        modifier = Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding)
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo to listState.firstVisibleItemIndex }
                    .collect { (layoutInfo, firstVisibleItemIndex) ->
                        if (bindingModel.hasNextPage && layoutInfo.totalItemsCount == firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size) {
                            loadMore()
                        }
                    }
            }
            LazyColumn(
                state = listState,
            ) {
                items(bindingModel.list) {
                    DoujinshiListItem(
                        item = it,
                        onClick = onClickItem,
                    )
                }
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
