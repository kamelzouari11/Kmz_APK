package com.football.footballapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PitchGreen,
    onPrimary = SurfaceLight,
    primaryContainer = PitchGreenLight,
    onPrimaryContainer = PitchGreenDark,
    secondary = GoldAccent,
    onSecondary = InkBlue,
    secondaryContainer = Color(0xFFFFF1C2),
    onSecondaryContainer = GoldAccentDark,
    tertiary = InkBlue,
    onTertiary = SurfaceLight,
    tertiaryContainer = Color(0xFFDDE4EE),
    onTertiaryContainer = InkBlue,
    background = Cream,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF49566B),
    outline = OutlineLight,
    error = Color(0xFFB3261E),
    onError = SurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EDFA1),
    onPrimary = PitchGreenDark,
    primaryContainer = PitchGreenDark,
    onPrimaryContainer = PitchGreenLight,
    secondary = Color(0xFFFFD54F),
    onSecondary = InkBlue,
    secondaryContainer = GoldAccentDark,
    onSecondaryContainer = Color(0xFFFFF1C2),
    tertiary = Color(0xFF9FB4D0),
    onTertiary = InkBlue,
    tertiaryContainer = InkBlueLight,
    onTertiaryContainer = Color(0xFFDDE4EE),
    background = InkBlue,
    onBackground = Color(0xFFE6ECF3),
    surface = InkBlueLight,
    onSurface = Color(0xFFE6ECF3),
    surfaceVariant = Color(0xFF253857),
    onSurfaceVariant = Color(0xFFB8C2D1),
    outline = OutlineDark,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun FootballScheduleAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
