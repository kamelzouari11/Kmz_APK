package com.kmz.shazamplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ShazamPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme =
                    darkColorScheme(
                            primary = Color(0xFF0088FF),
                            background = Color.Black,
                            surface = Color(0xFF121212)
                    ),
            content = content
    )
}
