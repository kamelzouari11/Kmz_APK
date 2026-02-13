package com.prototype.upnpradio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

/**
 * StreamProxy - Local HTTP server that proxies HTTPS audio streams.
 *
 * Many UPnP/DLNA devices (like the Sangean) don't support HTTPS. This proxy fetches the HTTPS
 * stream and re-serves it via plain HTTP so the device can access it.
 *
 * Usage: proxy.start() val localUrl = proxy.getProxyUrl(originalHttpsUrl) // Send localUrl to the
 * UPnP device instead of the HTTPS URL // e.g. http://192.168.1.100:12345/stream?url=https://...
 */
class StreamProxy(private val context: Context) {

    companion object {
        private const val TAG = "StreamProxy"
        private const val BUFFER_SIZE = 16384
        private const val LOGO_SIZE = 300
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverThread: Thread? = null
    private var logoBitmap: Bitmap? = null
    private var logoBytes: ByteArray? = null
    var port: Int = 0
        private set

    var onLog: ((String) -> Unit)? = null

    /** Start the proxy server on a random available port. */
    fun start() {
        if (isRunning) return

        try {
            serverSocket = ServerSocket(0).also { port = it.localPort }
            isRunning = true

            onLog?.invoke("🌐 Proxy HTTP démarré sur le port $port")
            onLog?.invoke("   IP locale: ${getLocalIpAddress()}")

            serverThread = Thread {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        onLog?.invoke(
                                "🌐 Proxy: connexion entrante de ${clientSocket.inetAddress.hostAddress}"
                        )
                        Thread { handleClient(clientSocket) }.start()
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Proxy accept error", e)
                        }
                    }
                }
            }
            serverThread?.isDaemon = true
            serverThread?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start proxy", e)
            onLog?.invoke("❌ Erreur démarrage proxy: ${e.message}")
        }
    }

    /** Stop the proxy server. */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        onLog?.invoke("🌐 Proxy arrêté")
    }

    /**
     * Build the local proxy URL for a given remote stream URL. The Sangean will request this URL,
     * and the proxy will forward to the real stream.
     */
    fun getProxyUrl(remoteUrl: String): String {
        val localIp = getLocalIpAddress()
        val encodedUrl = java.net.URLEncoder.encode(remoteUrl, "UTF-8")
        return "http://$localIp:$port/stream?url=$encodedUrl"
    }

    /** Get the local URL for the radio logo (with .jpg extension for device compatibility). */
    fun getLogoUrl(): String? {
        if (logoBytes == null) return null
        val localIp = getLocalIpAddress()
        return "http://$localIp:$port/logo.jpg"
    }

    /**
     * Set the logo to serve. The bitmap is scaled to 300x300 and compressed to JPEG. Call this
     * before play() to make the logo available.
     */
    fun setLogo(bitmap: Bitmap) {
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, LOGO_SIZE, LOGO_SIZE, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            logoBytes = baos.toByteArray()
            logoBitmap = scaled
            onLog?.invoke("🖼️ Logo chargé (${logoBytes!!.size} bytes, ${LOGO_SIZE}x${LOGO_SIZE})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set logo", e)
            onLog?.invoke("❌ Erreur chargement logo: ${e.message}")
        }
    }

    /** Download a logo from a URL and set it. Must be called from a background thread. */
    fun setLogoFromUrl(imageUrl: String) {
        try {
            onLog?.invoke("🖼️ Téléchargement logo: $imageUrl")
            val conn = URL(imageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            val bitmap = BitmapFactory.decodeStream(conn.inputStream)
            conn.disconnect()
            if (bitmap != null) {
                setLogo(bitmap)
            } else {
                onLog?.invoke("⚠️ Logo: image non décodable")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download logo", e)
            onLog?.invoke("⚠️ Logo non téléchargé: ${e.message}")
        }
    }

    /** Set logo from a drawable resource ID. Supports both Bitmaps and Vectors. */
    fun setLogoFromResource(resId: Int) {
        try {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return

            // Create a bitmap that matches our target logo size
            val bitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            setLogo(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load logo from resource", e)
        }
    }

    /**
     * Handle an incoming HTTP request from the UPnP device. Parse the request, extract the target
     * URL, fetch it, and proxy the response.
     */
    private fun handleClient(clientSocket: Socket) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            // Read the HTTP request line and headers
            val requestLine = readLine(input)
            onLog?.invoke("🌐 Proxy requête: $requestLine")

            if (requestLine.isNullOrEmpty()) {
                clientSocket.close()
                return
            }

            // Read remaining headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim()] =
                            line.substring(colonIdx + 1).trim()
                }
            }

            // Parse the URL from the request
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendError(output, 400, "Bad Request")
                clientSocket.close()
                return
            }

            val requestPath = parts[1]

            // Route: /logo.jpg → serve logo image
            if (requestPath.contains("logo") && requestPath.endsWith(".jpg")) {
                serveLogo(output)
                clientSocket.close()
                return
            }

            // Route: /stream?url=... → proxy audio stream
            val urlParam = extractQueryParam(requestPath, "url")
            if (urlParam == null) {
                onLog?.invoke("🌐 Proxy: pas de paramètre 'url'")
                sendError(output, 400, "Missing url parameter")
                clientSocket.close()
                return
            }

            val targetUrl = java.net.URLDecoder.decode(urlParam, "UTF-8")
            onLog?.invoke("🌐 Proxy → fetching: $targetUrl")

            // Connect to the actual stream
            proxyStream(targetUrl, headers, output)

            clientSocket.close()
        } catch (e: Exception) {
            onLog?.invoke("🌐 Proxy erreur client: ${e.message}")
            Log.e(TAG, "Proxy client error", e)
            try {
                clientSocket.close()
            } catch (_: Exception) {}
        }
    }

    /** Serve the logo image as JPEG to the requesting device. */
    private fun serveLogo(output: OutputStream) {
        val bytes = logoBytes
        if (bytes == null) {
            onLog?.invoke("🖼️ Logo demandé mais aucun logo chargé")
            sendError(output, 404, "No logo available")
            return
        }

        try {
            onLog?.invoke("🖼️ Logo servi (${bytes.size} bytes)")
            val sb = StringBuilder()
            sb.append("HTTP/1.1 200 OK\r\n")
            sb.append("Content-Type: image/jpeg\r\n")
            sb.append("Content-Length: ${bytes.size}\r\n")
            sb.append("Connection: close\r\n")
            sb.append("\r\n")
            output.write(sb.toString().toByteArray())
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serve logo", e)
        }
    }

    /** Fetch the remote stream and pipe it to the client output. */
    private fun proxyStream(
            targetUrl: String,
            clientHeaders: Map<String, String>,
            clientOutput: OutputStream
    ) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "UPnP-Proxy/1.0")
            connection.setRequestProperty("Accept", "*/*")

            // Forward Range header if present
            clientHeaders["Range"]?.let {
                connection.setRequestProperty("Range", it)
                onLog?.invoke("🌐 Proxy: forwarding Range: $it")
            }

            val responseCode = connection.responseCode
            val contentType = connection.contentType ?: "audio/mpeg"
            val contentLength = connection.contentLength

            onLog?.invoke("🌐 Proxy: stream HTTP $responseCode, type=$contentType")

            // Build HTTP response to the UPnP device
            val sb = StringBuilder()
            if (responseCode == 206) {
                sb.append("HTTP/1.1 206 Partial Content\r\n")
            } else {
                sb.append("HTTP/1.1 200 OK\r\n")
            }
            sb.append("Content-Type: $contentType\r\n")
            sb.append("Accept-Ranges: bytes\r\n")
            sb.append("Connection: close\r\n")
            sb.append("transferMode.dlna.org: Streaming\r\n")
            sb.append(
                    "contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n"
            )

            if (contentLength > 0) {
                sb.append("Content-Length: $contentLength\r\n")
            }

            // Forward Content-Range if present
            connection.getHeaderField("Content-Range")?.let { sb.append("Content-Range: $it\r\n") }

            sb.append("\r\n")

            // Send headers
            clientOutput.write(sb.toString().toByteArray())
            clientOutput.flush()

            // Pipe the audio stream
            val buffer = ByteArray(BUFFER_SIZE)
            val inputStream = connection.inputStream
            var totalBytes = 0L

            while (isRunning) {
                val bytesRead =
                        try {
                            inputStream.read(buffer)
                        } catch (_: Exception) {
                            -1
                        }

                if (bytesRead == -1) break

                try {
                    clientOutput.write(buffer, 0, bytesRead)
                    clientOutput.flush()
                    totalBytes += bytesRead
                } catch (_: Exception) {
                    // Client disconnected
                    break
                }
            }

            onLog?.invoke("🌐 Proxy: stream terminé ($totalBytes bytes transférés)")
        } catch (e: Exception) {
            onLog?.invoke("🌐 Proxy: erreur stream: ${e.message}")
            Log.e(TAG, "Proxy stream error", e)
            try {
                val error = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n"
                clientOutput.write(error.toByteArray())
            } catch (_: Exception) {}
        } finally {
            connection?.disconnect()
        }
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        val response = "HTTP/1.1 $code $message\r\nContent-Length: 0\r\n\r\n"
        output.write(response.toByteArray())
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun extractQueryParam(path: String, param: String): String? {
        val queryStart = path.indexOf('?')
        if (queryStart == -1) return null
        val query = path.substring(queryStart + 1)
        for (pair in query.split("&")) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0 && pair.substring(0, eqIdx) == param) {
                return pair.substring(eqIdx + 1)
            }
        }
        return null
    }

    /**
     * Get the device's WiFi IP address using ConnectivityManager (modern API). This is the same
     * approach used by the working RadioUpnp app.
     */
    fun getLocalIpAddress(): String {
        try {
            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            for (network in connectivityManager.allNetworks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue

                val linkProperties = connectivityManager.getLinkProperties(network) ?: continue
                for (linkAddress in linkProperties.linkAddresses) {
                    val address = linkAddress.address
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: continue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
        }
        return "127.0.0.1"
    }
}
