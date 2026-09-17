package com.kmz.myvolume

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var preferences: OverlayPreferences
    private var overlayPermissionGranted by mutableStateOf(false)
    private var refreshToken by mutableIntStateOf(0)
    private var enableAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = OverlayPreferences(this)
        overlayPermissionGranted = Settings.canDrawOverlays(this)
        enableEdgeToEdge()
        setContent {
            MyVolumeTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                MyVolumeScreen(
                    preferences = preferences,
                    overlayPermissionGranted = overlayPermissionGranted,
                    refreshToken = refreshToken,
                    onRequestOverlayPermission = ::requestOverlayPermission,
                    onOverlayEnabledChange = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(this)) {
                            requestOverlayPermission()
                        } else {
                            preferences.enabled = enabled
                            if (enabled) {
                                startOverlayService()
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                            } else {
                                stopService(Intent(this, VolumeOverlayService::class.java))
                            }
                            refreshToken++
                        }
                    },
                    onOpenAppSettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = Settings.canDrawOverlays(this)
        if (enableAfterPermission) {
            enableAfterPermission = false
            if (overlayPermissionGranted) {
                preferences.enabled = true
                startOverlayService()
            }
        } else if (overlayPermissionGranted && preferences.enabled) {
            startOverlayService()
        }
        if (!overlayPermissionGranted && preferences.enabled) preferences.enabled = false
        refreshToken++
    }

    private fun requestOverlayPermission() {
        enableAfterPermission = true
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun startOverlayService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, VolumeOverlayService::class.java),
        )
    }
}

@Composable
private fun MyVolumeScreen(
    preferences: OverlayPreferences,
    overlayPermissionGranted: Boolean,
    refreshToken: Int,
    onRequestOverlayPermission: () -> Unit,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember(refreshToken) { mutableStateOf(preferences.enabled) }
    val initialHsv = remember(refreshToken) { hsvOf(preferences.color) }
    var hue by remember(refreshToken) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(refreshToken) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(refreshToken) { mutableFloatStateOf(initialHsv[2]) }
    var size by remember(refreshToken) { mutableFloatStateOf(preferences.sizeDp) }
    var opacity by remember(refreshToken) { mutableFloatStateOf(preferences.opacity) }
    var snapToEdge by remember(refreshToken) { mutableStateOf(preferences.snapToEdge) }
    var startOnBoot by remember(refreshToken) { mutableStateOf(preferences.startOnBoot) }
    val previewColor = Color.hsv(hue, saturation, brightness)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07110F))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "MyVolume",
            color = Color(0xFF00D1B2),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Votre bouton de volume physique, directement sur l’écran.",
            color = Color(0xFFB7C8C4),
            style = MaterialTheme.typography.bodyLarge,
        )

        SettingsCard {
            SettingHeader(
                icon = Icons.Rounded.Layers,
                title = "Autorisation de superposition",
                subtitle = if (overlayPermissionGranted) {
                    "Autorisée : le bouton peut apparaître devant les applications."
                } else {
                    "Obligatoire pour afficher le bouton devant les autres écrans."
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (overlayPermissionGranted) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (overlayPermissionGranted) Color(0xFF22C55E) else Color(0xFFF59E0B),
                    )
                    Text(
                        if (overlayPermissionGranted) "Autorisation accordée" else "Autorisation requise",
                        color = Color.White,
                    )
                }
                if (!overlayPermissionGranted) {
                    Button(onClick = onRequestOverlayPermission) {
                        Text("Autoriser")
                    }
                }
            }
        }

        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Bouton flottant",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (enabled) "Actif et visible" else "Désactivé",
                        color = Color(0xFF9FB0AC),
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = overlayPermissionGranted,
                    onCheckedChange = { checked ->
                        enabled = checked
                        onOverlayEnabledChange(checked)
                    },
                )
            }
        }

        SettingsCard {
            SettingHeader(
                icon = Icons.Rounded.Palette,
                title = "Couleur",
                subtitle = "Choisissez la saturation et la luminosité dans le carré, puis une teinte dans le pavé.",
            )
            PreviewButton(
                color = previewColor,
                sizeDp = size,
                opacity = opacity,
            )
            ColorPickerSquare(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onColorPointChanged = { newSaturation, newBrightness ->
                    saturation = newSaturation
                    brightness = newBrightness
                    preferences.color = Color.hsv(
                        hue,
                        newSaturation,
                        newBrightness,
                    ).toArgb()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Teinte",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            HuePalette(
                hue = hue,
                onHueChanged = { newHue ->
                    hue = newHue
                    preferences.color = Color.hsv(
                        newHue,
                        saturation,
                        brightness,
                    ).toArgb()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsCard {
            SettingHeader(
                icon = Icons.Rounded.Straighten,
                title = "Taille : ${size.toInt()} dp",
                subtitle = "Assez grande pour être facile à toucher sans masquer l’écran.",
            )
            Slider(
                value = size,
                onValueChange = { newSize ->
                    size = newSize
                    preferences.sizeDp = newSize
                },
                valueRange = OverlayPreferences.MIN_SIZE_DP..OverlayPreferences.MAX_SIZE_DP,
            )
            Text(
                text = "Transparence : ${(opacity * 100).toInt()} %",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = opacity,
                onValueChange = { newOpacity ->
                    opacity = newOpacity
                    preferences.opacity = newOpacity
                },
                valueRange = OverlayPreferences.MIN_OPACITY..1f,
            )
        }

        SettingsCard {
            SettingSwitch(
                title = "Aimantation au bord",
                subtitle = "Après déplacement, le bouton rejoint automatiquement le bord le plus proche.",
                checked = snapToEdge,
                onCheckedChange = {
                    snapToEdge = it
                    preferences.snapToEdge = it
                },
            )
            Spacer(Modifier.height(4.dp))
            SettingSwitch(
                title = "Redémarrer avec le téléphone",
                subtitle = "Restaure automatiquement le bouton après l’allumage.",
                checked = startOnBoot,
                onCheckedChange = {
                    startOnBoot = it
                    preferences.startOnBoot = it
                },
            )
        }

        SettingsCard {
            SettingHeader(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = "Test du volume natif",
                subtitle = "Ouvre exactement le panneau de volume Samsung utilisé par le bouton flottant.",
            )
            OutlinedButton(
                onClick = {
                    context.getSystemService(AudioManager::class.java).adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_SAME,
                        AudioManager.FLAG_SHOW_UI,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                Text("Afficher le volume", modifier = Modifier.padding(start = 8.dp))
            }
        }

        SettingsCard {
            SettingHeader(
                icon = Icons.Rounded.BatterySaver,
                title = "Fiabilité Samsung",
                subtitle = "Si le bouton disparaît, réglez la batterie de MyVolume sur « Sans restriction ».",
            )
            OutlinedButton(
                onClick = onOpenAppSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                Text("Ouvrir les informations de l’application", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Touchez le bouton pour afficher le volume. Faites-le glisser pour le déplacer. " +
                "Masquez-le temporairement et réaffichez-le depuis la notification MyVolume. " +
                "MyVolume ne collecte aucune donnée et n’utilise pas Internet.",
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            color = Color(0xFF91A39E),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1C19)),
        border = BorderStroke(1.dp, Color(0xFF1E3934)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF00D1B2))
        Column {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(subtitle, color = Color(0xFF9FB0AC), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Color(0xFF9FB0AC), style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreviewButton(
    color: Color,
    sizeDp: Float,
    opacity: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(sizeDp.dp)
                .alpha(opacity),
            color = color,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            shadowElevation = 10.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size((sizeDp * 0.48f).dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ColorPickerSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onColorPointChanged: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun updateFromPosition(x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        onColorPointChanged(
            (x / width).coerceIn(0f, 1f),
            (1f - y / height).coerceIn(0f, 1f),
        )
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(hue) {
                detectTapGestures { position ->
                    updateFromPosition(
                        position.x,
                        position.y,
                        size.width.toFloat(),
                        size.height.toFloat(),
                    )
                }
            }
            .pointerInput(hue) {
                detectDragGestures(
                    onDragStart = { position ->
                        updateFromPosition(
                            position.x,
                            position.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromPosition(
                            change.position.x,
                            change.position.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    },
                )
            },
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, Color.hsv(hue, 1f, 1f)),
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
            ),
        )
        val markerCenter = androidx.compose.ui.geometry.Offset(
            x = saturation * size.width,
            y = (1f - brightness) * size.height,
        )
        drawCircle(Color.Black.copy(alpha = 0.75f), radius = 12.dp.toPx(), center = markerCenter)
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = markerCenter,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun HuePalette(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hues = listOf(
        0f,
        30f,
        60f,
        90f,
        120f,
        150f,
        180f,
        210f,
        240f,
        270f,
        300f,
        330f,
        345f,
        15f,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        hues.chunked(7).forEach { rowHues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowHues.forEach { tileHue ->
                    val isSelected = kotlin.math.abs(hue - tileHue) < 1f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.45f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.hsv(tileHue, 0.9f, 1f))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onHueChanged(tileHue) },
                    )
                }
            }
        }
    }
}

private fun hsvOf(color: Int): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color, hsv)
    return hsv
}

@Composable
private fun MyVolumeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00D1B2),
            onPrimary = Color(0xFF001F1A),
            background = Color(0xFF07110F),
            surface = Color(0xFF0D1C19),
            onSurface = Color.White,
        ),
        content = content,
    )
}
