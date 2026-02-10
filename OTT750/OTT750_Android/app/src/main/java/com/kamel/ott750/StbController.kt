package com.kamel.ott750

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.InputStream
import java.util.UUID
import java.util.zip.Inflater

/**
 * Contrôleur de communication avec le récepteur STB Hisilicon Hi3798.
 */
class StbController(private val context: Context) {
    
    // Provider Mapper pour enrichir les chaînes avec leur provider
    private val providerMapper: ProviderMapper by lazy { ProviderMapper.getInstance(context) }
    
    // Database Manager pour la persistance locale
    private val databaseManager: DatabaseManager by lazy { DatabaseManager(context) }

    companion object {
        private const val TAG = "StbController"
        
        const val DEFAULT_STB_IP = "192.168.1.12"
        const val DEFAULT_PORT = 20000
        
        const val CMD_IDENTIFY = 998
        const val CMD_INIT = 1012
        const val CMD_STATUS = 20
        const val CMD_GET_CHANNELS = 0
        const val CMD_GET_LOCK_STATUS = 1
        const val CMD_GET_TIME = 11        // Heure du STB
        const val CMD_GET_FAV_GROUPS = 12
        const val CMD_GET_SLEEP_SETTINGS = 13
        const val CMD_GET_STB_INFO = 15
        const val CMD_CHANGE_CHANNEL = 1000
        const val CMD_SET_FAVORITE = 1004
        
        const val CHANNELS_PER_REQUEST = 100
    }
    
    var isConnected = false
        private set
    
    var stbAddress: String = DEFAULT_STB_IP
        private set
    
    var stbInfo: StbInfo? = null
        private set
        
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var inputStream: InputStream? = null
    private var deviceUuid: String = UUID.randomUUID().toString()
    private var readBuffer = ByteArray(65536)
    private var bufferData = ByteArray(0)

    private var isZapping = false
    private var currentProgramId: String = ""
    
    var channels: MutableList<StbChannel> = mutableListOf()
        private set
    var favoriteGroups: MutableList<FavoriteGroup> = mutableListOf()
        private set
        
    fun updateFavoriteGroups(groups: List<FavoriteGroup>) {
        favoriteGroups.clear()
        favoriteGroups.addAll(groups)
    }
    
    interface ConnectionListener {
        fun onConnected(address: String)
        fun onDisconnected()
        fun onError(error: String)
        fun onChannelsLoaded(count: Int)
        fun onFavoritesSaved(success: Boolean)
        fun onProgress(message: String)
    }
    
    var listener: ConnectionListener? = null
    
    suspend fun connect(ipAddress: String = DEFAULT_STB_IP): Boolean = withContext(Dispatchers.IO) {
        stbAddress = ipAddress
        
        try {
            disconnect()
            
            socket = Socket()
            socket?.keepAlive = true // Tentative d'activation du KeepAlive TCP pour maintenir la connexion
            socket?.connect(InetSocketAddress(ipAddress, DEFAULT_PORT), 5000)
            socket?.soTimeout = 15000
            
            writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8), true)
            inputStream = socket!!.getInputStream()
            bufferData = ByteArray(0)
            
            Log.d(TAG, "Connected to $ipAddress:$DEFAULT_PORT")
            
            // Séquence d'initialisation (comme gmscreen)
            sendCommand(CMD_IDENTIFY, "<data>${Build.MODEL}</data><uuid>$deviceUuid-02:00:00:00:00:00</uuid>")
            delay(200)
            
            sendCommand(CMD_STATUS, null)
            delay(100)
            
            // Ignorer les réponses initiales
            skipGarbageAndReadGcdh(2000)
            
            isConnected = true
            
            withContext(Dispatchers.Main) {
                listener?.onConnected(ipAddress)
            }
            
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            isConnected = false
            
            withContext(Dispatchers.Main) {
                listener?.onError("Connexion échouée: ${e.message}")
            }
            
            return@withContext false
        }
    }
    
    private fun sendCommand(requestCode: Int, content: String?): Boolean {
        val xml = if (content != null) {
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"$requestCode\">$content</Command>"
        } else {
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"$requestCode\" />"
        }
        
        val length = xml.length.toString().padStart(7, '0')
        val command = "Start${length}End$xml"
        
        return try {
            if (writer == null || writer!!.checkError()) { // Vérification de l'état avant envoi
                throw Exception("Writer is null or in error state")
            }
            writer?.print(command)
            writer?.flush()
            if (writer!!.checkError()) { // Vérification post-envoi pour détecter une coupure immédiate
                 throw Exception("Writer error after flush")
            }
            Log.d(TAG, "Sent: request=$requestCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            false
        }
    }
    
    /**
     * Lit les données du socket en ignorant les données "garbage" avant GCDH
     */
    private fun skipGarbageAndReadGcdh(timeoutMs: Int): String? {
        try {
            val input = inputStream ?: return null
            val startTime = System.currentTimeMillis()
            
            // Lire les données jusqu'à trouver GCDH
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // Lire plus de données disponibles
                val available = input.available()
                if (available > 0) {
                    val count = input.read(readBuffer, 0, minOf(available, readBuffer.size))
                    if (count > 0) {
                        bufferData = bufferData + readBuffer.copyOf(count)
                    }
                } else {
                    // Petite pause pour laisser arriver les données
                    try { Thread.sleep(10) } catch (e: InterruptedException) { break }
                }
                
                // Chercher GCDH dans le buffer
                val gcdh = byteArrayOf('G'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte(), 'H'.code.toByte())
                val pos = findBytes(bufferData, gcdh)
                
                if (pos >= 0) {
                    // Jeter les données avant GCDH
                    bufferData = bufferData.copyOfRange(pos, bufferData.size)
                    
                    // Vérifier qu'on a le header complet (16 bytes)
                    while (bufferData.size < 16) {
                        val count = input.read(readBuffer, 0, readBuffer.size)
                        if (count > 0) {
                            bufferData = bufferData + readBuffer.copyOf(count)
                        }
                    }
                    
                    // Lire la taille (little endian)
                    val size = (bufferData[4].toInt() and 0xFF) or
                              ((bufferData[5].toInt() and 0xFF) shl 8) or
                              ((bufferData[6].toInt() and 0xFF) shl 16) or
                              ((bufferData[7].toInt() and 0xFF) shl 24)
                    
                    Log.d(TAG, "Found GCDH, size=$size")
                    
                    if (size <= 0 || size > 500_000) {
                        Log.e(TAG, "Invalid size: $size")
                        bufferData = bufferData.copyOfRange(4, bufferData.size)
                        continue
                    }
                    
                    // Lire les données compressées
                    while (bufferData.size < 16 + size) {
                        val count = input.read(readBuffer, 0, readBuffer.size)
                        if (count > 0) {
                            bufferData = bufferData + readBuffer.copyOf(count)
                        }
                    }
                    
                    // Extraire les données compressées
                    val compressed = bufferData.copyOfRange(16, 16 + size)
                    bufferData = bufferData.copyOfRange(16 + size, bufferData.size)
                    
                    // Décompresser
                    return try {
                        val inflater = Inflater()
                        inflater.setInput(compressed)
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (!inflater.finished()) {
                            val count = inflater.inflate(buffer)
                            if (count == 0) break
                            output.write(buffer, 0, count)
                        }
                        inflater.end()
                        val result = output.toString("UTF-8")
                        Log.d(TAG, "Decompressed: ${result.length} bytes")
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "Decompress error: ${e.message}")
                        null
                    }
                }
                
                Thread.sleep(50)
            }
            
            Log.d(TAG, "Timeout waiting for GCDH")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Read error: ${e.message}")
            return null
        }
    }
    
    private fun findBytes(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
    
    suspend fun loadChannels(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected) {
            if (!connect(stbAddress)) return@withContext false
        }
        
        channels.clear()
        
        try {
            // Charger les groupes favoris
            withContext(Dispatchers.Main) { listener?.onProgress("Favoris...") }
            sendCommand(CMD_GET_FAV_GROUPS, null)
            delay(300)
            val favResponse = skipGarbageAndReadGcdh(5000)
            if (favResponse != null) {
                parseFavoriteGroups(favResponse)
                Log.d(TAG, "Loaded ${favoriteGroups.size} favorite groups")
                // Sauvegarde immédiate des groupes pour sécuriser la persistance
                launch(Dispatchers.IO) { databaseManager.saveFavoriteGroups(favoriteGroups) }
            }
            
            // Charger infos STB
            sendCommand(CMD_GET_STB_INFO, null)
            delay(300)
            val infoResponse = skipGarbageAndReadGcdh(5000)
            var totalChannels = 3500
            if (infoResponse != null) {
                parseStbInfo(infoResponse)
                totalChannels = stbInfo?.channelCount ?: 3500
                Log.d(TAG, "STB has $totalChannels channels")
            }
            
            // Charger les chaînes par blocs
            var fromIndex = 0
            var emptyCount = 0
            
            while (fromIndex < totalChannels && emptyCount < 3) {
                val toIndex = fromIndex + CHANNELS_PER_REQUEST - 1
                
                withContext(Dispatchers.Main) { 
                    listener?.onProgress("${channels.size}/$totalChannels...") 
                }
                
                val content = "<parm><FromIndex>$fromIndex</FromIndex><ToIndex>$toIndex</ToIndex></parm>"
                sendCommand(CMD_GET_CHANNELS, content)
                delay(300)
                
                val response = skipGarbageAndReadGcdh(8000)
                if (response != null) {
                    // DEBUG: Sauvegarder le premier bloc XML pour analyse
                    if (fromIndex == 0) {
                        try {
                            val debugFile = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "debug_channels.xml")
                            java.io.FileWriter(debugFile).use { it.write(response) }
                            Log.d("StbController", "DEBUG XML saved to $debugFile")
                        } catch (e: Exception) {
                            Log.e("StbController", "Failed to save debug xml", e)
                        }
                    }
                    
                    val parsed = parseChannels(response)
                    if (parsed.isNotEmpty()) {
                        channels.addAll(parsed)
                        Log.d(TAG, "Loaded $fromIndex-$toIndex: ${parsed.size} (total: ${channels.size})")
                        emptyCount = 0
                    } else {
                        emptyCount++
                        Log.w(TAG, "Empty response for $fromIndex-$toIndex")
                    }
                } else {
                    emptyCount++
                    Log.e(TAG, "No response for $fromIndex-$toIndex")
                }
                
                fromIndex += CHANNELS_PER_REQUEST
            }
            
            Log.d(TAG, "Total channels loaded: ${channels.size}")
            
            Log.d(TAG, "Total channels loaded: ${channels.size}")
            
            withContext(Dispatchers.Main) {
                // Sauvegarde via DatabaseManager (IO interne)
                launch(Dispatchers.IO) { saveChannels(context) }
                listener?.onChannelsLoaded(channels.size)
            }
            
            return@withContext channels.isNotEmpty()
            
        } catch (e: Exception) {
            Log.e(TAG, "Load channels error", e)
            withContext(Dispatchers.Main) {
                listener?.onError("Erreur: ${e.message}")
            }
            return@withContext false
        }
    }
    
    private fun parseFavoriteGroups(xml: String) {
        favoriteGroups.clear()
        try {
            val parmPattern = Regex("<parm>(.*?)</parm>", RegexOption.DOT_MATCHES_ALL)
            for (match in parmPattern.findAll(xml)) {
                val content = match.groupValues[1]
                val name = extractTag(content, "favorGroupName")
                val id = extractTag(content, "FavorGroupID").toIntOrNull() ?: 0
                if (id > 0 && name.isNotEmpty()) {
                    favoriteGroups.add(FavoriteGroup(id, name))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse fav groups error", e)
        }
    }
    
    private fun parseStbInfo(xml: String) {
        try {
            stbInfo = StbInfo(
                productName = extractTag(xml, "ProductName"),
                softwareVersion = extractTag(xml, "SoftwareVersion"),
                serialNumber = extractTag(xml, "SerialNumber"),
                channelCount = extractTag(xml, "ChannelNum").toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse STB info error", e)
        }
    }
    
    private fun parseChannels(xml: String): List<StbChannel> {
        val result = mutableListOf<StbChannel>()
        
        try {
            val parmPattern = Regex("<parm>(.*?)</parm>", RegexOption.DOT_MATCHES_ALL)
            
            for (match in parmPattern.findAll(xml)) {
                val content = match.groupValues[1]
                val programId = extractTag(content, "ProgramId")
                val name = extractTag(content, "ProgramName")
                
                if (programId.isNotEmpty() && name.isNotEmpty()) {
                    // Essayer d'obtenir le provider depuis le STB
                    var provider = (extractTag(content, "ServiceProviderName").takeIf { it.isNotEmpty() }
                               ?: extractTag(content, "ProviderName").takeIf { it.isNotEmpty() }
                               ?: extractTag(content, "ISPName")).trim()
                    
                    // Si pas de provider du STB, utiliser le ProviderMapper
                    if (provider.isEmpty()) {
                        provider = providerMapper.getProviderByName(name)
                    }
                    
                    result.add(StbChannel(
                        programId = programId,
                        name = name,
                        programIndex = extractTag(content, "ProgramIndex").toIntOrNull() ?: 0,
                        programType = extractTag(content, "ProgramType").toIntOrNull() ?: 0,
                        isHD = extractTag(content, "IsProgramHD") == "1",
                        favMark = extractTag(content, "FavMark").toIntOrNull() ?: 0,
                        favorGroupIds = StbChannel.parseFavorGroupIds(extractTag(content, "FavorGroupID")),
                        isLocked = extractTag(content, "LockMark") == "1",
                        channelType = extractTag(content, "ChannelType").toIntOrNull() ?: 0,
                        provider = provider
                    ))
                }
            }
            
            // Log des stats providers
            val withProvider = result.count { it.provider.isNotEmpty() }
            Log.d(TAG, "Parsed ${result.size} channels, $withProvider with provider (${100 * withProvider / maxOf(result.size, 1)}%)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Parse channels error: ${e.message}")
        }
        
        return result
    }
    
    private fun extractTag(content: String, tagName: String): String {
        val pattern = Regex("<$tagName>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(content)?.groupValues?.get(1) ?: ""
    }
    
    // === Commandes One-Shot (Connexion -> Commande -> Déconnexion) ===
    
    suspend fun saveFavorite(channel: StbChannel): Boolean = withContext(Dispatchers.IO) {
        ensureConnected()
        val favorGroupIdStr = channel.getFavorGroupIdString()
        val content = "<TvState>0</TvState><FavMark>${channel.favMark}</FavMark><FavorGroupID>$favorGroupIdStr</FavorGroupID><ProgramId>${channel.programId}</ProgramId>"
        return@withContext sendCommand(CMD_SET_FAVORITE, content)
    }
    
    suspend fun saveFavorites(channelsToSave: List<StbChannel>): Boolean = withContext(Dispatchers.IO) {
        ensureConnected()
        var allSuccess = true
        for (channel in channelsToSave) {
            if (!saveFavorite(channel)) allSuccess = false
            delay(150)
        }
        withContext(Dispatchers.Main) { listener?.onFavoritesSaved(allSuccess) }
        return@withContext allSuccess
    }

    private suspend fun ensureConnected() {
        if (!isConnected || socket == null || socket?.isConnected != true) {
            connect(stbAddress)
        }
    }
    
    /**
     * Récupère l'heure actuelle du STB (code 11).
     */
    suspend fun getStbTime(): StbTime? = withContext(Dispatchers.IO) {
        ensureConnected()
        if (!isConnected) return@withContext null
        
        try {
            sendCommand(CMD_GET_TIME, null)
            delay(300)
            
            val response = skipGarbageAndReadGcdh(3000)
            if (response != null && response.contains("<StbMonth>")) {
                val month = extractTag(response, "StbMonth").toIntOrNull() ?: 0
                val day = extractTag(response, "StbDay").toIntOrNull() ?: 0
                val hour = extractTag(response, "StbHour").toIntOrNull() ?: 0
                val min = extractTag(response, "StbMin").toIntOrNull() ?: 0
                return@withContext StbTime(month, day, hour, min)
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Get time error: ${e.message}")
            return@withContext null
        }
    }

    // === Commandes One-Shot abandonnées, retour au via la connexion principale mais renforcée ===

    /**
     * Assure qu'une connexion silencieuse est active.
     * Si déjà connecté, ne fait rien. Sinon, tente reconnectSilent().
     */
    private suspend fun ensureSilentConnection(): Boolean {
        return if (!isConnected || socket == null || socket?.isConnected != true) {
            reconnectSilent()
        } else true
    }

    suspend fun zapToChannel(channel: StbChannel): Boolean = withContext(Dispatchers.IO) {
        if (isZapping) {
            Log.w(TAG, "Already zapping, ignoring request for ${channel.name}")
            return@withContext false
        }

        isZapping = true
        try {
            Log.d(TAG, "Zapping to channel ${channel.name} (ProgramId=${channel.programId})")
            if (!ensureSilentConnection()) return@withContext false
            val content = "<parm><TvState>0</TvState><ProgramId>${channel.programId}</ProgramId></parm>"
            val success = sendCommandWithRetry(CMD_CHANGE_CHANNEL, content)
            if (success) {
                currentProgramId = channel.programId
            }
            return@withContext success
        } finally {
            // Laisser 300-500ms avant de permettre un nouveau zap
            delay(400)
            isZapping = false
        }
    }



    suspend fun channelUp(): Boolean = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext false
        
        var nextIndex = 0
        if (currentProgramId.isNotEmpty()) {
            val currentIndex = channels.indexOfFirst { it.programId == currentProgramId }
            if (currentIndex >= 0) {
                nextIndex = (currentIndex + 1) % channels.size
            }
        }
        val nextChannel = channels[nextIndex]
        return@withContext zapToChannel(nextChannel)
    }

    suspend fun channelDown(): Boolean = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext false
        
        var prevIndex = 0
        if (currentProgramId.isNotEmpty()) {
            val currentIndex = channels.indexOfFirst { it.programId == currentProgramId }
            if (currentIndex >= 0) {
                prevIndex = if (currentIndex - 1 < 0) channels.size - 1 else currentIndex - 1
            }
        }
        val prevChannel = channels[prevIndex]
        return@withContext zapToChannel(prevChannel)
    }

    private suspend fun sendCommandWithRetry(requestCode: Int, content: String?): Boolean {
        // Premier essai
        if (sendCommand(requestCode, content)) return true
        
        // Si échec, on assume que la connexion est morte, même si isConnected dit le contraire
        Log.w(TAG, "Command failed, forcing silent reconnect...")
        
        // Tentative de reconnexion
        if (reconnectSilent()) {
            // Deuxième essai
            return sendCommand(requestCode, content)
        }
        
        return false
    }
    
    /**
     * Reconnexion silencieuse - uniquement socket TCP, sans envoyer CMD_IDENTIFY ni CMD_STATUS
     * Cela évite le "SOFTWARE INITIALIZING" sur le STB lors du zapping
     */
    private suspend fun reconnectSilent(): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            
            socket = Socket()
            socket?.keepAlive = true
            socket?.connect(InetSocketAddress(stbAddress, DEFAULT_PORT), 5000)
            socket?.soTimeout = 15000
            
            writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8), true)
            inputStream = socket!!.getInputStream()
            bufferData = ByteArray(0)
            
            Log.d(TAG, "Silent reconnect to $stbAddress:$DEFAULT_PORT")
            isConnected = true
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Silent reconnect failed", e)
            isConnected = false
            return@withContext false
        }
    }
    
    suspend fun sendKey(keyCode: Int) = withContext(Dispatchers.IO) {
        // Utilisation du protocole TCP existant (GMScreen)
        // Tentative avec CMD 2 (souvent utilisé pour SendKey)
        val content = "<parm><KeyVal>$keyCode</KeyVal></parm>"
        
        if (sendCommandWithRetry(2, content)) {
            Log.d(TAG, "Key $keyCode sent successfully via TCP (CMD 2)")
        } else {
             Log.w(TAG, "Failed to send key $keyCode via TCP (CMD 2)")
        }
    }

    fun disconnect() {
        try {
            writer?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e("StbController", "Disconnect error", e)
        }
        writer = null
        inputStream = null
        socket = null
        isConnected = false
        bufferData = ByteArray(0)
    }
    
    suspend fun saveChannels(context: Context) {
        databaseManager.saveStbChannels(channels)
        databaseManager.saveFavoriteGroups(favoriteGroups)
    }
    
    suspend fun loadChannelsFromCache(context: Context): Boolean {
        return try {
            val loaded = databaseManager.loadStbChannels()
            if (loaded.isNotEmpty()) {
                channels = loaded
                
                // Charger également les groupes
                val loadedGroups = databaseManager.loadFavoriteGroups()
                if (loadedGroups.isNotEmpty()) {
                    favoriteGroups = loadedGroups
                }
                
                Log.d("StbController", "Loaded ${channels.size} channels and ${favoriteGroups.size} groups from cache")
                true
            } else {
                // Fallback ancienne méthode serialization si DB vide (migration)
                if (loadChannelsLegacy(context)) {
                     // Migrer vers DB
                     saveChannels(context)
                     true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("StbController", "Load cache error", e)
            false
        }
    }

    private fun loadChannelsLegacy(context: Context): Boolean {
        try {
            val file = java.io.File(context.filesDir, "channels.dat")
            if (!file.exists()) return false
            
            val fileGroups = java.io.File(context.filesDir, "groups.dat")
            if (fileGroups.exists()) {
                java.io.ObjectInputStream(java.io.FileInputStream(fileGroups)).use { 
                    @Suppress("UNCHECKED_CAST")
                    favoriteGroups = it.readObject() as MutableList<FavoriteGroup>
                }
            }
            
            java.io.ObjectInputStream(java.io.FileInputStream(file)).use { 
                @Suppress("UNCHECKED_CAST")
                channels = it.readObject() as MutableList<StbChannel>
            }
            return channels.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }
}

data class StbInfo(
    val productName: String,
    val softwareVersion: String,
    val serialNumber: String,
    val channelCount: Int
)

/**
 * Heure actuelle du STB.
 */
data class StbTime(
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int
) {
    override fun toString(): String = "%02d/%02d %02d:%02d".format(day, month, hour, minute)
}
