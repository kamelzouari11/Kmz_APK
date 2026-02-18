package com.example.simpleradio.upnp

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.*

class SsdpDiscovery(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onDeviceFound(device: DiscoveredDevice)
        fun onDiscoveryFinished()
        fun onLog(message: String)
    }

    data class DiscoveredDevice(
            val friendlyName: String,
            val location: String,
            val remoteIp: String,
            val uuid: String,
            val avTransportControlUrl: String,
            val avTransportServiceType: String
    )

    private var discoveryJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val foundUuids = mutableSetOf<String>()

    init {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("upnp_discovery")
    }

    fun startDiscovery(scope: CoroutineScope) {
        if (discoveryJob?.isActive == true) return

        foundUuids.clear()
        discoveryJob =
                scope.launch(Dispatchers.IO) {
                    try {
                        multicastLock?.acquire()
                        listener.onLog("🔓 Multicast lock acquis")
                        listener.onLog("━━━━━━━━━━ Début recherche SSDP ━━━━━━━━━━")

                        val socket = DatagramSocket(null)
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress(0))
                        socket.soTimeout = 1000

                        // Search target for MediaRenderers
                        val st = "urn:schemas-upnp-org:device:MediaRenderer:1"
                        val query =
                                "M-SEARCH * HTTP/1.1\r\n" +
                                        "HOST: 239.255.255.250:1900\r\n" +
                                        "MAN: \"ssdp:discover\"\r\n" +
                                        "MX: 1\r\n" +
                                        "ST: $st\r\n\r\n"

                        val group = InetAddress.getByName("239.255.255.250")
                        val packet = DatagramPacket(query.toByteArray(), query.length, group, 1900)

                        repeat(3) { i ->
                            if (!isActive) return@repeat
                            listener.onLog("📤 Envoi M-SEARCH #${i + 1}/3")
                            socket.send(packet)

                            val startTime = System.currentTimeMillis()
                            // Écouter les réponses pendant 0.5 seconde (Ultra agressif)
                            while (isActive && System.currentTimeMillis() - startTime < 500) {
                                try {
                                    val buf = ByteArray(2048)
                                    val recv = DatagramPacket(buf, buf.size)
                                    socket.receive(recv)
                                    val response = String(recv.data, 0, recv.length)
                                    parseSsdpResponse(response)
                                } catch (e: java.net.SocketTimeoutException) {
                                    break // Plus de réponses pour cette salve
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        listener.onLog("❌ Erreur SSDP: ${e.message}")
                    } finally {
                        stopDiscovery()
                    }
                }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        if (multicastLock?.isHeld == true) {
            multicastLock?.release()
            listener.onLog("🔒 Multicast lock libéré")
        }
        listener.onDiscoveryFinished()
        listener.onLog("━━━━━━━━━━ Fin recherche SSDP ━━━━━━━━━━")
    }

    private suspend fun parseSsdpResponse(response: String) {
        val lines = response.split("\r\n")
        var location: String? = null
        var usn: String? = null

        for (line in lines) {
            if (line.startsWith("LOCATION:", true)) location = line.substring(9).trim()
            if (line.startsWith("USN:", true)) usn = line.substring(4).trim()
        }

        if (location != null && usn != null) {
            val uuid = usn.split("::").firstOrNull() ?: usn
            if (foundUuids.add(uuid)) {
                listener.onLog(
                        "📡 Réponse SSDP de ${location.substringAfter("//").substringBefore("/")}"
                )
                fetchDeviceDescription(location, uuid)
            }
        }
    }

    private suspend fun fetchDeviceDescription(url: String, uuid: String) {
        withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                val xml = conn.inputStream.bufferedReader().use { it.readText() }

                // Optimized XML parsing using regex (lighter than DOM for mobile)
                val friendlyName =
                        Regex("<friendlyName>(.*?)</friendlyName>").find(xml)?.groupValues?.get(1)
                                ?: "Unknown"

                // Extract AVTransport service info
                val avTransportXml =
                        Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL)
                                .findAll(xml)
                                .find { it.value.contains("AVTransport:1") }
                                ?.value
                                ?: return@withContext

                val serviceType =
                        Regex("<serviceType>(.*?)</serviceType>")
                                .find(avTransportXml)
                                ?.groupValues
                                ?.get(1)
                                ?: ""
                val controlUrlRelative =
                        Regex("<controlURL>(.*?)</controlURL>")
                                .find(avTransportXml)
                                ?.groupValues
                                ?.get(1)
                                ?: ""

                // Build full control URL
                val avTransportControlUrl =
                        if (controlUrlRelative.startsWith("http")) {
                            controlUrlRelative
                        } else {
                            val base =
                                    url.substringBefore("/", "http://") +
                                            "//" +
                                            url.substringAfter("//").substringBefore("/")
                            if (controlUrlRelative.startsWith("/")) {
                                base + controlUrlRelative
                            } else {
                                "$base/$controlUrlRelative"
                            }
                        }

                val remoteIp = url.substringAfter("//").substringBefore("/")

                val device =
                        DiscoveredDevice(
                                friendlyName = friendlyName,
                                location = url,
                                remoteIp = remoteIp,
                                uuid = uuid,
                                avTransportControlUrl = avTransportControlUrl,
                                avTransportServiceType = serviceType
                        )

                withContext(Dispatchers.Main) {
                    listener.onDeviceFound(device)
                    listener.onLog("✅ Appareil trouvé: $friendlyName")
                }
            } catch (e: Exception) {
                Log.e("SSDP", "Error fetching XML", e)
            }
        }
    }
}
