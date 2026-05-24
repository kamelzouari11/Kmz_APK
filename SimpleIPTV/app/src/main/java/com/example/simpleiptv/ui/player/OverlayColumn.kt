package com.example.simpleiptv.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Helper composable for overlay columns (title + content).
 */
@Composable
fun OverlayColumn(
        title: String,
        width: Dp,
        content: @Composable ColumnScope.() -> Unit
) {
        Column(modifier = Modifier.fillMaxHeight().width(width)) {
                Text(
                        text = title,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall
                )
                content()
        }
}
