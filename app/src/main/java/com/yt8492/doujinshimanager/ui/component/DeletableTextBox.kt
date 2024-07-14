package com.yt8492.doujinshimanager.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme

@Composable
fun DeletableTextBox(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    onClickDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(
                width = 1.dp,
                color = color,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "delete",
                tint = color
            )
        }
    }
}

@Preview
@Composable
private fun DeletableTextBoxPreview() {
    DoujinshiManagerTheme {
        DeletableTextBox(text = "Daitarabochi") {

        }
    }
}
