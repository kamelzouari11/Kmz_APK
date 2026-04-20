package com.example.simpleiptv.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bouton étoile pour ajouter/retirer des favoris dans l'overlay du player.
 */
@Composable
fun FavoriteStarButton(
        isFavorite: Boolean,
        onClick: () -> Unit
) {
        var isStarFocused by remember { mutableStateOf(false) }

        Surface(
                modifier = Modifier
                        .size(40.dp)
                        .onFocusChanged { isStarFocused = it.isFocused }
                        .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClick
                        )
                        .focusable(),
                shape = MaterialTheme.shapes.small,
                color = when {
                        isStarFocused -> Color.White
                        else -> Color.Transparent
                },
                border = when {
                        isStarFocused -> BorderStroke(2.dp, Color.White)
                        else -> BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                }
        ) {
                Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (isStarFocused) Color.Black else if (isFavorite) Color.Green else Color.Gray,
                        modifier = Modifier.size(24.dp)
                )
        }
}
