package com.example.simpleiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun HeaderIconButton(
        icon: ImageVector,
        desc: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        tintNormal: Color = MaterialTheme.colorScheme.primary,
        isSelected: Boolean = false
) {
        var isFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = onClick,
                modifier =
                        modifier.size(60.dp)
                                .onFocusChanged { state -> isFocused = state.isFocused }
                                .scale(if (isFocused) 1.05f else 1f)
                                .background(
                                        when {
                                                isFocused -> Color.White
                                                isSelected -> Color.Cyan.copy(alpha = 0.2f)
                                                else -> Color.Transparent
                                        },
                                        MaterialTheme.shapes.small
                                )
        ) {
                Icon(
                        icon,
                        desc,
                        tint = if (isFocused) Color.Black else tintNormal,
                        modifier = Modifier.size(48.dp)
                )
        }
}
