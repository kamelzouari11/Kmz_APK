package com.kamel.cccamscrapper.tester

import com.kamel.cccamscrapper.parser.CccamServer
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ServerState {
    UNTESTED,
    TESTING,
    ACTIVE,
    VALID,
    INACTIVE
}

data class ServerTestResult(
    val state: ServerState,
    val latencyMs: Long? = null,
    val message: String
)

class CccamTester(
    private val timeoutMs: Int = 5_000
) {
    suspend fun test(server: CccamServer): ServerTestResult = withContext(Dispatchers.IO) {
        var result: ServerTestResult? = null
        var errorMessage = "Connexion impossible"

        val latency = measureTimeMillis {
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(server.host, server.port), timeoutMs)
                    result = validateCccamLogin(socket, server)
                }
            } catch (error: Exception) {
                errorMessage = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            }
        }

        result?.let {
            if (it.state == ServerState.VALID) {
                return@withContext it.copy(latencyMs = latency)
            }
            return@withContext it
        }

        ServerTestResult(
            state = ServerState.INACTIVE,
            message = errorMessage
        )
    }

    private fun validateCccamLogin(socket: Socket, server: CccamServer): ServerTestResult {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val seed = input.readExact(16)
        ccXor(seed)

        val hash = MessageDigest.getInstance("SHA-1").digest(seed)

        val decrypt = CccamCipher(hash)
        decrypt.crypt(seed, CryptMode.DECRYPT)

        val encrypt = CccamCipher(seed)
        encrypt.crypt(hash, CryptMode.DECRYPT)
        encrypt.crypt(hash, CryptMode.ENCRYPT)
        output.write(hash)

        val username = ByteArray(20)
        server.username.toByteArray(Charsets.ISO_8859_1)
            .copyInto(username, endIndex = minOf(server.username.length, username.size))
        encrypt.crypt(username, CryptMode.ENCRYPT)
        output.write(username)

        val password = server.password.toByteArray(Charsets.ISO_8859_1)
        encrypt.crypt(password, CryptMode.ENCRYPT)

        val passwordProof = ByteArray(6)
        "CCcam".toByteArray(Charsets.ISO_8859_1).copyInto(passwordProof)
        encrypt.crypt(passwordProof, CryptMode.ENCRYPT)
        output.write(passwordProof)
        output.flush()

        val ack = input.readExact(20)
        decrypt.crypt(ack, CryptMode.DECRYPT)

        return if (ack.copyOfRange(0, 5).contentEquals(CCCAM_MARKER.copyOfRange(0, 5))) {
            sendClientData(output, encrypt, server.username)
            ServerTestResult(
                state = ServerState.VALID,
                message = "Compte valide"
            )
        } else {
            ServerTestResult(
                state = ServerState.ACTIVE,
                message = "Port ouvert, login refuse"
            )
        }
    }

    private fun sendClientData(output: OutputStream, encrypt: CccamCipher, username: String) {
        val payload = ByteArray(91)
        username.toByteArray(Charsets.ISO_8859_1)
            .copyInto(payload, endIndex = minOf(username.length, 20))
        createNodeId().copyInto(payload, destinationOffset = 20)
        payload[28] = 0
        "2.1.1".toByteArray(Charsets.ISO_8859_1).copyInto(payload, destinationOffset = 29)
        "2971".toByteArray(Charsets.ISO_8859_1).copyInto(payload, destinationOffset = 61)
        sendCommand(output, encrypt, payload, MSG_CLI_DATA)
    }

    private fun sendCommand(output: OutputStream, encrypt: CccamCipher, payload: ByteArray, command: Int) {
        val packet = ByteArray(payload.size + 4)
        packet[0] = 0
        packet[1] = command.toByte()
        packet[2] = (payload.size shr 8).toByte()
        packet[3] = payload.size.toByte()
        payload.copyInto(packet, destinationOffset = 4)
        encrypt.crypt(packet, CryptMode.ENCRYPT)
        output.write(packet)
        output.flush()
    }

    private fun createNodeId(): ByteArray {
        val nodeId = ByteArray(8)
        Random.nextBytes(nodeId, fromIndex = 0, toIndex = 4)
        var sum = 0x1234
        for (i in 0 until 4) {
            sum += nodeId[i].toInt() and 0xff
        }
        nodeId[4] = 0x10
        sum += nodeId[4].toInt() and 0xff
        nodeId[5] = 0xAA.toByte()
        for (i in 0 until 5) {
            nodeId[5] = ((nodeId[5].toInt() and 0xff) xor (nodeId[i].toInt() and 0xff)).toByte()
        }
        sum += nodeId[5].toInt() and 0xff
        nodeId[6] = (sum shr 8).toByte()
        nodeId[7] = sum.toByte()
        return nodeId
    }
}

private const val MSG_CLI_DATA = 0
private val CCCAM_MARKER = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 0)

private enum class CryptMode {
    DECRYPT,
    ENCRYPT
}

private class CccamCipher(key: ByteArray) {
    private val keyTable = IntArray(256) { it }
    private var state = key.first().toInt() and 0xff
    private var counter = 0
    private var sum = 0

    init {
        var j = 0
        for (i in 0 until 256) {
            j = (j + (key[i % key.size].toInt() and 0xff) + keyTable[i]) and 0xff
            keyTable.swap(i, j)
        }
    }

    fun crypt(data: ByteArray, mode: CryptMode) {
        for (i in data.indices) {
            counter = (counter + 1) and 0xff
            sum = (sum + keyTable[counter]) and 0xff
            keyTable.swap(counter, sum)

            var z = data[i].toInt() and 0xff
            data[i] = (z xor keyTable[(keyTable[counter] + keyTable[sum]) and 0xff]).toByte()
            data[i] = ((data[i].toInt() and 0xff) xor state).toByte()
            if (mode == CryptMode.DECRYPT) {
                z = data[i].toInt() and 0xff
            }
            state = state xor z
        }
    }
}

private fun IntArray.swap(first: Int, second: Int) {
    val tmp = this[first]
    this[first] = this[second]
    this[second] = tmp
}

private fun ccXor(buffer: ByteArray) {
    for (i in 0 until 8) {
        buffer[8 + i] = ((i * (buffer[i].toInt() and 0xff)) and 0xff).toByte()
        if (i <= 5) {
            buffer[i] = ((buffer[i].toInt() and 0xff) xor (CCCAM_MARKER[i].toInt() and 0xff)).toByte()
        }
    }
}

private fun InputStream.readExact(size: Int): ByteArray {
    val buffer = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(buffer, offset, size - offset)
        if (read == -1) throw EOFException("Connexion fermee")
        offset += read
    }
    return buffer
}
