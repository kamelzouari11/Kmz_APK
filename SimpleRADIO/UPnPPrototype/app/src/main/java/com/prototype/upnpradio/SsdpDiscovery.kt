package com.prototype.upnpradio

import android.util.Log
import java.net.*
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*

/**
 * SSDP Discovery Client Discovers UPnP MediaRenderer devices on the local network.
 *
 * Protocol: SSDP over UDP Multicast Address: 239.255.255.250:1900 Search:
 * urn:schemas-upnp-org:device:MediaRenderer:1
 */
class SsdpDiscovery(private val listener: Listener) {

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val SEARCH_TTL = 2
        private const val SOCKET_TIMEOUT_MS = 6000
        private const val SEARCH_REPEAT = 3
        private const val SEARCH_DELAY_MS = 1500L

        // M-SEARCH message template (SSDP discovery)
        private val SEARCH_MESSAGE =
                ("M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $MULTICAST_ADDRESS:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: $SEARCH_TARGET\r\n" +
                        "\r\n")
    }

    interface Listener {
        fun onDeviceFound(device: DiscoveredDevice)
        fun onSearchStarted()
        fun onSearchFinished()
        fun onError(message: String)
        fun onLog(message: String)
    }

    data class DiscoveredDevice(
            val friendlyName: String,
            val location: String, // URL to device description XML
            val uuid: String,
            val remoteIp: String,
            val avTransportControlUrl: String, // Full URL: base + controlURL
            val avTransportServiceType: String
    )

    private var searchJob: Job? = null
    private val foundDevices = mutableSetOf<String>() // Track by UUID

    /** Start SSDP discovery. Sends M-SEARCH multicast and listens for responses. */
    fun startDiscovery(scope: CoroutineScope) {
        if (searchJob?.isActive == true) {
            listener.onLog("⚠️ Recherche déjà en cours")
            return
        }

        foundDevices.clear()
        searchJob =
                scope.launch(Dispatchers.IO) {
                    listener.onSearchStarted()
                    listener.onLog("━━━━━━━━━━ Début recherche SSDP ━━━━━━━━━━")

                    var socket: DatagramSocket? = null
                    try {
                        socket =
                                DatagramSocket().apply {
                                    soTimeout = SOCKET_TIMEOUT_MS
                                    broadcast = true
                                }

                        val searchBytes = SEARCH_MESSAGE.toByteArray(StandardCharsets.UTF_8)
                        val multicastAddr = InetAddress.getByName(MULTICAST_ADDRESS)
                        val searchPacket =
                                DatagramPacket(
                                        searchBytes,
                                        searchBytes.size,
                                        multicastAddr,
                                        SSDP_PORT
                                )

                        // Send M-SEARCH multiple times
                        for (i in 1..SEARCH_REPEAT) {
                            listener.onLog("📤 Envoi M-SEARCH #$i/$SEARCH_REPEAT")
                            listener.onLog("   → $MULTICAST_ADDRESS:$SSDP_PORT")
                            listener.onLog("   → ST: $SEARCH_TARGET")
                            socket.send(searchPacket)

                            // Listen for responses after each M-SEARCH
                            listenForResponses(socket)

                            if (i < SEARCH_REPEAT) {
                                delay(SEARCH_DELAY_MS)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Discovery error", e)
                        listener.onError("Erreur de découverte: ${e.message}")
                        listener.onLog("❌ Erreur: ${e.message}")
                    } finally {
                        socket?.close()
                        listener.onLog("━━━━━━━━━━ Fin recherche SSDP ━━━━━━━━━━")
                        listener.onLog("Appareils trouvés: ${foundDevices.size}")
                        listener.onSearchFinished()
                    }
                }
    }

    fun stopDiscovery() {
        searchJob?.cancel()
        searchJob = null
    }

    /** Listen for SSDP M-SEARCH responses on the socket. */
    private suspend fun listenForResponses(socket: DatagramSocket) {
        val buffer = ByteArray(2048)
        val startTime = System.currentTimeMillis()
        val listenDuration = 3000L // Listen for 3 seconds

        while (System.currentTimeMillis() - startTime < listenDuration) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val response = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                val senderIp = packet.address.hostAddress ?: "unknown"

                listener.onLog("📥 Réponse SSDP de $senderIp")

                // Parse the SSDP response
                parseSsdpResponse(response, senderIp)
            } catch (e: SocketTimeoutException) {
                // Normal timeout, continue
                break
            } catch (e: Exception) {
                listener.onLog("⚠️ Erreur parsing: ${e.message}")
            }
        }
    }

    /**
     * Parse an SSDP response and extract device information. Then fetch the device description XML
     * for full details.
     */
    private suspend fun parseSsdpResponse(response: String, senderIp: String) {
        val headers = mutableMapOf<String, String>()

        response.split("\r\n").forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().uppercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }

        val location = headers["LOCATION"] ?: return
        val usn = headers["USN"] ?: return

        // Extract UUID from USN (format: uuid:xxx::urn:schemas-upnp-org:...)
        val uuid = extractUuid(usn) ?: return

        // Skip if already found
        if (uuid in foundDevices) return
        foundDevices.add(uuid)

        listener.onLog("   📍 LOCATION: $location")
        listener.onLog("   🔑 UUID: $uuid")

        // Fetch device description XML
        try {
            listener.onLog("📥 Récupération description XML...")
            val device = fetchDeviceDescription(location, uuid, senderIp)
            if (device != null) {
                listener.onLog("✅ Appareil trouvé: ${device.friendlyName}")
                listener.onLog("   🎛️ AVTransport: ${device.avTransportControlUrl}")
                listener.onDeviceFound(device)
            }
        } catch (e: Exception) {
            listener.onLog("❌ Erreur description XML: ${e.message}")
        }
    }

    /**
     * Fetch and parse the UPnP device description XML. Extracts: friendlyName, services list,
     * control URLs
     */
    private fun fetchDeviceDescription(
            locationUrl: String,
            uuid: String,
            senderIp: String
    ): DiscoveredDevice? {
        val url = URL(locationUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            val xml = connection.inputStream.bufferedReader().readText()
            listener.onLog("   📄 XML reçu (${xml.length} chars)")

            // Parse the XML to extract device info
            val friendlyName = extractXmlTag(xml, "friendlyName") ?: "Appareil inconnu"
            val deviceType = extractXmlTag(xml, "deviceType") ?: ""

            listener.onLog("   📛 friendlyName: $friendlyName")
            listener.onLog("   📦 deviceType: $deviceType")

            // Check it's a MediaRenderer
            if (!deviceType.contains("MediaRenderer")) {
                listener.onLog("   ⏭️ Ignoré (pas un MediaRenderer)")
                return null
            }

            // Find AVTransport service
            val avTransportControlUrl = findServiceControlUrl(xml, "AVTransport")
            if (avTransportControlUrl == null) {
                listener.onLog("   ⏭️ Ignoré (pas de service AVTransport)")
                return null
            }

            // Find AVTransport service type
            val avTransportServiceType =
                    findServiceType(xml, "AVTransport")
                            ?: "urn:schemas-upnp-org:service:AVTransport:1"

            // Resolve the control URL relative to the base URL
            val baseUrl = "${url.protocol}://${url.host}:${url.port}"
            val fullControlUrl =
                    when {
                        avTransportControlUrl.startsWith("http") -> avTransportControlUrl
                        avTransportControlUrl.startsWith("/") -> "$baseUrl$avTransportControlUrl"
                        else -> "$baseUrl/$avTransportControlUrl"
                    }

            return DiscoveredDevice(
                    friendlyName = friendlyName,
                    location = locationUrl,
                    uuid = uuid,
                    remoteIp = senderIp,
                    avTransportControlUrl = fullControlUrl,
                    avTransportServiceType = avTransportServiceType
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Find the controlURL for a specific service by its serviceId keyword. */
    private fun findServiceControlUrl(xml: String, serviceKeyword: String): String? {
        // Find the <service> block that contains the serviceKeyword
        val serviceBlocks = xml.split("<service>")
        for (block in serviceBlocks) {
            if (block.contains(serviceKeyword)) {
                return extractXmlTag(block, "controlURL")
            }
        }
        return null
    }

    /** Find the serviceType for a specific service by its serviceId keyword. */
    private fun findServiceType(xml: String, serviceKeyword: String): String? {
        val serviceBlocks = xml.split("<service>")
        for (block in serviceBlocks) {
            if (block.contains(serviceKeyword)) {
                return extractXmlTag(block, "serviceType")
            }
        }
        return null
    }

    /** Simple XML tag content extractor (no full XML parser needed). */
    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }

    /** Extract UUID from USN header. Format: uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx::urn:... */
    private fun extractUuid(usn: String): String? {
        val match = Regex("^(uuid:[^:]+)").find(usn)
        return match?.groupValues?.get(1)
    }
}
