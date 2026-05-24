package com.prototype.upnpradio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.*

class StreamProxy(private val context: Context) {

    companion object {
        private const val TAG = "StreamProxy"
        private const val LOGO_SIZE = 300
        private const val BUFFER_SIZE = 8192 // 8KB for faster start/lower latency
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val proxyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var port = 0
    private var cachedIp: String? = null

    private var wifiLock: WifiManager.WifiLock? = null
    private var logoBytes: ByteArray? = null

    var onLog: ((String) -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        // Aquire WiFi Lock to prevent disconnection when screen is off
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "StreamProxyLock")
        wifiLock?.acquire()

        proxyScope.launch {
            try {
                serverSocket = ServerSocket(0)
                port = serverSocket!!.localPort
                onLog?.invoke("🌐 Proxy HTTP optimisé démarré sur le port $port")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                if (isRunning) onLog?.invoke("❌ Erreur Proxy: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        wifiLock?.let { if (it.isHeld) it.release() }
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        proxyScope.cancel()
    }

    fun getProxyUrl(remoteUrl: String): String {
        val localIp = getCachedIpAddress()
        val encodedUrl = java.net.URLEncoder.encode(remoteUrl, "UTF-8")
        // Always use .mp3 for max compatibility since MP3 320k is our target
        return "http://$localIp:$port/radio.mp3?url=$encodedUrl"
    }

    private fun getCachedIpAddress(): String {
        cachedIp?.let {
            return it
        }
        val ip = getLocalIpAddress()
        cachedIp = ip
        return ip
    }

    fun getLogoUrl(): String? {
        if (logoBytes == null) return null
        return "http://${getCachedIpAddress()}:$port/logo.jpg"
    }

    fun setLogoFromUrl(imageUrl: String) {
        proxyScope.launch {
            try {
                val conn = URL(imageUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.inputStream.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) setLogo(bitmap)
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun setLogo(bitmap: Bitmap) {
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, LOGO_SIZE, LOGO_SIZE, true)
            ByteArrayOutputStream().use { baos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos) // 85% quality is enough
                logoBytes = baos.toByteArray()
            }
        } catch (_: Exception) {}
    }

    fun setLogoFromResource(resId: Int) {
        try {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return
            val bitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            setLogo(bitmap)
        } catch (_: Exception) {}
    }

    private suspend fun handleClient(clientSocket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                clientSocket.use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val output = socket.getOutputStream()
                    val requestLine = input.readLine() ?: return@withContext

                    val parts = requestLine.split(" ")
                    if (parts.size < 2) return@withContext
                    val requestPath = parts[1]
                    val method = parts[0].uppercase()

                    if (requestPath.contains("logo")) {
                        serveLogo(output)
                    } else {
                        val urlParam = extractQueryParam(requestPath, "url")
                        if (urlParam != null) {
                            val targetUrl = java.net.URLDecoder.decode(urlParam, "UTF-8")
                            proxyStream(targetUrl, method, output)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun proxyStream(targetUrl: String, method: String, clientOutput: OutputStream) {
        var connection: HttpURLConnection? = null
        var totalSent = 0L
        try {
            connection = URL(targetUrl).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = method // Use same method as client (HEAD for HEAD, GET for GET)
                connectTimeout = 8000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Icy-Metadata", "0")
            }

            val responseCode = connection.responseCode
            onLog?.invoke("🌐 Proxy $method -> Source: $responseCode")

            val headers =
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: audio/mpeg\r\n" +
                            "Connection: close\r\n" +
                            "Accept-Ranges: none\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "\r\n"

            clientOutput.write(headers.toByteArray())
            clientOutput.flush()

            if (method == "HEAD") {
                onLog?.invoke("🌐 Proxy: headers envoyés (0 bytes de données)")
                return
            }

            connection.inputStream.use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (isRunning) {
                    read = inputStream.read(buffer)
                    if (read == -1) break
                    clientOutput.write(buffer, 0, read)
                    totalSent += read
                }
            }
            onLog?.invoke("🌐 Proxy: flux terminé (${totalSent / 1024} Ko envoyés)")
        } catch (_: Exception) {} finally {
            connection?.disconnect()
        }
    }

    private fun serveLogo(output: OutputStream) {
        val bytes = logoBytes ?: return
        try {
            val headers =
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: try-all\r\n" +
                            "\r\n"
            output.write(headers.toByteArray())
            output.write(bytes)
            output.flush()
        } catch (_: Exception) {}
    }

    private fun extractQueryParam(path: String, key: String): String? {
        return path.substringAfter("?", "")
                .split("&")
                .find { it.startsWith("$key=") }
                ?.substringAfter("=")
    }

    private fun getLocalIpAddress(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.allNetworks.forEach { net ->
            cm.getNetworkCapabilities(net)?.let { cap ->
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    cm.getLinkProperties(net)?.linkAddresses?.forEach { la ->
                        val addr = la.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress)
                                return addr.hostAddress ?: ""
                    }
                }
            }
        }
        return "127.0.0.1"
    }
}
