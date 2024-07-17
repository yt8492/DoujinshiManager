package com.yt8492.doujinshimanager.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.component.DeletableTextBox
import com.yt8492.doujinshimanager.ui.component.DoujinshiListItem
import com.yt8492.doujinshimanager.ui.component.InputSection
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTemplate(
    bindingModel: SearchBindingModel,
    onInputTitle: (String) -> Unit,
    toggleShowSearchOption: () -> Unit,
    onInputCircle: (String) -> Unit,
    onSelectCircle: (Circle) -> Unit,
    onDeleteCircle: () -> Unit,
    onInputAuthor: (String) -> Unit,
    onSelectAuthor: (Author) -> Unit,
    onDeleteAuthor: (Author) -> Unit,
    onInputTag: (String) -> Unit,
    onSelectTag: (Tag) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onInputEvent: (String) -> Unit,
    onSelectEvent: (Event) -> Unit,
    onDeleteEvent: () -> Unit,
    onClickItem: (DoujinshiBindingModel) -> Unit,
    loadMore: () -> Unit,
    onDismiss: () -> Unit,
    onBackPress: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = bindingModel.title,
                        onValueChange = onInputTitle,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                            )
                        },
                        placeholder = {
                            Text(text = "タイトル")
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = toggleShowSearchOption) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                        )
                    }
                }
            )
        }
    ) { paddingValues ->  
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
        ) {
            if (bindingModel.isShowSearchOptions) {
                SearchOptionSheet(
                    bindingModel = bindingModel,
                    onInputCircle = onInputCircle,
                    onSelectCircle = onSelectCircle,
                    onDeleteCircle = onDeleteCircle,
                    onInputAuthor = onInputAuthor,
                    onSelectAuthor = onSelectAuthor,
                    onDeleteAuthor = onDeleteAuthor,
                    onInputTag = onInputTag,
                    onSelectTag = onSelectTag,
                    onDeleteTag = onDeleteTag,
                    onInputEvent = onInputEvent,
                    onSelectEvent = onSelectEvent,
                    onDeleteEvent = onDeleteEvent,
                    onDismiss = onDismiss,
                )
            }
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
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                if (bindingModel.searchResult.isEmpty()) {
                    item {
                        Text(
                            text = "同人誌が見つかりませんでした",
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        )
                    }
                }
                items(
                    items = bindingModel.searchResult,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchOptionSheet(
    bindingModel: SearchBindingModel,
    onInputCircle: (String) -> Unit,
    onSelectCircle: (Circle) -> Unit,
    onDeleteCircle: () -> Unit,
    onInputAuthor: (String) -> Unit,
    onSelectAuthor: (Author) -> Unit,
    onDeleteAuthor: (Author) -> Unit,
    onInputTag: (String) -> Unit,
    onSelectTag: (Tag) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onInputEvent: (String) -> Unit,
    onSelectEvent: (Event) -> Unit,
    onDeleteEvent: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        InputSection(label = "サークル名") {
            Box {
                if (bindingModel.circle != null) {
                    DeletableTextBox(
                        text = bindingModel.circle.name,
                        onClickDelete = onDeleteCircle,
                    )
                } else {
                    OutlinedTextField(
                        value = bindingModel.inputCircle,
                        onValueChange = onInputCircle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DropdownMenu(
                    expanded = bindingModel.suggestedCircles.isNotEmpty(),
                    onDismissRequest = onDismiss,
                    properties = PopupProperties(focusable = false),
                ) {
                    bindingModel.suggestedCircles.forEach { circle ->
                        DropdownMenuItem(
                            text = {
                                Text(text = circle.name)
                            },
                            onClick = {
                                onSelectCircle(circle)
                            }
                        )
                    }
                }
            }
        }
        InputSection(label = "作者名") {
            Column {
                Box {
                    OutlinedTextField(
                        value = bindingModel.inputAuthor,
                        onValueChange = onInputAuthor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = bindingModel.suggestedAuthors.isNotEmpty(),
                        onDismissRequest = onDismiss,
                        properties = PopupProperties(focusable = false),
                    ) {
                        bindingModel.suggestedAuthors.forEach { author ->
                            DropdownMenuItem(
                                text = {
                                    Text(text = author.name)
                                },
                                onClick = {
                                    onSelectAuthor(author)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    bindingModel.authors.forEach { author ->
                        DeletableTextBox(
                            text = author.name,
                            onClickDelete = {
                                onDeleteAuthor(author)
                            }
                        )
                    }
                }
            }
        }
        InputSection(label = "タグ") {
            Column {
                Box {
                    OutlinedTextField(
                        value = bindingModel.inputTag,
                        onValueChange = onInputTag,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = bindingModel.suggestedTags.isNotEmpty(),
                        onDismissRequest = onDismiss,
                        properties = PopupProperties(focusable = false),
                    ) {
                        bindingModel.suggestedTags.forEach { tag ->
                            DropdownMenuItem(
                                text = {
                                    Text(text = tag.name)
                                },
                                onClick = {
                                    onSelectTag(tag)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    bindingModel.tags.forEach { tag ->
                        DeletableTextBox(
                            text = tag.name,
                            onClickDelete = {
                                onDeleteTag(tag)
                            }
                        )
                    }
                }
            }
        }
        InputSection(label = "イベント") {
            Box {
                if (bindingModel.event != null) {
                    DeletableTextBox(
                        text = bindingModel.event.name,
                        onClickDelete = onDeleteEvent,
                    )
                } else {
                    OutlinedTextField(
                        value = bindingModel.inputEvent,
                        onValueChange = onInputEvent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DropdownMenu(
                    expanded = bindingModel.suggestedEvents.isNotEmpty(),
                    onDismissRequest = onDismiss,
                    properties = PopupProperties(focusable = false),
                ) {
                    bindingModel.suggestedEvents.forEach { event ->
                        DropdownMenuItem(
                            text = {
                                Text(text = event.name)
                            },
                            onClick = {
                                onSelectEvent(event)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SearchTemplatePreview() {
    DoujinshiManagerTheme {
        SearchTemplate(
            bindingModel = SearchBindingModel.empty().copy(
                title = "道草屋",
                isShowSearchOptions = true,
            ),
            onInputTitle = {},
            toggleShowSearchOption = {},
            onInputCircle = {},
            onSelectCircle = {},
            onDeleteCircle = {},
            onInputAuthor = {},
            onSelectAuthor = {},
            onDeleteAuthor = {},
            onInputTag = {},
            onSelectTag = {},
            onDeleteTag = {},
            onInputEvent = {},
            onSelectEvent = {},
            onDeleteEvent = {},
            onDismiss = {},
            onClickItem = {},
            loadMore = {},
            onBackPress = {},
        )
    }
}
