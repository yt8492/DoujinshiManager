package com.yt8492.doujinshimanager.ui.register

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.ui.component.DeletableTextBox
import com.yt8492.doujinshimanager.ui.component.InputSection
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegisterTemplate(
    bindingModel: RegisterBindingModel,
    onInputTitle: (String) -> Unit,
    onFocusCircle: () -> Unit,
    onInputCircle: (String) -> Unit,
    onSelectCircle: (Circle) -> Unit,
    onDeleteCircle: () -> Unit,
    onFocusAuthor: () -> Unit,
    onInputAuthor: (String) -> Unit,
    onEnterAuthor: () -> Unit,
    onSelectAuthor: (Author) -> Unit,
    onDeleteAuthor: (Author) -> Unit,
    onFocusTag: () -> Unit,
    onInputTag: (String) -> Unit,
    onEnterTag: () -> Unit,
    onSelectTag: (Tag) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onFocusEvent: () -> Unit,
    onInputEvent: (String) -> Unit,
    onEnterEventName: () -> Unit,
    onEnterEventDate: (LocalDate?, LocalDate?) -> Unit,
    onSelectEvent: (Event) -> Unit,
    onDeleteEvent: () -> Unit,
    onSelectPubDate: (LocalDate) -> Unit,
    onDeletePubDate: () -> Unit,
    onClickAddImage: () -> Unit,
    onClickTakePicture: () -> Unit,
    onDeleteImage: (String) -> Unit,
    onClickRegister: () -> Unit,
    onDismiss: () -> Unit,
    onBackPress: () -> Unit,
) {
    val focusRequester = remember {
        FocusRequester()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "同人誌登録")
                },
                actions = {
                    TextButton(onClick = onClickRegister) {
                        Text(text = "登録")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InputSection(label = "タイトル") {
                OutlinedTextField(
                    value = bindingModel.title,
                    onValueChange = onInputTitle,
                    singleLine = true,
                )
            }
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
                            singleLine = true,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusCircle()
                                    }
                                },
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = bindingModel.inputAuthor,
                                onValueChange = onInputAuthor,
                                singleLine = true,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusAuthor()
                                        }
                                    },
                            )
                            TextButton(
                                onClick = onEnterAuthor,
                                enabled = bindingModel.inputAuthor.isNotBlank(),
                            ) {
                                Text(text = "追加")
                            }
                        }
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
                        Row {
                            OutlinedTextField(
                                value = bindingModel.inputTag,
                                onValueChange = onInputTag,
                                singleLine = true,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusTag()
                                        }
                                    },
                            )
                            TextButton(
                                onClick = onEnterTag,
                                enabled = bindingModel.inputTag.isNotBlank(),
                            ) {
                                Text(text = "追加")
                            }
                        }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = bindingModel.inputEvent,
                                onValueChange = onInputEvent,
                                singleLine = true,
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusEvent()
                                        }
                                    },
                            )
                            TextButton(
                                onClick = onEnterEventName,
                                enabled = bindingModel.inputEvent.isNotBlank()
                            ) {
                                Text(text = "追加")
                            }
                        }
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
                val dateRangePickerState = rememberDateRangePickerState()
                if (bindingModel.isShowEventDatePicker) {
                    val sheetState = rememberModalBottomSheetState()
                    ModalBottomSheet(
                        sheetState = sheetState,
                        onDismissRequest = onDismiss,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = onDismiss
                            ) {
                                Text(text = "Cancel")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    val startDate = dateRangePickerState.selectedStartDateMillis?.let { start ->
                                        Instant.fromEpochMilliseconds(start)
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                            .date
                                    }
                                    val endDate = dateRangePickerState.selectedEndDateMillis?.let { end ->
                                        Instant.fromEpochMilliseconds(end)
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                            .date
                                    }
                                    onEnterEventDate(startDate, endDate)
                                },
                            ) {
                                Text(text = "OK")
                            }
                        }
                        DateRangePicker(state = dateRangePickerState)
                    }
                }
            }
            InputSection(label = "発行日") {
                var isShowDatePicker by remember {
                    mutableStateOf(false)
                }
                if (bindingModel.pubDate != null) {
                    DeletableTextBox(
                        text = bindingModel.pubDate
                            .toJavaLocalDate()
                            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                        onClickDelete = onDeletePubDate,
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            isShowDatePicker = true
                        }
                    ) {
                        Text(text = "選択")
                    }
                }
                if (isShowDatePicker) {
                    val initMilli = remember(bindingModel.event) {
                        bindingModel.event
                            ?.term
                            ?.start
                            ?.toJavaLocalDate()
                            ?.atStartOfDay(ZoneId.systemDefault())
                            ?.toInstant()
                            ?.toEpochMilli()
                    }
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = initMilli,
                    )
                    DatePickerDialog(
                        onDismissRequest = {
                            isShowDatePicker = false
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let {
                                        val date = Instant.fromEpochMilliseconds(it)
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                            .date
                                        onSelectPubDate(date)
                                        isShowDatePicker = false
                                    }
                                }
                            ) {
                                Text(text = "OK")
                            }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }
            InputSection(label = "画像") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(bindingModel.imagePaths) {
                        Box(
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(File(it))
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .size(
                                        width = 128.dp,
                                        height = 96.dp,
                                    ),
                            )
                            IconButton(
                                onClick = {
                                    onDeleteImage(it)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "削除",
                                )
                            }
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    onClickAddImage()
                                }
                                .size(
                                    width = 128.dp,
                                    height = 96.dp,
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "追加",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    onClickTakePicture()
                                }
                                .size(
                                    width = 128.dp,
                                    height = 96.dp,
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraEnhance,
                                contentDescription = "撮影",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RegisterTemplatePreview() {
    DoujinshiManagerTheme(darkTheme = true) {
        RegisterTemplate(
            bindingModel = RegisterBindingModel.empty()
                .copy(
                    title = "hoge",
                    circle = Circle.create("hoge"),
                    inputAuthor = "hoge",
                    suggestedAuthors = listOf(
                        Author.create("hoge"),
                        Author.create("hogehoge"),
                    ),
                    authors = listOf(
                        Author.create("hoge"),
                    )
                ),
            onInputTitle = {},
            onFocusCircle = {},
            onInputCircle = {},
            onSelectCircle = {},
            onDeleteCircle = {},
            onFocusAuthor = {},
            onInputAuthor = {},
            onEnterAuthor = {},
            onSelectAuthor = {},
            onDeleteAuthor = {},
            onFocusTag = {},
            onInputTag = {},
            onEnterTag = {},
            onSelectTag = {},
            onDeleteTag = {},
            onFocusEvent = {},
            onInputEvent = {},
            onEnterEventName = {},
            onEnterEventDate = { _, _ -> },
            onSelectEvent = {},
            onDeleteEvent = {},
            onSelectPubDate = {},
            onDeletePubDate = {},
            onClickAddImage = {},
            onClickTakePicture = {},
            onDeleteImage = {},
            onClickRegister = {},
            onDismiss = {},
            onBackPress = {},
        )
    }
}
