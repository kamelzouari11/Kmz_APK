package com.prototype.upnpradio

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*

/**
 * UPnP Controller Sends SOAP commands to a UPnP MediaRenderer device to control playback.
 *
 * Supported actions:
 * - SetAVTransportURI : Set the media URL to play
 * - Play : Start playback
 * - Stop : Stop playback
 * - GetTransportInfo : Query playback state (watchdog)
 */
class UpnpController(private val context: Context, private val listener: Listener) {

    companion object {
        private const val TAG = "UpnpController"
        private const val SOAP_TIMEOUT_MS = 8000

        // Radio Paradise: MP3 320 kbps
        const val RADIO_URL = "http://stream.radioparadise.com/mp3-320"
        const val RADIO_NAME = "Radio Paradise (MP3 320k)"
        const val CONTENT_TYPE = "audio/mpeg"
        const val RADIO_LOGO_URL = "" // No logo for this test
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
    private var currentProxyUrl: String? = null
    private var currentLogoUrl: String? = null

    /**
     * Full play sequence:
     * 1. SetAVTransportURI — sends the radio stream URL + DIDL-Lite metadata
     * 2. Play — starts playback with Speed=1
     * 3. Starts watchdog to monitor transport state
     */
    fun play(device: SsdpDiscovery.DiscoveredDevice, scope: CoroutineScope, proxy: StreamProxy) {
        scope.launch(Dispatchers.IO) {
            try {
                currentState = PlaybackState.LOADING
                listener.onLog("━━━━━━━━━━ Séquence de lecture ━━━━━━━━━━")
                listener.onLog("🎯 Cible: ${device.friendlyName}")
                listener.onLog("🔗 Control URL: ${device.avTransportControlUrl}")
                listener.onLog("🔗 Service Type: ${device.avTransportServiceType}")
                listener.onLog("")

                // ── Generate proxy URL (HTTPS → HTTP) ──
                currentProxyUrl = proxy.getProxyUrl(RADIO_URL)
                listener.onLog("🌐 URL originale (HTTPS): $RADIO_URL")
                listener.onLog("🌐 URL proxy (HTTP):      $currentProxyUrl")

                // ── Download and set logo ──
                if (RADIO_LOGO_URL.isNotEmpty()) {
                    proxy.setLogoFromUrl(RADIO_LOGO_URL)
                }
                // Fallback to drawable if URL download failed
                if (proxy.getLogoUrl() == null) {
                    listener.onLog("🖼️ URL logo échouée, utilisation du drawable par défaut")
                    // Use resources.getIdentifier to avoid R class issues in different environments
                    val resId =
                            context.resources.getIdentifier(
                                    "ic_radio_logo",
                                    "drawable",
                                    context.packageName
                            )
                    if (resId != 0) {
                        proxy.setLogoFromResource(resId)
                    }
                }
                currentLogoUrl = proxy.getLogoUrl()
                if (currentLogoUrl != null) {
                    listener.onLog("🖼️ Logo URL: $currentLogoUrl")
                } else {
                    listener.onLog("⚠️ Aucun logo disponible")
                }
                listener.onLog("")

                // ── Step 1: SetAVTransportURI ──
                listener.onLog("📤 Étape 1/2: SetAVTransportURI")
                val setUriSuccess = sendSetAVTransportURI(device)
                if (!setUriSuccess) {
                    // Retry with empty metadata
                    listener.onLog("⚠️ Échec avec metadata, tentative SANS metadata...")
                    val retrySuccess = sendSetAVTransportURINoMetadata(device)
                    if (!retrySuccess) {
                        currentState = PlaybackState.ERROR
                        listener.onPlaybackError("Échec SetAVTransportURI")
                        return@launch
                    }
                }
                listener.onLog("✅ SetAVTransportURI OK")
                listener.onLog("")

                // Small delay between commands
                delay(800)

                // ── Step 2: Play ──
                listener.onLog("📤 Étape 2/2: Play")
                val playSuccess = sendPlay(device)
                if (!playSuccess) {
                    currentState = PlaybackState.ERROR
                    listener.onPlaybackError("Échec Play")
                    return@launch
                }

                currentState = PlaybackState.PLAYING
                listener.onLog("✅ Play OK")
                listener.onLog("🔊 LECTURE EN COURS !")
                listener.onPlaybackStarted()

                // Start watchdog
                startWatchdog(device, scope)
            } catch (e: Exception) {
                Log.e(TAG, "Play sequence error", e)
                currentState = PlaybackState.ERROR
                listener.onPlaybackError("Erreur: ${e.message}")
                listener.onLog("❌ Exception: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** Stop playback by sending the Stop SOAP action. */
    fun stop(device: SsdpDiscovery.DiscoveredDevice, scope: CoroutineScope) {
        stopWatchdog()
        scope.launch(Dispatchers.IO) {
            try {
                listener.onLog("")
                listener.onLog("📤 Envoi commande Stop...")
                val success = sendStop(device)
                if (success) {
                    currentState = PlaybackState.STOPPED
                    listener.onLog("✅ Stop OK")
                    listener.onPlaybackStopped()
                } else {
                    listener.onLog("❌ Stop a échoué")
                    listener.onPlaybackError("Échec Stop")
                }
            } catch (e: Exception) {
                listener.onLog("❌ Erreur Stop: ${e.message}")
                listener.onPlaybackError("Erreur Stop: ${e.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════
    // SOAP Actions
    // ══════════════════════════════════════════════════

    /** SOAP Action: SetAVTransportURI with DIDL-Lite metadata. Uses proxy URL. */
    private fun sendSetAVTransportURI(device: SsdpDiscovery.DiscoveredDevice): Boolean {
        val streamUrl = currentProxyUrl ?: RADIO_URL
        val metadata = buildDidlLiteMetadata(streamUrl)
        val escapedMetadata = escapeXml(metadata)

        listener.onLog("   InstanceID: 0")
        listener.onLog("   CurrentURI: $streamUrl")
        listener.onLog("   Content-Type: $CONTENT_TYPE")
        listener.onLog("   Metadata: DIDL-Lite (${metadata.length} chars)")

        val soapBody =
                buildSoapEnvelope(
                        device.avTransportServiceType,
                        "SetAVTransportURI",
                        mapOf(
                                "InstanceID" to "0",
                                "CurrentURI" to streamUrl,
                                "CurrentURIMetaData" to escapedMetadata
                        )
                )

        listener.onLog("   SOAP body size: ${soapBody.length} chars")
        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "SetAVTransportURI",
                soapBody
        )
    }

    /** SOAP Action: SetAVTransportURI WITHOUT metadata (fallback). Uses proxy URL. */
    private fun sendSetAVTransportURINoMetadata(device: SsdpDiscovery.DiscoveredDevice): Boolean {
        val streamUrl = currentProxyUrl ?: RADIO_URL
        listener.onLog("   (sans metadata, URL: $streamUrl)")

        val soapBody =
                buildSoapEnvelope(
                        device.avTransportServiceType,
                        "SetAVTransportURI",
                        mapOf(
                                "InstanceID" to "0",
                                "CurrentURI" to streamUrl,
                                "CurrentURIMetaData" to ""
                        )
                )

        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "SetAVTransportURI",
                soapBody
        )
    }

    /** SOAP Action: Play */
    private fun sendPlay(device: SsdpDiscovery.DiscoveredDevice): Boolean {
        listener.onLog("   InstanceID: 0, Speed: 1")

        val soapBody =
                buildSoapEnvelope(
                        device.avTransportServiceType,
                        "Play",
                        mapOf("InstanceID" to "0", "Speed" to "1")
                )

        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "Play",
                soapBody
        )
    }

    /** SOAP Action: Stop */
    private fun sendStop(device: SsdpDiscovery.DiscoveredDevice): Boolean {
        val soapBody =
                buildSoapEnvelope(device.avTransportServiceType, "Stop", mapOf("InstanceID" to "0"))

        return sendSoapRequest(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "Stop",
                soapBody
        )
    }

    /** SOAP Action: GetTransportInfo */
    private fun sendGetTransportInfo(device: SsdpDiscovery.DiscoveredDevice): String? {
        val soapBody =
                buildSoapEnvelope(
                        device.avTransportServiceType,
                        "GetTransportInfo",
                        mapOf("InstanceID" to "0")
                )

        return sendSoapRequestWithResponse(
                device.avTransportControlUrl,
                device.avTransportServiceType,
                "GetTransportInfo",
                soapBody,
                "CurrentTransportState"
        )
    }

    // ══════════════════════════════════════════════════
    // SOAP / HTTP helpers
    // ══════════════════════════════════════════════════

    /**
     * Build a proper SOAP envelope with arguments. This matches the exact format expected by UPnP
     * devices.
     */
    private fun buildSoapEnvelope(
            serviceType: String,
            action: String,
            arguments: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.append(
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
        )
        sb.append("<s:Body>")
        sb.append("<u:$action xmlns:u=\"$serviceType\">")
        for ((key, value) in arguments) {
            sb.append("<$key>$value</$key>")
        }
        sb.append("</u:$action>")
        sb.append("</s:Body>")
        sb.append("</s:Envelope>")
        return sb.toString()
    }

    /** Build DIDL-Lite metadata for the radio stream. Uses the provided URL (proxy or direct). */
    private fun buildDidlLiteMetadata(streamUrl: String): String {
        val logoTag =
                if (currentLogoUrl != null) {
                    "<upnp:albumArtURI>$currentLogoUrl</upnp:albumArtURI>"
                } else {
                    ""
                }
        // Build protocolInfo simply to match RadioUpnp style
        val protocolInfo = "http-get:*:$CONTENT_TYPE:*"

        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"0\" parentID=\"0\" restricted=\"1\">" +
                "<upnp:class>object.item.audioItem.audioBroadcast</upnp:class>" +
                "<dc:title>${escapeXml(RADIO_NAME)}</dc:title>" +
                "<upnp:artist>UPnP Prototype</upnp:artist>" +
                "<upnp:album>Live Streaming</upnp:album>" +
                logoTag +
                "<res duration=\"0:00:00\" protocolInfo=\"$protocolInfo\">$streamUrl</res>" +
                "</item>" +
                "</DIDL-Lite>"
    }

    /** Escape XML special characters for embedding in SOAP. */
    private fun escapeXml(xml: String): String {
        return xml.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }

    /**
     * Send a SOAP HTTP POST request and check for success (2xx response). Includes full debug
     * logging of both request and response.
     */
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
                connectTimeout = SOAP_TIMEOUT_MS
                readTimeout = SOAP_TIMEOUT_MS
                requestMethod = "POST"
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPAction", "\"${serviceType}#${action}\"")
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "UPnP Prototype/1.0")
                doOutput = true
                doInput = true
            }

            listener.onLog("   → POST $controlUrl")
            listener.onLog("   → SOAPAction: \"${serviceType}#${action}\"")
            listener.onLog("   → Content-Type: text/xml; charset=\"utf-8\"")

            // Write SOAP body
            val outputStream = connection.outputStream
            val bodyBytes = soapBody.toByteArray(Charsets.UTF_8)
            outputStream.write(bodyBytes)
            outputStream.flush()
            outputStream.close()

            listener.onLog("   → Body envoyé (${bodyBytes.size} bytes)")

            val responseCode = connection.responseCode
            val responseMsg = connection.responseMessage
            listener.onLog("   ← HTTP $responseCode $responseMsg")

            // Log response headers
            connection.headerFields?.forEach { (key, values) ->
                if (key != null) {
                    listener.onLog("   ← $key: ${values.joinToString(", ")}")
                }
            }

            if (responseCode in 200..299) {
                try {
                    val response = connection.inputStream.bufferedReader().readText()
                    listener.onLog("   ← Réponse OK (${response.length} chars)")
                    if (response.length < 2000) {
                        listener.onLog("   ← $response")
                    }
                } catch (_: Exception) {
                    listener.onLog("   ← (pas de body)")
                }
                connection.disconnect()
                return true
            } else {
                // Read error response for debug
                try {
                    val error = connection.errorStream?.bufferedReader()?.readText() ?: "(vide)"
                    listener.onLog("   ← ERREUR body:")
                    listener.onLog("   ← $error")
                    parseSoapFault(error)
                } catch (_: Exception) {
                    listener.onLog("   ← (impossible de lire l'erreur)")
                }
                connection.disconnect()
                return false
            }
        } catch (e: Exception) {
            listener.onLog("   ← Exception ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "SOAP request error for $action", e)
            return false
        }
    }

    /** Send a SOAP request and extract a specific response value. */
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
                setRequestProperty("SOAPAction", "\"${serviceType}#${action}\"")
                setRequestProperty("Connection", "close")
                doOutput = true
                doInput = true
            }

            val outputStream = connection.outputStream
            outputStream.write(soapBody.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                val regex = Regex("<$responseTag>(.*?)</$responseTag>", RegexOption.DOT_MATCHES_ALL)
                return regex.find(response)?.groupValues?.get(1)
            }

            connection.disconnect()
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /** Parse SOAP fault details from error response. */
    private fun parseSoapFault(xml: String) {
        try {
            val faultCode = Regex("<faultcode>(.*?)</faultcode>").find(xml)?.groupValues?.get(1)
            val faultString =
                    Regex("<faultstring>(.*?)</faultstring>").find(xml)?.groupValues?.get(1)
            val errorCode = Regex("<errorCode>(.*?)</errorCode>").find(xml)?.groupValues?.get(1)
            val errorDesc =
                    Regex("<errorDescription>(.*?)</errorDescription>")
                            .find(xml)
                            ?.groupValues
                            ?.get(1)

            if (faultCode != null) listener.onLog("   ⚡ FAULT: $faultCode / $faultString")
            if (errorCode != null) listener.onLog("   ⚡ UPnP Error $errorCode: $errorDesc")
        } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════════════
    // Watchdog
    // ══════════════════════════════════════════════════

    private fun startWatchdog(device: SsdpDiscovery.DiscoveredDevice, scope: CoroutineScope) {
        stopWatchdog()
        watchdogJob =
                scope.launch(Dispatchers.IO) {
                    listener.onLog("")
                    listener.onLog("🐕 Watchdog démarré (toutes les 5s)")
                    var failureCount = 0

                    while (isActive && currentState == PlaybackState.PLAYING) {
                        delay(5000)
                        if (!isActive) break

                        val state = sendGetTransportInfo(device)
                        if (state != null) {
                            listener.onTransportState(state)
                            if (state == "PLAYING" || state == "TRANSITIONING") {
                                failureCount = 0
                                listener.onLog("🐕 État: $state ✓")
                            } else {
                                failureCount++
                                listener.onLog("🐕 État: $state ⚠️ ($failureCount/3)")
                            }
                        } else {
                            failureCount++
                            listener.onLog("🐕 Pas de réponse ⚠️ ($failureCount/3)")
                        }

                        if (failureCount >= 3) {
                            listener.onLog("🐕 Watchdog: trop d'échecs → arrêt")
                            currentState = PlaybackState.ERROR
                            listener.onPlaybackError("Transport non-PLAYING")
                            break
                        }
                    }
                    listener.onLog("🐕 Watchdog arrêté")
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
