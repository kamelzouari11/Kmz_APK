package com.kamel.ott750

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.util.Log
import android.content.SharedPreferences
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), StbController.ConnectionListener {

    private lateinit var stbController: StbController
    private lateinit var adapter: StbChannelAdapter
    
    private lateinit var tvStbInfo: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDownload: Button
    private lateinit var spinnerFilter: Spinner
    private lateinit var btnZappingMode: MaterialButton
    private lateinit var etSearch: EditText
    private lateinit var progressBar: LinearLayout
    private lateinit var tvProgress: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var btnManageFavs: Button
    private lateinit var btnSaveToStb: Button
    
    private var allChannels: List<StbChannel> = emptyList()
    private var filteredChannels: List<StbChannel> = emptyList()
    private var modifiedChannels: MutableSet<StbChannel> = mutableSetOf()
    private var isZappingMode = true
    
    private var filterOptions: MutableList<FilterOption> = mutableListOf()
    private var currentFilter: FilterOption? = null
    
    // Map des préfixes vers noms de satellites groupés
    private val satelliteNames = mutableMapOf<String, String>()
    private var lastZappedChannel: StbChannel? = null
    
    // Gestion de l'adresse IP persistante
    private lateinit var prefs: SharedPreferences
    private val PREF_NAME = "ott750_prefs"
    private val PREF_STB_IP = "stb_ip_address"
          
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialiser les préférences
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        
        stbController = StbController(this)
        stbController.listener = this
        
        setupUI()
        
        // Charger les données sauvegardées AVANT de tenter la connexion
        lifecycleScope.launch {
            val hasCache = stbController.loadChannelsFromCache(this@MainActivity)
            
            if (hasCache) {
                Log.d("MainActivity", "Cache chargé: ${stbController.channels.size} chaînes, ${stbController.favoriteGroups.size} groupes")
                
                // Si les groupes sont vides (mais qu'on a des chaînes), on force un rechargement spécifique des groupes au cas où
                if (stbController.favoriteGroups.isEmpty()) {
                     Log.w("MainActivity", "Channels loaded but 0 groups. Retrying load groups from DB...")
                     val dbMan = DatabaseManager(this@MainActivity)
                     val groups = dbMan.loadFavoriteGroups()
                     if (groups.isNotEmpty()) {
                         stbController.updateFavoriteGroups(groups)
                         Log.d("MainActivity", "Recovered ${groups.size} groups from DB")
                     }
                }
                
                // Mettre à jour l'UI avec les données du cache
                allChannels = stbController.channels
                filteredChannels = allChannels
                buildSatelliteNames()
                adapter.satelliteNames = satelliteNames
                adapter.favoriteGroups = stbController.favoriteGroups
                adapter.updateList(filteredChannels)
                buildFilterOptions()
                updateStatus()
                
                Toast.makeText(this@MainActivity, "${allChannels.size} chaînes chargées du cache", Toast.LENGTH_SHORT).show()
            }
            
            // Tenter la connexion (sans téléchargement auto si on a déjà des données)
            connectToStb()
        }
    }

    private fun setupUI() {
        tvStbInfo = findViewById(R.id.tvStbInfo)
        btnConnect = findViewById(R.id.btnConnect)
        btnDownload = findViewById(R.id.btnDownload)
        spinnerFilter = findViewById(R.id.spinnerFilter)
        btnZappingMode = findViewById(R.id.btnZappingMode)
        etSearch = findViewById(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        recyclerView = findViewById(R.id.recyclerView)
        tvStatus = findViewById(R.id.tvStatus)
        btnManageFavs = findViewById(R.id.btnManageFavs)
        btnSaveToStb = findViewById(R.id.btnSaveToStb)
        
        adapter = StbChannelAdapter(
            channels = emptyList(),
            onSelectionChanged = { updateStatus() },
            onChannelZap = { channel -> zapToChannel(channel) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Initial state update
        adapter.isZappingMode = isZappingMode
        btnZappingMode.text = if (isZappingMode) "✖" else "📺"
        
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < filterOptions.size) {
                    currentFilter = filterOptions[position]
                    applyFilters()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilters() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        btnConnect.setOnClickListener {
            if (stbController.isConnected) {
                stbController.disconnect()
                onDisconnected()
            } else {
                connectOnly()
            }
        }
        
        btnDownload.setOnClickListener {
            downloadChannels()
        }
        
        btnManageFavs.setOnClickListener { showFavoritesDialog() }
        btnSaveToStb.setOnClickListener { saveModifiedFavorites() }
        btnZappingMode.setOnClickListener { toggleZappingMode() }
    }
    
    /**
     * Connexion uniquement (sans téléchargement)
     */
    private fun connectOnly() {
        lifecycleScope.launch {
            connectToStb()
        }
    }
    
    /**
     * Connexion au STB (sans téléchargement automatique).
     * Le téléchargement est toujours manuel via le bouton Download.
     */
    private suspend fun connectToStb() {
        val savedIp = getSavedIpAddress()
        showProgress("Connexion à $savedIp...")
        
        val success = stbController.connect(savedIp)
        hideProgress()
        
        if (success) {
            saveIpAddress(savedIp)
            if (allChannels.isEmpty()) {
                Toast.makeText(this@MainActivity, "Connecté. Appuyez sur ⬇️ pour télécharger les chaînes.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Connecté (${allChannels.size} chaînes)", Toast.LENGTH_SHORT).show()
            }
        } else {
            showManualIpDialog()
        }
    }
    
    /**
     * Télécharge la liste des chaînes
     */
    private fun downloadChannels() {
        if (!stbController.isConnected) {
            Toast.makeText(this, "Connectez-vous d'abord", Toast.LENGTH_SHORT).show()
            return
        }
        
        showProgress("Téléchargement...")
        lifecycleScope.launch {
            stbController.loadChannels()
        }
    }
    
    private fun showManualIpDialog() {
        val input = EditText(this)
        val currentIp = getSavedIpAddress()
        input.hint = "192.168.1.x"
        input.setText(currentIp)
        input.setPadding(48, 32, 48, 32)
        input.setSelection(input.text.length) // Curseur à la fin
        
        AlertDialog.Builder(this)
            .setTitle("Adresse IP du récepteur")
            .setMessage("Dernière adresse: $currentIp")
            .setView(input)
            .setPositiveButton("Connecter") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) {
                    showProgress("Connexion à $ip...")
                    lifecycleScope.launch {
                        val success = stbController.connect(ip)
                        hideProgress()
                        if (success) {
                            saveIpAddress(ip)
                            Toast.makeText(this@MainActivity, "Connecté à $ip", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Échec connexion à $ip", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    /**
     * Récupère l'adresse IP sauvegardée ou l'adresse par défaut
     */
    private fun getSavedIpAddress(): String {
        return prefs.getString(PREF_STB_IP, StbController.DEFAULT_STB_IP) ?: StbController.DEFAULT_STB_IP
    }
    
    /**
     * Sauvegarde l'adresse IP pour les prochaines connexions
     */
    private fun saveIpAddress(ip: String) {
        prefs.edit().putString(PREF_STB_IP, ip).apply()
        Log.d("MainActivity", "IP sauvegardée: $ip")
    }
    
    /**
     * Groupe les satellites par 3 : SATL_1 (Sat1+2+3), SATL_2 (Sat4+5+6), etc.
     */
    private fun buildSatelliteNames() {
        satelliteNames.clear()
        val prefixes = allChannels.map { it.programId.take(7) }.distinct().sorted()
        
        prefixes.forEachIndexed { idx, prefix ->
            // Grouper par 3 : Sat 0,1,2 -> SATL_1 | Sat 3,4,5 -> SATL_2 | etc.
            val groupNum = (idx / 3) + 1
            val name = when(groupNum) {
                1 -> "Nilesat"
                2 -> "Hotbird"
                3 -> "Astra"
                else -> "Sat_$groupNum"
            }
            satelliteNames[prefix] = name
        }
    }
    
    private fun buildFilterOptions() {
        filterOptions.clear()
        
        // "Toutes les chaînes"
        filterOptions.add(FilterOption("📋 Toutes (${allChannels.size})", FilterType.ALL, null))
        
        // Satellites groupés par 3 (SATL_1, SATL_2, etc.) - non vides
        val prefixes = allChannels.map { it.programId.take(7) }.distinct().sorted()
        val satGroups = prefixes.mapIndexed { idx, prefix -> 
            Pair(prefix, (idx / 3) + 1) 
        }.groupBy { it.second }
        
        for ((groupNum, prefixList) in satGroups) {
            val prefixesInGroup = prefixList.map { it.first }
            val count = allChannels.count { ch -> prefixesInGroup.any { ch.programId.startsWith(it) } }
            if (count > 0) {
                val name = when(groupNum) {
                    1 -> "Nilesat"
                    2 -> "Hotbird"
                    3 -> "Astra"
                    else -> "Sat_$groupNum"
                }
                filterOptions.add(FilterOption("📡 $name ($count)", FilterType.SATELLITE_GROUP, prefixesInGroup))
            }
        }
        
        // Groupes de favoris (seulement les non-vides)
        // Groupes de favoris (seulement les non-vides, comme demandé)
        for (group in stbController.favoriteGroups) {
            val count = allChannels.count { it.isInFavoriteGroup(group.id) }
            if (count > 0) {
                filterOptions.add(FilterOption("★ ${group.name} ($count)", FilterType.FAVORITE, group.id))
            }
        }
        
        // Providers (seulement les non-vides)
        val providers = allChannels
            .mapNotNull { ch -> 
                val raw = ch.provider
                // On ignore complètement les "Other" ou vide car jugés inutiles
                val isOther = raw.isEmpty() || raw.equals("Other", ignoreCase = true)
                if (isOther) null else raw
            }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
        
        for ((provider, count) in providers) {
            if (count >= 5) { // Afficher seulement les providers avec au moins 5 chaînes
                filterOptions.add(FilterOption("🏢 $provider ($count)", FilterType.PROVIDER, provider))
            }
        }
        
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            filterOptions.map { it.label }
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = spinnerAdapter
    }
    
    private fun applyFilters() {
        val searchQuery = etSearch.text.toString().trim()
        
        filteredChannels = allChannels.filter { channel ->
            val matchesFilter = when (currentFilter?.type) {
                FilterType.ALL, null -> true
                FilterType.SATELLITE_GROUP -> {
                    @Suppress("UNCHECKED_CAST")
                    val prefixes = currentFilter?.value as? List<String> ?: emptyList()
                    prefixes.any { channel.programId.startsWith(it) }
                }
                FilterType.FAVORITE -> channel.isInFavoriteGroup(currentFilter?.value as? Int ?: 0)
                FilterType.PROVIDER -> channel.provider.equals(currentFilter?.value as? String ?: "", ignoreCase = true)
            }
            
            val matchesSearch = searchQuery.isEmpty() || 
                channel.name.contains(searchQuery, ignoreCase = true)
            
            matchesFilter && matchesSearch
        }.sortedBy { it.name.lowercase() }
        
        adapter.updateList(filteredChannels)
        updateStatus()
    }
    
    private fun updateStatus() {
        val selected = adapter.getSelectedChannels().size
        val total = filteredChannels.size
        val modified = modifiedChannels.size
        
        tvStatus.text = if (selected > 0) {
            "$selected sélect. / $total"
        } else {
            "$total chaîne(s)"
        }
        
        btnSaveToStb.text = if (modified > 0) "💾 Sauver ($modified)" else "💾 Sauver"
    }
    
    private fun toggleZappingMode() {
        isZappingMode = !isZappingMode
        adapter.isZappingMode = isZappingMode
        
        btnZappingMode.text = if (isZappingMode) "✖" else "📺"
        Toast.makeText(this, 
            if (isZappingMode) "Mode Zapping ON" else "Mode Zapping OFF", 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun zapToChannel(channel: StbChannel) {
        // Laisser le contrôleur gérer la reconnexion auto si besoin
        
        Toast.makeText(this, "→ ${channel.name}", Toast.LENGTH_SHORT).show()
        lastZappedChannel = channel
        
        lifecycleScope.launch {
            val success = stbController.zapToChannel(channel)
            if (!success) {
                Toast.makeText(this@MainActivity, "Échec - Reconnectez", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showFavoritesDialog() {
        val selected = adapter.getSelectedChannels()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Sélectionnez des chaînes", Toast.LENGTH_SHORT).show()
            return
        }
        
        val groups = stbController.favoriteGroups
        if (groups.isEmpty()) {
            Toast.makeText(this, "Aucun groupe favori", Toast.LENGTH_SHORT).show()
            return
        }
        
        val groupNames = groups.map { it.name }.toTypedArray()
        var selectedIndex = 0
        
        AlertDialog.Builder(this)
            .setTitle("Favoris (${selected.size} chaînes)")
            .setSingleChoiceItems(groupNames, 0) { _, which -> selectedIndex = which }
            .setPositiveButton("➕ Ajouter") { _, _ ->
                val groupId = groups[selectedIndex].id
                var count = 0
                for (ch in selected) {
                    if (!ch.isInFavoriteGroup(groupId)) {
                        ch.addToFavoriteGroup(groupId)
                        modifiedChannels.add(ch)
                        count++
                    }
                }
                adapter.clearSelection()
                adapter.notifyDataSetChanged()
                buildFilterOptions()
                Toast.makeText(this, "+$count à ${groupNames[selectedIndex]}", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
            .setNegativeButton("➖ Retirer") { _, _ ->
                val groupId = groups[selectedIndex].id
                var count = 0
                for (ch in selected) {
                    if (ch.isInFavoriteGroup(groupId)) {
                        ch.removeFromFavoriteGroup(groupId)
                        modifiedChannels.add(ch)
                        count++
                    }
                }
                adapter.clearSelection()
                adapter.notifyDataSetChanged()
                buildFilterOptions()
                Toast.makeText(this, "-$count de ${groupNames[selectedIndex]}", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
            .setNeutralButton("Annuler", null)
            .show()
    }
    
    private fun saveModifiedFavorites() {
        if (modifiedChannels.isEmpty()) {
            Toast.makeText(this, "Aucune modification", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!stbController.isConnected) {
            Toast.makeText(this, "Connectez-vous d'abord", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Sauvegarder")
            .setMessage("Envoyer ${modifiedChannels.size} modification(s)?")
            .setPositiveButton("Oui") { _, _ ->
                showProgress("Sauvegarde...")
                lifecycleScope.launch {
                    val success = stbController.saveFavorites(modifiedChannels.toList())
                    hideProgress()
                    if (success) {
                        modifiedChannels.clear()
                        updateStatus()
                        Toast.makeText(this@MainActivity, "✅ OK!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Non", null)
            .show()
    }
    
    private fun showProgress(message: String) {
        runOnUiThread {
            tvProgress.text = message
            progressBar.visibility = View.VISIBLE
        }
    }
    
    private fun hideProgress() {
        runOnUiThread {
            progressBar.visibility = View.GONE
        }
    }
    
    // === StbController.ConnectionListener ===
    
    override fun onConnected(address: String) {
        runOnUiThread {
            btnConnect.text = "Déconnecter"
            btnConnect.backgroundTintList = getColorStateList(R.color.disconnect_button)
            btnDownload.visibility = View.VISIBLE
            val info = stbController.stbInfo
            tvStbInfo.text = "📡 ${info?.productName ?: address}"
            tvStbInfo.setTextColor(getColor(R.color.status_connected))
        }
    }
    
    override fun onDisconnected() {
        runOnUiThread {
            btnConnect.text = "Connecter"
            btnConnect.backgroundTintList = getColorStateList(R.color.connect_button)
            btnDownload.visibility = View.GONE
            tvStbInfo.text = "📡 Déconnecté (${allChannels.size} en mémoire)"
            tvStbInfo.setTextColor(getColor(R.color.status_disconnected))
        }
    }
    
    override fun onError(error: String) {
        runOnUiThread {
            hideProgress()
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onProgress(message: String) {
        showProgress(message)
    }
    
    override fun onChannelsLoaded(count: Int) {
        runOnUiThread {
            hideProgress()
            allChannels = stbController.channels
            filteredChannels = allChannels
            
            buildSatelliteNames()
            adapter.satelliteNames = satelliteNames
            adapter.favoriteGroups = stbController.favoriteGroups
            adapter.updateList(filteredChannels)
            
            buildFilterOptions()
            updateStatus()
            
            Toast.makeText(this, "$count chaînes téléchargées", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onFavoritesSaved(success: Boolean) {}
    
    override fun onDestroy() {
        super.onDestroy()
        stbController.disconnect()
    }
}

enum class FilterType { ALL, SATELLITE_GROUP, FAVORITE, PROVIDER }

data class FilterOption(
    val label: String,
    val type: FilterType,
    val value: Any?
)
