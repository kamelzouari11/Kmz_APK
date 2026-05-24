package com.kmz.shoppinglist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
        darkColorScheme(
                primary = AccentBlue,
                secondary = AccentGreen,
                tertiary = PriorityImportant,
                background = Black,
                surface = DarkGray,
                onPrimary = White,
                onSecondary = White,
                onTertiary = White,
                onBackground = White,
                onSurface = White,
                surfaceVariant = MediumGray,
                onSurfaceVariant = TextGray,
                error = AccentRed,
                onError = White
        )

@Composable
fun ShoppingListTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
