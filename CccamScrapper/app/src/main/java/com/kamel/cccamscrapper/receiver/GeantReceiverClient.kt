package com.kamel.cccamscrapper.receiver

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.zip.Inflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReceiverInfo(
    val productName: String,
    val softwareVersion: String,
    val serialNumber: String,
    val channelCount: Int
)

data class ReceiverProbeResult(
    val connected: Boolean,
    val info: ReceiverInfo? = null,
    val message: String
)

enum class RemoteKeyMode(val label: String) {
    KEY_VAL("A"),
    KEY_VALUE("B"),
    KEY_CODE("C"),
    SPECIAL_MENU("D")
}

class GeantReceiverClient(
    private val port: Int = 20_000,
    private val timeoutMs: Int = 5_000
) {
    suspend fun probe(ipAddress: String): ReceiverProbeResult = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.keepAlive = true
                socket.connect(InetSocketAddress(ipAddress, port), timeoutMs)
                socket.soTimeout = 15_000

                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
                val reader = GcdhReader(socket.getInputStream())

                writer.sendCommand(
                    CMD_IDENTIFY,
                    "<data>${Build.MODEL}</data><uuid>${UUID.randomUUID()}-02:00:00:00:00:00</uuid>"
                )
                writer.sendCommand(CMD_STATUS, null)
                reader.readXml(2_000)

                writer.sendCommand(CMD_GET_STB_INFO, null)
                val infoXml = reader.readXml(5_000)
                val info = infoXml?.let(::parseInfo)

                if (info != null) {
                    ReceiverProbeResult(
                        connected = true,
                        info = info,
                        message = "${info.productName} - ${info.softwareVersion}"
                    )
                } else {
                    ReceiverProbeResult(
                        connected = true,
                        message = "Connecte, mais informations non recues"
                    )
                }
            }
        } catch (error: Exception) {
            ReceiverProbeResult(
                connected = false,
                message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            )
        }
    }

    suspend fun sendKey(
        ipAddress: String,
        keyCode: Int,
        mode: RemoteKeyMode = RemoteKeyMode.KEY_VAL
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, port), timeoutMs)
                socket.soTimeout = 5_000
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

                writer.sendCommand(
                    CMD_IDENTIFY,
                    "<data>${Build.MODEL}</data><uuid>${UUID.randomUUID()}-02:00:00:00:00:00</uuid>"
                )
                writer.sendCommand(CMD_STATUS, null)
                Thread.sleep(120)

                when (mode) {
                    RemoteKeyMode.KEY_VAL -> writer.sendCommand(CMD_SEND_KEY, "<parm><KeyVal>$keyCode</KeyVal></parm>")
                    RemoteKeyMode.KEY_VALUE -> writer.sendCommand(CMD_SEND_KEY, "<parm><KeyValue>$keyCode</KeyValue></parm>")
                    RemoteKeyMode.KEY_CODE -> writer.sendCommand(CMD_SEND_KEY, "<parm><KeyCode>$keyCode</KeyCode></parm>")
                    RemoteKeyMode.SPECIAL_MENU -> writer.sendCommand(CMD_SPECIAL_MENU, "<parm><KeyVal>$keyCode</KeyVal></parm>")
                }
                Thread.sleep(120)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun PrintWriter.sendCommand(requestCode: Int, content: String?) {
        val xml = if (content != null) {
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"$requestCode\">$content</Command>"
        } else {
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"$requestCode\" />"
        }
        val command = "Start${xml.length.toString().padStart(7, '0')}End$xml"
        print(command)
        flush()
    }

    private fun parseInfo(xml: String): ReceiverInfo? {
        val productName = xml.extractTag("ProductName")
        if (productName.isBlank()) return null

        return ReceiverInfo(
            productName = productName,
            softwareVersion = xml.extractTag("SoftwareVersion"),
            serialNumber = xml.extractTag("SerialNumber"),
            channelCount = xml.extractTag("ChannelNum").toIntOrNull() ?: 0
        )
    }

    private class GcdhReader(private val input: java.io.InputStream) {
        private val readBuffer = ByteArray(65_536)
        private var bufferData = ByteArray(0)

        fun readXml(timeoutMs: Long): String? {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val available = input.available()
                if (available > 0) {
                    val count = input.read(readBuffer, 0, minOf(available, readBuffer.size))
                    if (count > 0) bufferData += readBuffer.copyOf(count)
                } else {
                    Thread.sleep(20)
                }

                val pos = bufferData.indexOf(GCDH)
                if (pos >= 0) {
                    if (bufferData.size < pos + 16) continue
                    val size = bufferData.readLittleEndianInt(pos + 4)
                    if (size <= 0 || size > 500_000) {
                        bufferData = bufferData.copyOfRange(pos + 4, bufferData.size)
                        continue
                    }
                    while (bufferData.size < pos + 16 + size) {
                        val count = input.read(readBuffer, 0, readBuffer.size)
                        if (count > 0) bufferData += readBuffer.copyOf(count)
                    }
                    val compressed = bufferData.copyOfRange(pos + 16, pos + 16 + size)
                    bufferData = bufferData.copyOfRange(pos + 16 + size, bufferData.size)
                    return compressed.inflateUtf8()
                }
            }
            return null
        }
    }

    companion object {
        const val DEFAULT_IP = "192.168.1.11"

        const val KEY_BACK = 4
        const val KEY_DPAD_UP = 19
        const val KEY_DPAD_DOWN = 20
        const val KEY_DPAD_LEFT = 21
        const val KEY_DPAD_RIGHT = 22
        const val KEY_DPAD_CENTER = 23
        const val KEY_MENU = 82

        private const val CMD_IDENTIFY = 998
        private const val CMD_STATUS = 20
        private const val CMD_GET_STB_INFO = 15
        private const val CMD_SEND_KEY = 2
        private const val CMD_SPECIAL_MENU = 1005

        private val GCDH = byteArrayOf('G'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte(), 'H'.code.toByte())
    }
}

private fun String.extractTag(tagName: String): String {
    return Regex("<$tagName>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.trim()
        .orEmpty()
}

private fun ByteArray.indexOf(needle: ByteArray): Int {
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}

private fun ByteArray.readLittleEndianInt(offset: Int): Int {
    return (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)
}

private fun ByteArray.inflateUtf8(): String {
    val inflater = Inflater()
    inflater.setInput(this)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0) break
        output.write(buffer, 0, count)
    }
    inflater.end()
    return output.toString("UTF-8")
}
