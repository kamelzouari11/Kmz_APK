package com.example.simpleradio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SidebarItem(
        text: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        onDelete: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
            modifier =
                    modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.clickable {
                        onClick()
                    },
            border =
                    if (isSelected && !isFocused)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else null,
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isFocused) Color.White
                                    else if (isSelected) Color(0xFF333333) // Dark Gray for selected
                                    else Color.Transparent // Transparent for unselected (shows
                            // sidebar bg)
                            )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (icon != null) {
                    Icon(
                            icon,
                            null,
                            tint =
                                    if (isFocused) Color.Black
                                    else Color.White // Force White for unselected icons
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                        text,
                        color =
                                if (isFocused) Color.Black
                                else if (isSelected) Color.White
                                else Color.White, // Force visible white for unselected
                        maxLines = 1
                )
            }
            if (onDelete != null)
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(
                                Icons.Default.Delete,
                                null,
                                tint = if (isFocused) Color.Red else Color.Red.copy(alpha = 0.5f)
                        )
                    }
        }
    }
}
