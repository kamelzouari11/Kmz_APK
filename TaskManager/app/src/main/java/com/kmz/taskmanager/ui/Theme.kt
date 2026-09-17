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
                onSecondary = Black,
                onBackground = Color.White,
                onSurface = Color.White,
                surfaceVariant = SurfaceVariant,
                onSurfaceVariant = FolderLabelColor,
                primaryContainer = SelectedTaskBg,
                onPrimaryContainer = Color(0xFFC0FFDB),
                secondaryContainer = SurfaceVariant,
                onSecondaryContainer = Secondary,
                tertiary = Color(0xFFB0EE82),
                onTertiary = Black,
                tertiaryContainer = Color(0xFF284522),
                onTertiaryContainer = Color(0xFFCDF5B7),
                inverseSurface = Color(0xFFDDF5E7),
                inverseOnSurface = Black,
                inversePrimary = Color(0xFF006D40),
                surfaceTint = Primary,
                outline = Color(0xFF648B76),
                outlineVariant = Color(0xFF2C4B39),
                surfaceDim = Black,
                surfaceBright = Color(0xFF294535),
                surfaceContainerLowest = Color(0xFF040D08),
                surfaceContainerLow = DarkGray,
                surfaceContainer = TaskCardBg,
                surfaceContainerHigh = SurfaceVariant,
                surfaceContainerHighest = InputFieldBg
        )

@Composable
fun MyTasksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography(), content = content)
}
