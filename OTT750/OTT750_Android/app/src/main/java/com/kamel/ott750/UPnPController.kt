package com.kamel.ott750

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

/**
 * Contrôleur pour la communication avec le récepteur satellite Hisilicon Hi3798MV200.
 * Protocole propriétaire découvert via analyse de trafic gmscreen.
 */
class UPnPController(private val context: Context) {

    companion object {
        private const val TAG = "UPnPController"
        
        // Configuration par défaut
        const val DEFAULT_STB_IP = "192.168.1.12"
        const val DEFAULT_PORT = 20000
        
        // Commandes du protocole gmscreen
        const val CMD_IDENTIFY = 998
        const val CMD_INIT = 1012
        const val CMD_STATUS = 20
        const val CMD_HEARTBEAT = 3
        const val CMD_CHECK = 26
        const val CMD_CHANGE_CHANNEL = 1000
    }
    
    var isConnected = false
        private set
    
    var stbAddress: String = DEFAULT_STB_IP
        private set
    
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var deviceUuid: String = UUID.randomUUID().toString()
    
    interface ConnectionListener {
        fun onDeviceFound(address: String)
        fun onConnectionFailed(error: String)
        fun onCommandSent(success: Boolean)
    }
    
    var listener: ConnectionListener? = null
    
    /**
     * Connexion à l'adresse par défaut
     */
    suspend fun connectToDefault(): Boolean = withContext(Dispatchers.IO) {
        return@withContext connectDirect(DEFAULT_STB_IP)
    }
    
    /**
     * Connexion directe avec handshake complet
     */
    suspend fun connectDirect(ipAddress: String, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        stbAddress = ipAddress
        
        try {
            // Fermer l'ancienne connexion si existante
            disconnect()
            
            // Nouvelle connexion
            socket = Socket()
            socket?.connect(InetSocketAddress(ipAddress, port), 5000)
            socket?.soTimeout = 10000
            
            writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), Charsets.UTF_8), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8))
            
            Log.d(TAG, "Socket connected to $ipAddress:$port")
            
            // Séquence d'identification (comme gmscreen)
            val deviceName = Build.MODEL
            val identifyCmd = buildCommand(CMD_IDENTIFY, "<data>$deviceName</data><uuid>$deviceUuid</uuid>")
            sendRawCommand(identifyCmd)
            delay(100)
            
            // CMD_INIT supprimée pour éviter "Software Initializing" sur le STB
            // val initCmd = buildCommand(CMD_INIT, null)
            // sendRawCommand(initCmd)
            // delay(100)
            
            // Commande de status
            val statusCmd = buildCommand(CMD_STATUS, null)
            sendRawCommand(statusCmd)
            
            isConnected = true
            
            withContext(Dispatchers.Main) {
                listener?.onDeviceFound("$ipAddress:$port")
            }
            
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            isConnected = false
            
            withContext(Dispatchers.Main) {
                listener?.onConnectionFailed("Connexion échouée: ${e.message}")
            }
            
            return@withContext false
        }
    }
    
    /**
     * Construit une commande au format gmscreen
     * Format: Start[length 8 digits]End + XML payload
     */
    private fun buildCommand(requestCode: Int, innerContent: String?): String {
        val xml = if (innerContent != null) {
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"$requestCode\">$innerContent</Command>"
        } else {
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><Command request=\"$requestCode\" />"
        }
        
        val length = xml.length.toString().padStart(7, '0')
        return "Start${length}End$xml"
    }
    
    /**
     * Envoie une commande brute
     */
    private fun sendRawCommand(command: String): Boolean {
        return try {
            writer?.print(command)
            writer?.flush()
            Log.d(TAG, "Sent: ${command.take(100)}...")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
            false
        }
    }
    
    /**
     * Change de chaîne avec le ProgramId
     * Le ProgramId dans la DB correspond au format utilisé par gmscreen
     */
    suspend fun zapToChannelById(programId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected || socket == null || socket?.isConnected != true) {
            // Tenter une reconnexion
            if (!connectDirect(stbAddress)) {
                return@withContext false
            }
        }
        
        try {
            // Format du ProgramId: 14 chiffres avec padding
            val formattedProgramId = programId.toString().padStart(14, '0')
            
            // Commande de changement de chaîne
            val content = "<parm><TvState>0</TvState><ProgramId>$formattedProgramId</ProgramId></parm>"
            val command = buildCommand(CMD_CHANGE_CHANNEL, content)
            
            val success = sendRawCommand(command)
            
            if (success) {
                // Envoyer un heartbeat après
                delay(100)
                sendRawCommand(buildCommand(CMD_HEARTBEAT, null))
            }
            
            withContext(Dispatchers.Main) {
                listener?.onCommandSent(success)
            }
            
            return@withContext success
            
        } catch (e: Exception) {
            Log.e(TAG, "Zap error", e)
            isConnected = false
            
            withContext(Dispatchers.Main) {
                listener?.onCommandSent(false)
            }
            
            return@withContext false
        }
    }
    
    /**
     * Chaîne suivante (émule appui CH+)
     * Note: Ceci n'utilise pas le ProgramId mais pourrait nécessiter un autre format
     */
    suspend fun channelUp(): Boolean {
        // Pour l'instant, on ne peut pas faire CH+ sans connaître le ProgramId suivant
        return false
    }
    
    /**
     * Chaîne précédente
     */
    suspend fun channelDown(): Boolean {
        return false
    }
    
    /**
     * Envoie un heartbeat pour maintenir la connexion
     */
    suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext false
        
        try {
            sendRawCommand(buildCommand(CMD_HEARTBEAT, null))
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
    
    /**
     * Déconnexion propre
     */
    fun disconnect() {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
        
        writer = null
        reader = null
        socket = null
        isConnected = false
    }
    
    // Méthodes obsolètes conservées pour compatibilité
    suspend fun switchToChannel(channelNumber: Int): Boolean {
        return zapToChannelById(channelNumber)
    }
    
    suspend fun sendKey(keyCode: Int): Boolean {
        Log.d(TAG, "sendKey ignored: $keyCode")
        return false // Non supporté avec ce protocole
    }
}
