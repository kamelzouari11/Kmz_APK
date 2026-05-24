package com.example.simpleiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Item réutilisable pour les overlays du player (pays, catégorie, chaîne).
 * Gère automatiquement le focus, la sélection et le background.
 */
@Composable
fun OverlayListItem(
        text: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        focusRequester: FocusRequester? = null,
        content: (@Composable (isFocused: Boolean) -> Unit)? = null
) {
        var isFocused by remember { mutableStateOf(false) }
        Row(
                modifier = modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onClick() }
                        .focusable()
                                 .background(
                                         color = when {
                                             isFocused -> Color.White
                                             isSelected -> Color(0xFFBB86FC).copy(alpha = 0.3f)
                                             else -> Color.Transparent
                                         },
                                         shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                 )
                                 .padding(horizontal = 12.dp, vertical = 8.dp),

                verticalAlignment = Alignment.CenterVertically
        ) {
                if (content != null) {
                        content(isFocused)
                } else {
                                 Text(
                                         text = text,
                                         color = when {
                                             isFocused -> Color.Black
                                             isSelected -> Color(0xFFBB86FC)
                                             else -> Color.White.copy(alpha = 0.7f)
                                         },
                                         style = MaterialTheme.typography.bodyLarge,
                                         maxLines = 1,
                                         overflow = TextOverflow.Ellipsis
                                     )

                }
        }
}
