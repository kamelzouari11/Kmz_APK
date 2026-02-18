package com.example.simpleradio.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.simpleradio.ui.MainViewModel
import com.example.simpleradio.upnp.SsdpDiscovery
import com.example.simpleradio.upnp.UpnpController
import kotlinx.coroutines.delay

@Composable
fun rememberUpnpManagement(context: Context, viewModel: MainViewModel): UpnpManager {
    val scope = rememberCoroutineScope()

    val discoveredDevices = remember { mutableStateListOf<SsdpDiscovery.DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<SsdpDiscovery.DiscoveredDevice?>(null) }
    var upnpPlaybackState by remember { mutableStateOf(UpnpController.PlaybackState.IDLE) }
    var connectionTrigger by remember { mutableIntStateOf(0) }

    val upnpController = remember {
        UpnpController(
                context,
                object : UpnpController.Listener {
                    override fun onPlaybackStarted() {
                        upnpPlaybackState = UpnpController.PlaybackState.PLAYING
                    }
                    override fun onPlaybackStopped() {
                        upnpPlaybackState = UpnpController.PlaybackState.STOPPED
                    }
                    override fun onPlaybackError(message: String) {
                        upnpPlaybackState = UpnpController.PlaybackState.ERROR
                    }
                    override fun onTransportState(state: String) {
                        // Optionnel: log ou mise à jour UI
                    }
                    override fun onLog(message: String) {
                        // Optionnel: log système
                    }
                }
        )
    }

    val ssdpDiscovery = remember {
        SsdpDiscovery(
                context,
                object : SsdpDiscovery.Listener {
                    override fun onDeviceFound(device: SsdpDiscovery.DiscoveredDevice) {
                        if (discoveredDevices.none { it.uuid == device.uuid }) {
                            discoveredDevices.add(device)
                        }
                    }
                    override fun onDiscoveryFinished() {
                        // Fin de recherche
                    }
                    override fun onLog(message: String) {}
                }
        )
    }

    // --- LOGIC: Auto-sync playback when station OR device changes ---
    LaunchedEffect(viewModel.playingRadio, selectedDevice, connectionTrigger) {
        val device = selectedDevice
        val radio = viewModel.playingRadio
        if (device != null && radio != null) {
            // Délai minimal pour garantir que l'UI est prête
            delay(150)

            val streamUrl =
                    (radio.url_resolved?.ifEmpty { radio.url } ?: radio.url).replace(
                            "https://",
                            "http://",
                            ignoreCase = true
                    )

            // On lance la lecture 100% DIRECTE (URL seule, zéro métadonnée pour la Sangean)
            upnpController.play(device = device, scope = scope, streamUrl = streamUrl)
        }
    }

    // Libération des ressources UPnP
    DisposableEffect(Unit) { onDispose { upnpController.release() } }

    return remember(discoveredDevices.size, selectedDevice, upnpPlaybackState) {
        UpnpManager(
                devices = discoveredDevices,
                selectedDevice = selectedDevice,
                playbackState = upnpPlaybackState,
                startDiscovery = {
                    discoveredDevices.clear()
                    ssdpDiscovery.startDiscovery(scope)
                },
                stopDiscovery = { ssdpDiscovery.stopDiscovery() },
                connectToDevice = { device ->
                    selectedDevice = device
                    connectionTrigger++ // Force l'envoi de l'URL immédiat (Handoff)
                    // La lecture sera déclenchée par le LaunchedEffect ci-dessus
                },
                disconnect = {
                    selectedDevice?.let { upnpController.stop(it, scope) }
                    selectedDevice = null
                    upnpPlaybackState = UpnpController.PlaybackState.IDLE
                }
        )
    }
}

data class UpnpManager(
        val devices: List<SsdpDiscovery.DiscoveredDevice>,
        val selectedDevice: SsdpDiscovery.DiscoveredDevice?,
        val playbackState: UpnpController.PlaybackState,
        val startDiscovery: () -> Unit,
        val stopDiscovery: () -> Unit,
        val connectToDevice: (SsdpDiscovery.DiscoveredDevice) -> Unit,
        val disconnect: () -> Unit
)
