package com.example.myiptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val MyIptvDarkColors = darkColorScheme(
    primary = MyIptvPalette.Primary,
    onPrimary = MyIptvPalette.Anthracite,
    primaryContainer = MyIptvPalette.PrimaryHover,
    onPrimaryContainer = MyIptvPalette.Anthracite,
    secondary = MyIptvPalette.AccentSecondary,
    onSecondary = MyIptvPalette.TextPrimary,
    secondaryContainer = MyIptvPalette.AccentSecondaryHover,
    onSecondaryContainer = MyIptvPalette.TextPrimary,
    tertiary = MyIptvPalette.Info,
    onTertiary = MyIptvPalette.Background,
    background = MyIptvPalette.Background,
    onBackground = MyIptvPalette.TextPrimary,
    surface = MyIptvPalette.Surface,
    onSurface = MyIptvPalette.TextPrimary,
    surfaceVariant = MyIptvPalette.Card,
    onSurfaceVariant = MyIptvPalette.TextSecondary,
    error = MyIptvPalette.Negative,
    onError = MyIptvPalette.TextPrimary,
    outline = MyIptvPalette.Border,
    outlineVariant = MyIptvPalette.Disabled,
    inverseSurface = MyIptvPalette.TextPrimary,
    inverseOnSurface = MyIptvPalette.Background,
    inversePrimary = MyIptvPalette.Primary,
    scrim = MyIptvPalette.Background,
    surfaceTint = MyIptvPalette.Primary,
)

@Immutable
data class TvTextSizes(
    val country: TextUnit,
    val body: TextUnit,
    val bodySmall: TextUnit,
    val title: TextUnit,
    val menu: TextUnit,
    val hero: TextUnit,
)

val LocalTvTextSizes = staticCompositionLocalOf {
    TvTextSizes(22.sp, 16.sp, 13.sp, 18.sp, 15.sp, 28.sp)
}

@Composable
fun MyIptvTheme(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val scale = if (configuration.screenHeightDp > configuration.screenWidthDp) {
        1f
    } else {
        min(
            configuration.screenWidthDp / 960f,
            configuration.screenHeightDp / 540f,
        ).coerceIn(0.82f, 1.5f)
    }
    val sizes = TvTextSizes(
        country = (22f * scale).sp,
        body = (16f * scale).sp,
        bodySmall = (13f * scale).sp,
        title = (18f * scale).sp,
        menu = (15f * scale).sp,
        hero = (28f * scale).sp,
    )
    val typography = Typography(
        bodyLarge = TextStyle(fontSize = sizes.body, lineHeight = sizes.body * 1.24f),
        bodyMedium = TextStyle(fontSize = sizes.bodySmall, lineHeight = sizes.bodySmall * 1.25f),
        titleMedium = TextStyle(
            fontSize = sizes.title,
            lineHeight = sizes.title * 1.18f,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineMedium = TextStyle(
            fontSize = sizes.hero,
            lineHeight = sizes.hero * 1.12f,
            fontWeight = FontWeight.Bold,
        ),
        labelLarge = TextStyle(
            fontSize = sizes.menu,
            lineHeight = sizes.menu * 1.15f,
            fontWeight = FontWeight.SemiBold,
        ),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalTvTextSizes provides sizes) {
        MaterialTheme(
            colorScheme = MyIptvDarkColors,
            typography = typography,
            content = content,
        )
    }
}
