package com.example.myiptv.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myiptv.data.translateEpgToFrench
import com.example.myiptv.ui.theme.MyIptvPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun EpgTranslatedText(
    title: String,
    description: String,
    country: String,
    buttonModifier: Modifier = Modifier,
) {
    key(title, description, country) {
        val scope = rememberCoroutineScope()
        var translated by remember { mutableStateOf<Pair<String, String>?>(null) }
        var showFrench by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf("Traduction en cours…") }
        var error by remember { mutableStateOf<String?>(null) }
        val displayed = if (showFrench) translated else null
        val buttonText = when {
            loading -> progress
            showFrench -> "Afficher l’original"
            else -> "Translate to French"
        }
        val onTranslate: () -> Unit = {
            if (!loading) {
                if (translated != null) {
                    showFrench = !showFrench
                } else {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            translated = translateEpgToFrench(title, description, country) {
                                progress = it
                            }
                            showFrench = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: IllegalArgumentException) {
                            error = "Langue non reconnue ou non prise en charge."
                        } catch (_: Exception) {
                            error = "Traduction indisponible. Vérifiez la connexion et réessayez."
                        } finally {
                            loading = false
                        }
                    }
                }
            }
        }
        if (isTvLandscape()) {
            // Use the same remote-accessible control as the TV menus. Keep focus while loading.
            MenuButton(text = buttonText, modifier = buttonModifier, onClick = onTranslate, maxLines = Int.MAX_VALUE)
        } else {
            TextButton(
                enabled = !loading,
                modifier = buttonModifier,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                onClick = onTranslate,
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelSmall, color = MyIptvPalette.Primary)
            }
        }
        if (loading && progress == "Préparation des langues…") {
            Text(
                "Les langues manquantes sont téléchargées une seule fois, puis conservées sur l’appareil.",
                color = MyIptvPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            displayed?.first ?: title,
            color = MyIptvPalette.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (description.isNotBlank()) {
            Text(displayed?.second ?: description, color = MyIptvPalette.TextSecondary)
        }
        error?.let { Text(it, color = MyIptvPalette.Negative, style = MaterialTheme.typography.bodySmall) }
    }
}
