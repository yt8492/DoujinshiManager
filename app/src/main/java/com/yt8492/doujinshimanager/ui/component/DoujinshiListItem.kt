package com.yt8492.doujinshimanager.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.theme.Typography

@Composable
fun DoujinshiListItem(
    item: DoujinshiBindingModel,
    onClick: (DoujinshiBindingModel) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick(item) }
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            )
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            Text(
                text = item.title,
                style = Typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.circleName,
                style = Typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.image != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.image)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .size(
                        width = 96.dp,
                        height = 72.dp,
                    )
            )
        }
    }
}

@Preview
@Composable
private fun DoujinshiListItemPreview() {
    Column {
        DoujinshiListItem(item = DoujinshiBindingModel(DoujinshiId(""), "title1", "circle1", null)) {

        }
        DoujinshiListItem(item = DoujinshiBindingModel(DoujinshiId(""), "title2", "circle2", null)) {

        }
    }
}
