package com.example.simpleradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Landscape lyrics overlay panel. Occupies 75% of the screen width (12/16), aligned to the left.
 * Includes translate/close controls with TV D-Pad focus support.
 */
@Composable
fun BoxScope.LandscapeLyricsOverlay(
        lyricsText: String?,
        isFetchingLyrics: Boolean,
        isTranslating: Boolean,
        translatedLyrics: String?,
        onTranslate: () -> Unit,
        onCloseLyrics: () -> Unit
) {
    val closeButtonFocusRequester = remember { FocusRequester() }

    // Force le focus sur le bouton Fermer dès l'ouverture
    LaunchedEffect(Unit) {
        try {
            closeButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
            modifier =
                    Modifier.fillMaxHeight()
                            .fillMaxWidth(0.75f) // 12/16 de l'écran (= 75%)
                            .align(Alignment.CenterStart)
                            .background(Color.Black) // Totalement opaque
                            .zIndex(10f) // S'assure d'être au-dessus
                            .padding(24.dp)
    ) {
        if (isFetchingLyrics) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    var isTranslateFocused by remember { mutableStateOf(false) }
                    TextButton(
                            onClick = onTranslate,
                            modifier =
                                    Modifier.onFocusChanged { isTranslateFocused = it.isFocused }
                                            .background(
                                                    if (isTranslateFocused) Color.White
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.small
                                            )
                    ) {
                        Text(
                                if (isTranslating) "Original" else "Translate (FR)",
                                color =
                                        if (isTranslateFocused) Color.Black
                                        else MaterialTheme.colorScheme.primary
                        )
                    }
                    var isCloseFocused by remember { mutableStateOf(false) }
                    IconButton(
                            onClick = onCloseLyrics,
                            modifier =
                                    Modifier.focusRequester(closeButtonFocusRequester)
                                            .onFocusChanged { isCloseFocused = it.isFocused }
                                            .background(
                                                    if (isCloseFocused) Color.White
                                                    else Color.Transparent,
                                                    CircleShape
                                            )
                    ) {
                        Icon(
                                Icons.Default.Close,
                                null,
                                tint = if (isCloseFocused) Color.Black else Color.White
                        )
                    }
                }

                BilingualLyrics(
                        original = lyricsText ?: "",
                        translated = translatedLyrics,
                        isTranslating = isTranslating,
                        modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Petit bouton fermer discret en haut à droite de l'overlay pour la
        // souris/touch
        IconButton(onClick = onCloseLyrics, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Close, "Fermer", tint = Color.Gray)
        }
    }
}
