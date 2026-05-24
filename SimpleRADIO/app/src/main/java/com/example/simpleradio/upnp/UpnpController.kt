package com.example.simpleradio.upnp

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*

/** UPnP Controller Sends SOAP commands to a UPnP MediaRenderer device to control playback. */
class UpnpController(private val context: Context, private val listener: Listener) {

    companion object {
        private const val TAG = "UpnpController"
        private const val SOAP_TIMEOUT_MS = 8000
    }

    interface Listener {
        fun onPlaybackStarted()
        fun onPlaybackStopped()
        fun onPlaybackError(message: String)
        fun onTransportState(state: String)
        fun onLog(message: String)
    }

    enum class PlaybackState {
        IDLE,
        LOADING,
        PLAYING,
        STOPPED,
        ERROR
    }

    var currentState = PlaybackState.IDLE
        private set

    private var watchdogJob: Job? = null

    fun play(device: SsdpDiscovery.DiscoveredDevice, scope: CoroutineScope, streamUrl: String) {
        scope.launch(Dispatchers.IO) {
            try {
                currentState = PlaybackState.LOADING
                listener.onLog("━━━━━━━━━━ LECTURE DIRECTE ━━━━━━━━━━")
                listener.onLog("🎯 Cible: ${device.friendlyName}")

                // ── Unique Step: Set & Play (100% Direct, No Metadata) ──
                val setUriSuccess = sendSetAVTransportURINoMetadata(device, streamUrl)
                if (!setUriSuccess) {
                    currentState = PlaybackState.ERROR
                    listener.onPlaybackError("Échec connexion")
                    return@launch
                }

                delay(150)

                val playSuccess = sendPlay(device)
                if (!playSuccess) {
                    currentState = PlaybackState.ERROR
                    listener.onPlaybackError("Échec Play")
                    return@launch
                }

                currentState = PlaybackState.PLAYING
                listener.onPlaybackStarted()
                startWatchdog(device, scope)
            } catch (e: Exception) {
                currentState = PlaybackState.ERROR
                listener.onPlaybackError("Erreur: ${e.message}")
            }
        }
    }

    fun stop(device: SsdpDiscovery.DiscoveredDevice, scope: CoroutineScope) {
        stopWatchdog()
        scope.launch(Dispatchers.IO) {
            try {
                listener.onLog("📤 Envoi commande Stop...")
                val success = sendStop(device)
                if (success) {
                    currentState = PlaybackState.STOPPED
                    listener.onLog("✅ Stop OK")
                    listener.onPlaybackStopped()
                } else {
                    listener.onPlaybackError("Échec Stop")
                }
            } catch (e: Exception) {
                listener.onPlaybackError("Erreur Stop: ${e.message}")
            }
        }
    }

    private fun sendSetAVTransportURINoMetadata(
            device: SsdpDiscovery.DiscoveredDevice,
            streamUrl: String
    ): Boolean {
        val soapBody =
                """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="${device.avTransportServiceType}">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${escapeXml(streamUrl)}</CurrentURI>
                        <CurrentURIMetaData></CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "SetAVTransportURI",
                soapBody
        )
    }

    private fun sendPlay(device: SsdpDiscovery.DiscoveredDevice): Boolean {
        val soapBody =
                """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Play xmlns:u="${device.avTransportServiceType}">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "Play",
                soapBody
        )
    }

    private fun sendStop(device: SsdpDiscovery.DiscoveredDevice): Boolean {
        val soapBody =
                """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Stop xmlns:u="${device.avTransportServiceType}">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "Stop",
                soapBody
        )
    }

    private fun sendGetTransportInfo(device: SsdpDiscovery.DiscoveredDevice): String? {
        val soapBody =
                """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:GetTransportInfo xmlns:u="${device.avTransportServiceType}">
                        <InstanceID>0</InstanceID>
                    </u:GetTransportInfo>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return sendSoapRequestWithResponse(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "GetTransportInfo",
                soapBody,
                "CurrentTransportState"
        )
    }

    private fun escapeXml(xml: String): String {
        return xml.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }

    private fun sendSoapRequest(
            controlUrl: String,
            serviceType: String,
            action: String,
            soapBody: String
    ): Boolean {
        try {
            val url = URL(controlUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 3000 // Timeout réduit
                readTimeout = 3000
                requestMethod = "POST"
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
                setRequestProperty("Connection", "close")
                doOutput = true
            }
            connection.outputStream.use { it.write(soapBody.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()
            return code in 200..299
        } catch (e: Exception) {
            return false
        }
    }

    private fun sendSoapRequestWithResponse(
            controlUrl: String,
            serviceType: String,
            action: String,
            soapBody: String,
            responseTag: String
    ): String? {
        try {
            val url = URL(controlUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = SOAP_TIMEOUT_MS
                readTimeout = SOAP_TIMEOUT_MS
                requestMethod = "POST"
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
                setRequestProperty("Connection", "close")
                doOutput = true
            }
            connection.outputStream.use { it.write(soapBody.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                return Regex("<$responseTag>(.*?)</$responseTag>", RegexOption.DOT_MATCHES_ALL)
                        .find(response)
                        ?.groupValues
                        ?.get(1)
            }
            connection.disconnect()
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun startWatchdog(device: SsdpDiscovery.DiscoveredDevice, scope: CoroutineScope) {
        stopWatchdog()
        watchdogJob =
                scope.launch(Dispatchers.IO) {
                    var failureCount = 0
                    while (isActive && currentState == PlaybackState.PLAYING) {
                        delay(5000)
                        val state = sendGetTransportInfo(device)
                        if (state == "PLAYING" || state == "TRANSITIONING") failureCount = 0
                        else failureCount++
                        if (failureCount >= 3) {
                            currentState = PlaybackState.ERROR
                            listener.onPlaybackError("Transport non-PLAYING")
                            break
                        }
                    }
                }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
    fun release() {
        stopWatchdog()
        currentState = PlaybackState.IDLE
    }
}
