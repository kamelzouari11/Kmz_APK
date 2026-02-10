package com.kamel.ott750

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Classe pour mapper les chaînes à leurs providers/packages
 * Utilise:
 * 1. Un fichier JSON de lookup par nom de chaîne (channel_providers.json)
 * 2. Un fichier CSV de mapping par fréquence (provider_mapping.csv)
 * 3. Des heuristiques basées sur le nom de la chaîne
 */
class ProviderMapper private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ProviderMapper"
        private const val MAPPING_FILE = "provider_mapping.csv"
        private const val LOOKUP_FILE = "channel_providers.json"

        @Volatile
        private var INSTANCE: ProviderMapper? = null

        fun getInstance(context: Context): ProviderMapper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProviderMapper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // DatabaseLoader pour charger depuis database.db enrichi
    private val dbLoader = DatabaseLoader(context)
    
    // Lookup par nom de chaîne (normalisé) -> provider (depuis database.db)
    private var databaseProviderLookup: Map<String, String> = emptyMap()
    
    // Lookup par nom de chaîne (normalisé) -> provider (depuis JSON assets)
    private var channelProviderLookup: Map<String, String> = emptyMap()
    
    // Structure pour stocker un mapping de provider par fréquence
    data class ProviderEntry(
        val satellite: String,
        val position: String,
        val freqMin: Int,
        val freqMax: Int,
        val pol: String,
        val provider: String,
        val packageName: String
    )
    
    // Liste des mappings par fréquence chargés depuis le CSV
    private var providerMappings: List<ProviderEntry> = emptyList()
    
    // Cache des providers
    private val providerCache = mutableMapOf<String, String>()
    
    // Patterns de noms pour détection heuristique
    private val namePatterns = listOf(
        Pair(Regex("(?i)bein|be in"), "beIN Sports"),
        Pair(Regex("(?i)movistar|m\\+|mplus"), "Movistar+"),
        Pair(Regex("(?i)canal\\+|canal plus|canalsat"), "Canal+"),
        Pair(Regex("(?i)sky( |$)|sky(sport|news|uno|atlantic)"), "Sky"),
        Pair(Regex("(?i)mbc( |[0-9]|$)"), "MBC"),
        Pair(Regex("(?i)rotana"), "Rotana"),
        Pair(Regex("(?i)osn"), "OSN"),
        Pair(Regex("(?i)al ?jazeera|aljazeera"), "Al Jazeera"),
        Pair(Regex("(?i)france ?[0-9]|tf1|m6|arte"), "France TV"),
        Pair(Regex("(?i)^rai |rai ?[0-9]"), "RAI"),
        Pair(Regex("(?i)nova( |$)"), "Nova"),
        Pair(Regex("(?i)trt|show ?tv|kanal ?d|atv"), "Turkish"),
        Pair(Regex("(?i)polsat|tvp|tvn"), "NC+/Cyfra+"),
        Pair(Regex("(?i)zdf|ard|rtl|sat\\.?1|pro ?7"), "German FTA"),
        Pair(Regex("(?i)nile ?sat|nile"), "Nilesat"),
        Pair(Regex("(?i)cbc|msr|dmc"), "Egyptian")
    )
    
    init {
        loadDatabaseLookup()  // Priorité: database.db enrichi
        loadChannelLookup()   // Fallback: JSON assets
        loadFrequencyMappings()
    }
    
    /**
     * Charge les providers depuis database.db enrichi (Downloads/)
     * C'est la source prioritaire car elle contient les données exactes
     */
    private fun loadDatabaseLookup() {
        try {
            if (!dbLoader.isDatabaseAvailable()) {
                Log.w(TAG, "database.db not found in Downloads, will use fallback")
                return
            }
            
            val channels = dbLoader.loadChannels()
            val lookup = mutableMapOf<String, String>()
            
            for (channel in channels) {
                if (channel.provider.isNotBlank() && channel.provider != "Other") {
                    // Normaliser le nom pour le matching
                    val normalizedName = channel.name.lowercase().trim().replace(Regex("\\s+"), " ")
                    lookup[normalizedName] = channel.provider
                }
            }
            
            databaseProviderLookup = lookup
            Log.d(TAG, "Loaded ${lookup.size} providers from database.db")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading database lookup: ${e.message}")
            databaseProviderLookup = emptyMap()
        }
    }
    
    /**
     * Charge le fichier JSON de lookup par nom de chaîne (fallback)
     */
    private fun loadChannelLookup() {
        try {
            context.assets.open(LOOKUP_FILE).use { inputStream ->
                val json = inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(json)
                val lookup = mutableMapOf<String, String>()
                
                for (key in jsonObject.keys()) {
                    lookup[key] = jsonObject.getString(key)
                }
                
                channelProviderLookup = lookup
                Log.d(TAG, "Loaded ${lookup.size} channel-provider lookups from JSON")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channel lookup: ${e.message}")
            channelProviderLookup = emptyMap()
        }
    }
    
    /**
     * Charge les mappings par fréquence depuis le fichier CSV
     */
    private fun loadFrequencyMappings() {
        try {
            val mappings = mutableListOf<ProviderEntry>()
            
            context.assets.open(MAPPING_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Skip header
                    
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.split(",")
                        if (parts.size >= 7) {
                            mappings.add(ProviderEntry(
                                satellite = parts[0].trim(),
                                position = parts[1].trim(),
                                freqMin = parts[2].trim().toIntOrNull() ?: 0,
                                freqMax = parts[3].trim().toIntOrNull() ?: 0,
                                pol = parts[4].trim(),
                                provider = parts[5].trim(),
                                packageName = parts[6].trim()
                            ))
                        }
                    }
                }
            }
            
            providerMappings = mappings
            Log.d(TAG, "Loaded ${mappings.size} frequency mappings")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading frequency mappings: ${e.message}")
            loadDefaultMappings()
        }
    }
    
    /**
     * Mappings par défaut si les fichiers ne sont pas disponibles
     */
    private fun loadDefaultMappings() {
        providerMappings = listOf(
            ProviderEntry("Astra1", "19.2E", 10700, 10900, "", "Movistar+", "Movistar Plus"),
            ProviderEntry("Astra1", "19.2E", 10900, 11100, "", "Canal+", "Canal+ France"),
            ProviderEntry("Astra1", "19.2E", 11100, 11700, "", "Sky Deutschland", "Sky DE"),
            ProviderEntry("Hotbird", "13E", 10700, 10900, "", "Sky Italia", "Sky IT"),
            ProviderEntry("Hotbird", "13E", 10900, 11200, "", "NC+/Cyfra+", "Polish"),
            ProviderEntry("Hotbird", "13E", 11200, 11600, "", "beIN Sports", "beIN MENA"),
            ProviderEntry("Nilesat", "7W", 10700, 11000, "", "Nilesat", "Egyptian"),
            ProviderEntry("Nilesat", "7W", 11000, 11400, "", "beIN Sports MENA", "beIN Arabic"),
            ProviderEntry("Nilesat", "7W", 11400, 12000, "", "MBC", "MBC Group")
        )
        Log.d(TAG, "Using ${providerMappings.size} default mappings")
    }
    
    /**
     * Trouve le provider pour une chaîne par son nom
     * Priorité: 1) database.db enrichi, 2) lookup JSON, 3) heuristiques par nom
     */
    fun getProviderByName(channelName: String): String {
        if (channelName.isBlank()) return ""
        
        // Vérifier le cache
        providerCache[channelName]?.let { return it }
        
        val normalizedName = channelName.lowercase().trim().replace(Regex("\\s+"), " ")
        
        // 1. Chercher dans database.db enrichi (source prioritaire)
        databaseProviderLookup[normalizedName]?.let { provider ->
            providerCache[channelName] = provider
            return provider
        }
        
        // 2. Chercher dans le lookup JSON (fallback)
        channelProviderLookup[normalizedName]?.let { provider ->
            providerCache[channelName] = provider
            return provider
        }
        
        // 3. Chercher par heuristiques (patterns dans le nom)
        for ((pattern, provider) in namePatterns) {
            if (pattern.containsMatchIn(channelName)) {
                providerCache[channelName] = provider
                return provider
            }
        }
        
        // Pas trouvé
        return ""
    }
    
    /**
     * Trouve le provider pour une fréquence et un satellite donnés
     */
    fun getProviderByFrequency(satellite: String, frequency: Int): String {
        if (frequency <= 0) return ""
        
        val cacheKey = "${satellite}_$frequency"
        providerCache[cacheKey]?.let { return it }
        
        val normalizedSat = normalizeSatelliteName(satellite)
        
        for (entry in providerMappings) {
            if (entry.satellite.equals(normalizedSat, ignoreCase = true)) {
                if (frequency >= entry.freqMin && frequency <= entry.freqMax) {
                    providerCache[cacheKey] = entry.provider
                    return entry.provider
                }
            }
        }
        
        return ""
    }
    
    /**
     * Normalise le nom du satellite pour le matching
     */
    private fun normalizeSatelliteName(name: String): String {
        return when {
            name.contains("Astra", ignoreCase = true) -> "Astra1"
            name.contains("Hotbird", ignoreCase = true) || name.contains("Hot Bird", ignoreCase = true) -> "Hotbird"
            name.contains("Nilesat", ignoreCase = true) -> "Nilesat"
            else -> name
        }
    }
    
    /**
     * Retourne la liste de tous les providers disponibles (pour le filtre UI)
     */
    fun getAllProviders(): List<String> {
        val fromDatabase = databaseProviderLookup.values.toSet()
        val fromMappings = providerMappings.map { it.provider }
        val fromPatterns = namePatterns.map { it.second }
        return (fromDatabase + fromMappings + fromPatterns).distinct().sorted()
    }
    
    /**
     * Nombre de chaînes dans le lookup database.db
     */
    fun getDatabaseLookupSize(): Int = databaseProviderLookup.size
    
    /**
     * Nombre de chaînes dans le lookup JSON
     */
    fun getLookupSize(): Int = channelProviderLookup.size
    
    /**
     * Vérifie si database.db est disponible
     */
    fun isDatabaseAvailable(): Boolean = dbLoader.isDatabaseAvailable()
    
    /**
     * Retourne les infos de debug sur les sources de données
     */
    fun getDebugInfo(): String {
        return "DB: ${databaseProviderLookup.size} | JSON: ${channelProviderLookup.size} | Freq: ${providerMappings.size}"
    }
}
