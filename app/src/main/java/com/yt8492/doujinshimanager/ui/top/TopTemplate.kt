package com.yt8492.doujinshimanager.ui.top

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.component.DoujinshiListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopTemplate(
    bindingModel: TopBindingModel,
    loadMore: () -> Unit,
    onClickItem: (DoujinshiBindingModel) -> Unit,
    onClickSearch: () -> Unit,
    onClickAddDoujinshi: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "同人誌管理マン")
                },
                actions = {
                    IconButton(
                        onClick = onClickSearch,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "同人誌を検索する",
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onClickAddDoujinshi) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "同人誌を登録する",
                )
            }
        }
    ) { paddingValues ->
        val listState = rememberLazyListState()
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo to listState.firstVisibleItemIndex }
                .collect { (layoutInfo, firstVisibleItemIndex) ->
                    if (layoutInfo.totalItemsCount == firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size) {
                        loadMore()
                    }
                }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
        ) {
            items(
                items = bindingModel.list,
                key = { it.id.value }
            ) {
                DoujinshiListItem(
                    item = it,
                    onClick = onClickItem,
                )
            }
        }
    }
}
