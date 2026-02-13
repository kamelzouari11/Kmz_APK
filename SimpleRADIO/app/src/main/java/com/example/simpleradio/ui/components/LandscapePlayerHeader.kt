package com.example.simpleradio.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Landscape player header row containing Back, Timer, Cast, and Lyrics buttons. Supports TV D-Pad
 * focus highlighting.
 */
@Composable
fun LandscapePlayerHeader(
        onBack: () -> Unit,
        onLyrics: () -> Unit,
        showLyricsButton: Boolean,
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
            modifier = modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        // --- BACK BUTTON ---
        var isBackFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = onBack,
                modifier =
                        Modifier.onFocusChanged { isBackFocused = it.isFocused }
                                .background(
                                        if (isBackFocused) Color.White
                                        else Color.White.copy(alpha = 0.15f),
                                        MaterialTheme.shapes.small
                                )
        ) {
            Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    null,
                    tint = if (isBackFocused) Color.Black else Color.White,
                    modifier = Modifier.size(32.dp)
            )
        }

        // --- TIMER BUTTON ---
        Box {
            var showTimerMenu by remember { mutableStateOf(false) }
            var isTimerFocused by remember { mutableStateOf(false) }
            IconButton(
                    onClick = { showTimerMenu = true },
                    modifier =
                            Modifier.onFocusChanged { isTimerFocused = it.isFocused }
                                    .background(
                                            if (isTimerFocused) Color.White
                                            else if (sleepTimerTimeLeft != null)
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.3f
                                                    )
                                            else Color.White.copy(alpha = 0.15f),
                                            MaterialTheme.shapes.small
                                    )
            ) {
                Icon(
                        Icons.Default.Timer,
                        null,
                        tint =
                                if (isTimerFocused) Color.Black
                                else if (sleepTimerTimeLeft != null)
                                        MaterialTheme.colorScheme.primary
                                else Color.White,
                        modifier = Modifier.size(28.dp)
                )
            }
            DropdownMenu(expanded = showTimerMenu, onDismissRequest = { showTimerMenu = false }) {
                listOf(10, 20, 30, 40).forEach { mins ->
                    DropdownMenuItem(
                            text = { Text("$mins min") },
                            onClick = {
                                onSetSleepTimer(mins)
                                showTimerMenu = false
                            }
                    )
                }
                if (sleepTimerTimeLeft != null) {
                    DropdownMenuItem(
                            text = { Text("Annuler", color = Color.Red) },
                            onClick = {
                                onSetSleepTimer(null)
                                showTimerMenu = false
                            }
                    )
                }
            }
        }

        // --- CAST BUTTON ---
        CastButton()

        // --- LYRICS BUTTON ---
        var isLyricsFocused by remember { mutableStateOf(false) }

        Box(
                modifier =
                        Modifier.height(40.dp)
                                .onFocusChanged { isLyricsFocused = it.isFocused }
                                .background(
                                        color =
                                                if (isLyricsFocused) Color.White
                                                else if (showLyricsButton)
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.3f)
                                                else Color.Transparent,
                                        shape = MaterialTheme.shapes.small
                                )
                                .border(
                                        width = 1.dp,
                                        color =
                                                if (isLyricsFocused) Color.Transparent
                                                else if (showLyricsButton)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.5f
                                                        )
                                                else Color.Gray.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.small
                                )
                                .clickable {
                                    if (showLyricsButton) {
                                        onLyrics()
                                    } else {
                                        Toast.makeText(
                                                        context,
                                                        "Pas de paroles disponibles",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                                .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        imageVector = Icons.AutoMirrored.Filled.Subject,
                        contentDescription = "Lyrics",
                        tint =
                                if (isLyricsFocused) Color.Black
                                else if (showLyricsButton) MaterialTheme.colorScheme.primary
                                else Color.Gray,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "PAROLES",
                        style = MaterialTheme.typography.labelMedium,
                        color =
                                if (isLyricsFocused) Color.Black
                                else if (showLyricsButton) MaterialTheme.colorScheme.primary
                                else Color.Gray
                )
            }
        }
    }
}
