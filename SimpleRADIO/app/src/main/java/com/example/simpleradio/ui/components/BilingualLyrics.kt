package com.example.simpleradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Displays lyrics in a bilingual format: original text with optional translation. Supports D-Pad
 * navigation line by line for TV compatibility.
 */
@Composable
fun BilingualLyrics(
        original: String,
        translated: String?,
        isTranslating: Boolean,
        modifier: Modifier = Modifier
) {
    val originalLines = remember(original) { original.split("\n") }
    val translatedLines = remember(translated) { translated?.split("\n") ?: emptyList() }
    val listState = rememberLazyListState()

    LazyColumn(state = listState, modifier = modifier.padding(top = 8.dp)) {
        items(originalLines.size) { i ->
            val orig = originalLines[i]
            if (orig.isBlank()) {
                Spacer(Modifier.height(16.dp))
            } else {
                var isFocused by remember { mutableStateOf(false) }
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                                if (isFocused)
                                                        Color(0xFF252525) // Gris foncé proche dark
                                                // theme
                                                else Color.Transparent,
                                                MaterialTheme.shapes.small
                                        )
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable() // MAGIC: Allows D-Pad
                                        // navigation line by line
                                        .padding(8.dp)
                ) {
                    Text(
                            text = orig,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                    )
                    if (isTranslating &&
                                    translated != null &&
                                    i < translatedLines.size &&
                                    translatedLines[i].isNotBlank()
                    ) {
                        Text(
                                text = translatedLines[i],
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
