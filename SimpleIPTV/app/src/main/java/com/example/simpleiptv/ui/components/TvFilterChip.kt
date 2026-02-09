package com.example.simpleiptv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun TvFilterChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: String?,
        icon: ImageVector? = null,
        modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
            modifier =
                    modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .scale(if (isFocused) 1.1f else 1f)
                            .clickable { onClick() }
                            .focusable(),
            shape = CircleShape,
            color =
                    if (isFocused) Color.White.copy(alpha = 0.9f)
                    else if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isFocused) Color.Black else MaterialTheme.colorScheme.onSurface
                )
                if (label != null) {
                    Spacer(Modifier.width(8.dp))
                }
            }
            if (label != null) {
                Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isFocused) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
