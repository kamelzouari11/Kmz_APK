package com.example.myiptv.ui.theme

import androidx.compose.ui.graphics.Color

object MyIptvPalette {
    val Background = Color(0xFF010306)
    val Surface = Color(0xFF04070B)
    val Card = Color(0xFF080C12)
    val CardHover = Color(0xFF101820)
    val ButtonBackground = Color(0xFF263640)
    val TextFieldBackground = Color(0xFF526C7A)
    val ActiveSurface = Color(0xFF082522)
    val Border = Color(0xFF18242E)

    val TextPrimary = Color(0xFFE6EAF0)
    val TextSecondary = Color(0xFF8B98A5)
    val Disabled = Color(0xFF59636F)

    val Primary = Color(0xFF00D1B2)
    val PrimaryHover = Color(0xFF00B89C)
    val AccentSecondary = Color(0xFF2962FF)
    val AccentSecondaryHover = Color(0xFF1E4DCC)
    val Positive = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Negative = Color(0xFFEF4444)
    val Info = Color(0xFF38BDF8)

    // Pastels sombres et désaturés : visibles sur le fond noir sans rompre le thème.
    val FavoritePastels = listOf(
        Color(0xFF7DD3C7), // turquoise
        Color(0xFF93C5FD), // bleu
        Color(0xFFC4B5FD), // violet
        Color(0xFFF9A8D4), // rose
        Color(0xFFFCD89B), // ambre
    )

    // Alias conservés pour les composants existants.
    val DarkEmerald = Background
    val Anthracite = Surface
    val SurfaceSecondary = CardHover
    val EmeraldAccent = Primary
    val Emerald = PrimaryHover
    val EmeraldLight = TextPrimary
    val BackgroundLight = TextPrimary
    val White = TextPrimary
}
