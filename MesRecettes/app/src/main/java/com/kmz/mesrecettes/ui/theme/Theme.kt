package com.kmz.mesrecettes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
        darkColorScheme(
                primary = Color(0xFFBB86FC),
                secondary = Color(0xFF03DAC5),
                tertiary = Color(0xFF3700B3),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onTertiary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White,
        )

@Composable
fun MesRecettesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme) {
        androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides
                        androidx.compose.material3.LocalTextStyle.current.copy(
                                textDirection =
                                        androidx.compose.ui.text.style.TextDirection.Content,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        ),
                content = content
        )
    }
}
