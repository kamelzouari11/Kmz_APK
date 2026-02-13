package com.example.simpleradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Reusable artwork/logo display with cycling button. Used in both Portrait and Landscape player
 * layouts.
 */
@Composable
fun ArtworkDisplay(
        artworkUrl: String?,
        onCycleArtwork: () -> Unit,
        modifier: Modifier = Modifier,
        syncButtonSize: Dp = 40.dp,
        syncIconSize: Dp = 20.dp,
        defaultIconSize: Dp = 120.dp,
        syncButtonPadding: PaddingValues = PaddingValues(8.dp)
) {
    Box(modifier = modifier.background(Color.Black)) {
        // Fond par défaut
        Icon(
                Icons.Default.Radio,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(defaultIconSize),
                tint = Color.White.copy(alpha = 0.2f)
        )

        if (artworkUrl != null) {
            AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
            )
        }

        // --- BOUTON SYNC (Cycling) ---
        var isSyncFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = onCycleArtwork,
                modifier =
                        Modifier.align(Alignment.BottomEnd)
                                .padding(syncButtonPadding)
                                .onFocusChanged { isSyncFocused = it.isFocused }
                                .background(
                                        if (isSyncFocused) Color.White
                                        else Color.Black.copy(alpha = 0.3f),
                                        CircleShape
                                )
                                .size(syncButtonSize)
        ) {
            Icon(
                    Icons.Default.Sync,
                    null,
                    tint = if (isSyncFocused) Color.Black else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(syncIconSize)
            )
        }
    }
}
