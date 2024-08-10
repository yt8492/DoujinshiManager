package com.yt8492.doujinshimanager.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.ui.component.TextBox
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailTemplate(
    doujinshi: Doujinshi?,
    onBackPress: () -> Unit,
    onClickCircle: (Circle) -> Unit,
    onClickAuthor: (Author) -> Unit,
    onClickTag: (Tag) -> Unit,
    onClickEvent: (Event) -> Unit,
    onClickEdit: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = doujinshi?.title ?: "",
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackPress,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onClickEdit,
                    ) {
                        Text(text = "編集")
                    }
                }
            )
        }
    ) { paddingValues ->
        doujinshi ?: return@Scaffold
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "タイトル",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(text = doujinshi.title)
            doujinshi.circle?.let { circle ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "サークル",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = circle.name,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        onClickCircle(circle)
                    }
                )
            }
            if (doujinshi.authors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "作者",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    doujinshi.authors.forEach {
                        TextBox(
                            text = it.name,
                            onClick = {
                                onClickAuthor(it)
                            }
                        )
                    }
                }
            }
            if (doujinshi.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "タグ",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    doujinshi.tags.forEach {
                        TextBox(
                            text = it.name,
                            onClick = {
                                onClickTag(it)
                            }
                        )
                    }
                }
            }
            doujinshi.event?.let { event ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "イベント",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = event.name,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        onClickEvent(event)
                    }
                )
                event.term?.let { term ->
                    if (term.start == term.end) {
                        Text(
                            text = term.start.toJavaLocalDate().format(formatter),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    } else {
                        val start = term.start.toJavaLocalDate().format(formatter)
                        val end = term.end.toJavaLocalDate().format(formatter)
                        Text(
                            text = "%s ~ %s".format(start, end),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            doujinshi.pubDate?.let { pubDate ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "発行日",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(text = pubDate.toJavaLocalDate().format(formatter))
            }
            if (doujinshi.imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "画像",
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyRow {
                    items(doujinshi.imagePaths) { path ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(path).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(256.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun DetailTemplatePreview() {
    DoujinshiManagerTheme {
        DetailTemplate(
            doujinshi = Doujinshi.create(
                title = "Daitarabochi Techbook",
                circle = Circle.create("Daitarabochi"),
                authors = listOf(
                    Author.create("マヤミト"),
                    Author.create("チキング"),
                ),
                tags = listOf(
                    Tag.create("技術書"),
                    Tag.create("Kotlin"),
                    Tag.create("Swift"),
                ),
                event = Event.create(
                    name = "技術書典15",
                    term = Period(
                        start = LocalDate.parse("2023-11-11"),
                        end = LocalDate.parse("2023-11-26")
                    ),
                ),
                pubDate = LocalDate.parse("2023-11-12"),
                imagePaths = listOf(),
            ),
            onBackPress = {},
            onClickCircle = {},
            onClickAuthor = {},
            onClickTag = {},
            onClickEvent = {},
            onClickEdit = {},
        )
    }
}
