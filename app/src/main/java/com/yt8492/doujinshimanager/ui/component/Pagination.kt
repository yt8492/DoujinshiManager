package com.yt8492.doujinshimanager.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun PaginationEffect(
    listState: LazyListState,
    itemCount: Int,
    hasNextPage: Boolean,
    enabled: Boolean,
    loadMore: () -> Unit,
) {
    val currentLoadMore by rememberUpdatedState(loadMore)
    LaunchedEffect(listState, itemCount, hasNextPage, enabled) {
        if (enabled && hasNextPage && itemCount > 0) {
            snapshotFlow {
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= itemCount - 1
            }.distinctUntilChanged().filter { it }.collect { currentLoadMore() }
        }
    }
}

@Composable
internal fun PaginationStatus(
    loading: Boolean,
    error: DoujinshiLoadError?,
    onRetry: () -> Unit,
) {
    if (loading) CircularProgressIndicator()
    if (error != null) {
        Column {
            Text("読み込みに失敗しました")
            TextButton(onClick = onRetry) { Text("再試行") }
        }
    }
}
