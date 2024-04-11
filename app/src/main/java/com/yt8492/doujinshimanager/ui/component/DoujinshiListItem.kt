package com.yt8492.doujinshimanager.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.theme.Typography

@Composable
fun DoujinshiListItem(
    item: DoujinshiBindingModel,
    onClick: (DoujinshiBindingModel) -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable { onClick(item) }
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            )
            .fillMaxWidth(),
    ) {
        Text(
            text = item.title,
            style = Typography.titleLarge,
        )
        Text(
            text = item.circleName,
            style = Typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun DoujinshiListItemPreview() {
    Column {
        DoujinshiListItem(item = DoujinshiBindingModel(DoujinshiId(""), "title1", "circle1")) {

        }
        DoujinshiListItem(item = DoujinshiBindingModel(DoujinshiId(""), "title2", "circle2")) {

        }
    }
}
