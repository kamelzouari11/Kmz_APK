package com.example.simpleradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Full-screen lyrics overlay used in portrait mode. Displays lyrics with translate/close controls
 * and a bilingual view.
 */
@Composable
fun PortraitLyricsOverlay(
        lyricsText: String?,
        isFetchingLyrics: Boolean,
        isTranslating: Boolean,
        translatedLyrics: String?,
        onToggleTranslation: () -> Unit,
        onClose: () -> Unit,
        bilingualLyricsContent: @Composable (String, String?, Boolean, Modifier) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).zIndex(10f).padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleTranslation) {
                    Text(
                            if (isTranslating) "Original" else "Traduire (FR)",
                            color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                        onClick = onClose,
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) { Text("Fermer") }
            }

            if (isFetchingLyrics) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                bilingualLyricsContent(
                        lyricsText ?: "",
                        translatedLyrics,
                        isTranslating,
                        Modifier.fillMaxSize()
                )
            }
        }
    }
}
