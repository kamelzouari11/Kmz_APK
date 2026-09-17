package com.example.simpleradio.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Reusable display for the single cover or station logo selected by PlaybackManagement.
 */
@Composable
fun ArtworkDisplay(
        artworkUrl: String?,
        modifier: Modifier = Modifier,
        defaultIconSize: Dp = 120.dp,
        showLogoValidation: Boolean = false,
        logoIsConfirmed: Boolean = false,
        logoCandidatePosition: Int = 0,
        logoCandidateCount: Int = 0,
        onArtworkLoadError: (String) -> Unit = {},
        onConfirmLogo: () -> Unit = {},
        onRejectLogo: () -> Unit = {},
        onUnconfirmLogo: () -> Unit = {}
) {
    Box(modifier = modifier.background(Color.Black)) {
        // Fond par défaut
        Icon(
                Icons.Default.Radio,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(defaultIconSize),
                tint = Color.White.copy(alpha = 0.2f)
        )

        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onError = { result ->
                        Log.e(
                                "StationArtwork",
                                "Impossible de charger le logo: $artworkUrl",
                                result.result.throwable
                        )
                        onArtworkLoadError(artworkUrl)
                    }
            )
        }

        if (showLogoValidation && !artworkUrl.isNullOrBlank()) {
            Surface(
                    modifier =
                            Modifier.align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 48.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Black.copy(alpha = 0.72f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logoCandidateCount > 1 && logoCandidatePosition > 0) {
                        Text(
                                text = "$logoCandidatePosition/$logoCandidateCount",
                                color = Color.White,
                                modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                        )
                    }
                    IconButton(onClick = onRejectLogo) {
                        Icon(
                                Icons.Default.Close,
                                contentDescription = "Ce logo est incorrect",
                                tint = Color(0xFFFF6B6B)
                        )
                    }
                    IconButton(onClick = onConfirmLogo) {
                        Icon(
                                Icons.Default.Check,
                                contentDescription = "Confirmer ce logo",
                                tint = Color(0xFF69F0AE)
                        )
                    }
                }
            }
        } else if (logoIsConfirmed && !artworkUrl.isNullOrBlank()) {
            Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Black.copy(alpha = 0.72f)
            ) {
                IconButton(onClick = onUnconfirmLogo) {
                    Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Annuler la confirmation de ce logo",
                            tint = Color(0xFF69F0AE)
                    )
                }
            }
        }
    }
}
