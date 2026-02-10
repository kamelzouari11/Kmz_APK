package com.kmz.taskmanager.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
        darkColorScheme(
                primary = Primary,
                secondary = Secondary,
                background = Black,
                surface = DarkGray,
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White,
                surfaceVariant = SurfaceVariant,
                onSurfaceVariant = Color.LightGray
        )

@Composable
fun TaskManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography(), content = content)
}
