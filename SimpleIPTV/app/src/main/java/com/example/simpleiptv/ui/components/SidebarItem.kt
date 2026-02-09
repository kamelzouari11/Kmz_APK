package com.example.simpleiptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SidebarItem(
        text: String,
        icon: ImageVector? = null,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        onDelete: (() -> Unit)? = null
) {
        var isItemFocused by remember { mutableStateOf(false) }
        var isDeleteFocused by remember { mutableStateOf(false) }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Card(
                        modifier =
                                modifier.weight(1f)
                                        .height(60.dp)
                                        .onFocusChanged { state -> isItemFocused = state.isFocused }
                                        .scale(if (isItemFocused) 1.02f else 1f)
                                        .clickable { onClick() }
                                        .focusable(),
                        border =
                                if (isSelected && !isItemFocused) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                when {
                                                        isItemFocused ->
                                                                Color.White.copy(alpha = 0.95f)
                                                        isSelected ->
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer
                                                        else -> MaterialTheme.colorScheme.surface
                                                }
                                )
                ) {
                        Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                if (icon != null) {
                                        Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint =
                                                        when {
                                                                isItemFocused -> Color.Black
                                                                isSelected ->
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else -> Color.Gray
                                                        }
                                        )
                                        Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                        text = text,
                                        maxLines = 1,
                                        color =
                                                if (isItemFocused) Color.Black
                                                else Color.Unspecified
                                )
                        }
                }

                if (onDelete != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                                modifier =
                                        Modifier.size(48.dp)
                                                .onFocusChanged { state ->
                                                        isDeleteFocused = state.isFocused
                                                }
                                                .scale(if (isDeleteFocused) 1.1f else 1f)
                                                .clickable { onDelete() }
                                                .focusable(),
                                shape = CircleShape,
                                color =
                                        if (isDeleteFocused) Color.White.copy(alpha = 0.2f)
                                        else Color.Transparent
                        ) {
                                Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint =
                                                        if (isDeleteFocused) Color.Red
                                                        else Color.Gray
                                        )
                                }
                        }
                }
        }
}
