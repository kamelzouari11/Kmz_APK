package com.example.simpleiptv.ui.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.entities.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class GeneratorType {
    SEARCH,
    GLOBAL_SEARCH,
    RECENTS,
    FAVORITES,
    CATEGORY
}

enum class SearchScope {
    ACTIVE_PROFILE,  // Recherche dans le profil actif uniquement
    ALL_PROFILES    // Recherche globale dans tous les profils
}

enum class MediaMode {
    LIVE,
    VOD
}

enum class FavoriteListScope {
    ALL_LISTS,      // Affiche toutes les listes (globales + profil)
    PROFILE_ONLY    // Affiche seulement les listes du profil actif
}

class MainViewModel(private val repository: IptvRepository) : ViewModel() {
    // UI State
    var currentMediaMode by mutableStateOf(MediaMode.LIVE)

    fun setMediaMode(mode: MediaMode) {
        if (currentMediaMode == mode) return
        currentMediaMode = mode
        selectedCategoryId = null
        selectedFavoriteListId = -1
        lastGeneratorType = GeneratorType.RECENTS
        searchQuery = ""
        selectProfile(activeProfileId) // Re-trigger observers for the new mode
    }
    var profiles by mutableStateOf<List<ProfileEntity>>(emptyList())
        private set
    var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
        private set
    var favoriteLists by mutableStateOf<List<FavoriteListEntity>>(emptyList())
        private set
    var channels by mutableStateOf<List<ChannelEntity>>(emptyList())
        private set

    /** Liste des favoris filtrée selon le scope d'affichage (toutes ou profil uniquement). */
    val filteredFavoriteLists by derivedStateOf {
        if (favoriteListScope == FavoriteListScope.ALL_LISTS) {
            favoriteLists
        } else {
            favoriteLists.filter { it.profileId == activeProfileId || it.profileId == null }
        }
    }
    var globalSearchResults by mutableStateOf<List<com.example.simpleiptv.data.local.ChannelWithProfile>>(emptyList())
        private set

    /**
     * Retourne toujours la dernière liste de chaînes chargée, quelle que soit la source.
     * En mode GLOBAL_SEARCH, convertit les résultats en ChannelEntity.
     * Le player utilise cette liste pour le zapping.
     */
    val lastList: List<ChannelEntity> by derivedStateOf {
        if (lastGeneratorType == GeneratorType.GLOBAL_SEARCH) {
            globalSearchResults.map { it.toChannelEntity() }
        } else {
            channels
        }
    }

    /** Label lisible pour la liste courante (affiché dans le player). */
    val lastListLabel: String by derivedStateOf {
        when (lastGeneratorType) {
            GeneratorType.RECENTS -> {
                val scopeLabel = if (recentScope == SearchScope.ALL_PROFILES) "global" else "profil"
                "Récents $scopeLabel"
            }
            GeneratorType.SEARCH, GeneratorType.GLOBAL_SEARCH -> {
                val scopeLabel = if (searchScope == SearchScope.ALL_PROFILES) "globale" else "profil"
                "Recherche $scopeLabel : $searchQuery"
            }
            GeneratorType.FAVORITES -> {
                val listName = favoriteLists.find { it.id == selectedFavoriteListId }?.name ?: "Favoris"
                val scopeLabel = if (favoriteScope == SearchScope.ALL_PROFILES || 
                    favoriteLists.find { it.id == selectedFavoriteListId }?.profileId == null) "global" else "profil"
                "$listName ($scopeLabel)"
            }
            GeneratorType.CATEGORY -> filteredCategories.find { it.category_id == selectedCategoryId }?.category_name ?: "Catégorie"
        }
    }

    var activeProfileId by mutableIntStateOf(-1)
    var selectedCategoryId by mutableStateOf<String?>(null)
    var selectedFavoriteListId by mutableIntStateOf(-1)
    var searchQuery by mutableStateOf("")
    var searchScope by mutableStateOf(SearchScope.ALL_PROFILES)  // Mode de recherche: profil actif ou tous les profils
    var recentScope by mutableStateOf(SearchScope.ALL_PROFILES)  // Mode recents: profil actif ou tous les profils
    var favoriteScope by mutableStateOf(SearchScope.ALL_PROFILES)  // Mode favoris: profil actif ou tous les profils
    var favoriteListScope by mutableStateOf(FavoriteListScope.ALL_LISTS)  // Mode affichage listes: toutes ou juste profil
    var selectedCountryFilter by mutableStateOf("ALL")

    var countryFilters by mutableStateOf<List<String>>(listOf("ALL"))
        private set

    val filteredCategories by derivedStateOf {
        val nonSeparatorCategories = categories.filter { !it.category_name.startsWith("-") }
        if (selectedCountryFilter == "ALL") nonSeparatorCategories
        else
                nonSeparatorCategories.filter {
                    it.category_name.startsWith(selectedCountryFilter, ignoreCase = true)
                }
    }

    var playingChannel by mutableStateOf<ChannelEntity?>(null)
    var isFullScreenPlayer by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    // (portrait state removed — landscape-only app)
    var lastGeneratorType by mutableStateOf(GeneratorType.RECENTS)

    // Dialog States
    var showProfileManager by mutableStateOf(false)
    var showAddProfileDialog by mutableStateOf(false)
    var profileToEdit by mutableStateOf<ProfileEntity?>(null)
    var showAddListDialog by mutableStateOf(false)
    var channelToFavorite by mutableStateOf<ChannelEntity?>(null)
    var targetFavoriteLists by mutableStateOf<List<FavoriteListEntity>>(emptyList())
        private set

    fun initFavoriteAction(channel: ChannelEntity) {
        channelToFavorite = channel
        viewModelScope.launch {
            targetFavoriteLists = repository.getFavoriteLists(channel.profileId, currentMediaMode.name).first()
        }
    }
    
    var showRestoreConfirmDialog by mutableStateOf(false)
    var backupJsonToRestore by mutableStateOf("")
    var syncError by mutableStateOf<String?>(null)
    var failedProfileToReload by mutableStateOf<ProfileEntity?>(null)

    // Historique des 20 dernières recherches
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set

    var loadedProfileIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    var allFavoriteIds by mutableStateOf<Set<String>>(emptySet())
        private set

    // Coroutine Jobs to avoid multiple collectors
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var favoritesJob: kotlinx.coroutines.Job? = null

    init {
        observeProfiles()
        observeLoadedProfiles()
        observeFavorites()
        viewModelScope.launch { searchHistory = repository.getSearchHistory() }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.allFavoriteIdsFlow.collect {
                allFavoriteIds = it.toSet()
            }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            try {
                repository.allProfiles.collect {
                    profiles = it
                    if (activeProfileId == -1 && it.isNotEmpty()) {
                        val selected = it.find { p -> p.isSelected } ?: it.first()
                        selectProfile(selected.id)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Error observing profiles", e)
            }
        }
    }

    private fun observeLoadedProfiles() {
        viewModelScope.launch {
            repository.loadedProfileIds.collect {
                loadedProfileIds = it.toSet()
            }
        }
    }

    fun selectProfile(id: Int) {
        activeProfileId = id
        viewModelScope.launch {
            repository.selectProfile(id)

            // Auto-sync if DB is empty for this profile AND has valid URL
            val count = repository.getCategoryCount(id)
            val profile = profiles.find { it.id == id }
            val hasValidUrl = profile?.url?.isNotBlank() == true
            
            if (count == 0 && hasValidUrl) {
                isLoading = true
                try {
                    repository.refreshDatabase(profile)
                } catch (e: Exception) {
                    failedProfileToReload = profile
                    syncError =
                            "Erreur d'importation : ${e.localizedMessage ?: "Erreur inconnue"}"
                } finally {
                    isLoading = false
                }
            } else if (count == 0 && !hasValidUrl) {
                // Skip sync for profiles without URL (empty default profile)
                android.util.Log.w("MainViewModel", "Skipping sync for profile without URL: ${profile?.profileName}")
            }

            categoriesJob?.cancel()
            categoriesJob = launch {
                repository.getCategories(id, currentMediaMode.name).collect { cats ->
                    categories = cats
                    // Process filters off the main thread for better fluidity
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                        val groups =
                                cats
                                        .mapNotNull {
                                            val name = it.category_name.trim()
                                            if (name.startsWith("-") || name.isEmpty())
                                                    return@mapNotNull null

                                            val spaceIndex = name.indexOf(' ')
                                            val length =
                                                    if (spaceIndex in 1..4) spaceIndex
                                                    else minOf(4, name.length)
                                            if (length > 0) name.substring(0, length).uppercase()
                                            else null
                                        }
                                        .distinct()
                                        .filter {
                                            it != "ALL"
                                        } // avoid duplicate with the prepended "ALL"
                        countryFilters = listOf("ALL") + groups
                    }
                }
            }

            favoritesJob?.cancel()
            favoritesJob = launch {
                repository.getAllFavoriteListsIncludingGlobal(id, currentMediaMode.name).collect {
                    favoriteLists = it
                }
            }

            lastGeneratorType = GeneratorType.RECENTS
            selectedCountryFilter = "ALL"
            refreshChannels()
        }
    }

    private var searchDebounceJob: kotlinx.coroutines.Job? = null

    fun refreshChannels(debounce: Boolean = false) {
        if (activeProfileId == -1) return

        channelsJob?.cancel()
        searchDebounceJob?.cancel()

        // Toujours utiliser debounce pour la recherche (plus fluide)
        val isSearch = searchQuery.isNotBlank() && 
            (lastGeneratorType == GeneratorType.SEARCH || lastGeneratorType == GeneratorType.GLOBAL_SEARCH)

        channelsJob =
                viewModelScope.launch {
                    if (debounce && isSearch) {
                        searchDebounceJob = launch {
                            kotlinx.coroutines.delay(400) // 400ms debounce pour fluidité
                            executeRefresh()
                        }
                    } else {
                        executeRefresh()
                    }
                }
    }

    private suspend fun executeRefresh() {
        android.util.Log.d("MainViewModel", "execute: type=$lastGeneratorType, query='$searchQuery', scope=$searchScope")
        
        // Si pas de query, aller chercher les chaines selon le type (RECENTS, CATEGORY, FAVORITES)
        if (searchQuery.isBlank()) {
            executeNormalRefresh()
            return
        }
        
        // Recherche : globale ou locale selon searchScope
        if (searchScope == SearchScope.ALL_PROFILES) {
            // Recherche globale - tous profils
            android.util.Log.d("MainViewModel", "Global search: query='$searchQuery'")
            lastGeneratorType = GeneratorType.GLOBAL_SEARCH
            globalSearchResults = emptyList()
            repository.searchChannelsAllProfiles(searchQuery, currentMediaMode.name)
                    .collect { 
                        android.util.Log.d("MainViewModel", "Global results: ${it.size}")
                        globalSearchResults = it 
                    }
        } else {
            // Recherche locale - profil actif seulement
            android.util.Log.d("MainViewModel", "Local search: query='$searchQuery', profile=$activeProfileId")
            lastGeneratorType = GeneratorType.SEARCH
            globalSearchResults = emptyList()
            repository.searchChannels(searchQuery, activeProfileId, currentMediaMode.name)
                    .collect { 
                        android.util.Log.d("MainViewModel", "Local results: ${it.size}")
                        channels = it 
                    }
        }
    }

    private suspend fun executeNormalRefresh() {
        // Pas de recherche - afficher selon le type de générateur
        globalSearchResults = emptyList()
        
        val recentFlow = if (recentScope == SearchScope.ALL_PROFILES) {
            repository.getAllRecentChannels(currentMediaMode.name)
        } else {
            repository.getRecentChannels(activeProfileId, currentMediaMode.name)
        }
        
val flow =
                when (lastGeneratorType) {
                    GeneratorType.RECENTS -> recentFlow
                    GeneratorType.CATEGORY -> {
                        repository.getChannelsByCategory(
                            selectedCategoryId ?: "",
                            activeProfileId,
                            currentMediaMode.name
                        )
                    }
                    GeneratorType.FAVORITES -> {
                        // Vérifier si la liste est multi-profils (profileId null) ou spécifique à un profil
                        val selectedList = favoriteLists.find { it.id == selectedFavoriteListId }
                        val isGlobalList = selectedList?.profileId == null
                        
                        // Si liste globale ou scope = tous profils, utiliser getAllProfileChannelsByFavoriteList
                        if (isGlobalList || favoriteScope == SearchScope.ALL_PROFILES) {
                            repository.getAllProfileChannelsByFavoriteList(
                                    selectedFavoriteListId,
                                    currentMediaMode.name
                            )
                        } else {
                            // Liste spécifique au profil
                            repository.getChannelsByFavoriteList(
                                    selectedFavoriteListId,
                                    activeProfileId,
                                    currentMediaMode.name
                            )
                        }
                    }
                    else -> repository.getRecentChannels(activeProfileId, currentMediaMode.name)
                }
        flow.collect { channels = it }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch { repository.deleteProfile(profile) }
    }

    fun addProfile(profile: ProfileEntity) {
        viewModelScope.launch { repository.addProfile(profile) }
    }

    fun updateProfile(profile: ProfileEntity) {
        viewModelScope.launch { repository.updateProfile(profile) }
    }

    fun toggleFavorite(streamId: String, listId: Int) {
        viewModelScope.launch {
            repository.toggleChannelFavorite(
                    streamId,
                    listId,
                    activeProfileId,
                    currentMediaMode.name
            )
        }
    }

    fun addToRecents(streamId: String) {
        viewModelScope.launch {
            repository.addToRecents(streamId, activeProfileId, currentMediaMode.name)
        }
    }

    fun clearRecents() {
        viewModelScope.launch {
            repository.clearRecents(activeProfileId, currentMediaMode.name)
            if (lastGeneratorType == GeneratorType.RECENTS) {
                refreshChannels()
            }
        }
    }

    /** Sauvegarde la requête courante dans l'historique (appelé lors de la validation). */
    fun commitSearchToHistory() {
        val q = searchQuery.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            repository.addToSearchHistory(q)
            searchHistory = repository.getSearchHistory()
        }
    }

    /** Change le scope de recherche entre profil actif et tous les profils. */
    fun toggleSearchScope() {
        searchScope = if (searchScope == SearchScope.ALL_PROFILES) {
            SearchScope.ACTIVE_PROFILE
        } else {
            SearchScope.ALL_PROFILES
        }
        // Relancer la recherche avec le nouveau scope
        if (searchQuery.isNotBlank()) {
            refreshChannels()
        }
    }

    /** Change le scope des recents entre profil actif et tous les profils. */
    fun toggleRecentScope() {
        recentScope = if (recentScope == SearchScope.ALL_PROFILES) {
            SearchScope.ACTIVE_PROFILE
        } else {
            SearchScope.ALL_PROFILES
        }
        // Relancer l'affichage des recents
        if (lastGeneratorType == GeneratorType.RECENTS) {
            refreshChannels()
        }
    }

    /** Change le scope des favoris entre profil actif et tous les profils. */
    fun toggleFavoriteScope() {
        favoriteScope = if (favoriteScope == SearchScope.ALL_PROFILES) {
            SearchScope.ACTIVE_PROFILE
        } else {
            SearchScope.ALL_PROFILES
        }
        // Relancer l'affichage des favoris
        if (lastGeneratorType == GeneratorType.FAVORITES) {
            refreshChannels()
        }
    }

    /** Change le scope d'affichage des listes de favoris entre toutes les listes et profil actif uniquement. */
    fun toggleFavoriteListScope() {
        favoriteListScope = if (favoriteListScope == FavoriteListScope.ALL_LISTS) {
            FavoriteListScope.PROFILE_ONLY
        } else {
            FavoriteListScope.ALL_LISTS
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            searchHistory = emptyList()
        }
    }

    fun addFavoriteList(name: String, isGlobal: Boolean = false) {
        viewModelScope.launch {
            val targetProfileId = if (isGlobal) null else activeProfileId
            repository.addFavoriteList(name, targetProfileId, currentMediaMode.name)
        }
    }

    fun removeFavoriteList(list: FavoriteListEntity) {
        viewModelScope.launch { repository.removeFavoriteList(list) }
    }

    fun addChannelToFavoriteList(channel: ChannelEntity, listId: Int) {
        viewModelScope.launch {
            repository.addChannelToFavoriteList(channel.stream_id, listId, channel.profileId, currentMediaMode.name)
        }
    }

    fun purgeProfiles() {
        viewModelScope.launch {
            val toDelete = mutableListOf<ProfileEntity>()
            val seenXtream = mutableSetOf<String>()
            val seenStalker = mutableSetOf<String>()

            profiles.forEach { profile ->
                val key =
                        if (profile.type == "xtream") {
                            "${profile.url}|${profile.username}|${profile.password}"
                        } else {
                            "${profile.url}|${profile.macAddress}"
                        }

                val seenSet = if (profile.type == "xtream") seenXtream else seenStalker

                if (seenSet.contains(key)) {
                    toDelete.add(profile)
                } else {
                    seenSet.add(key)
                }
            }

            toDelete.forEach { repository.deleteProfile(it) }
        }
    }

    suspend fun refreshDatabase(profile: ProfileEntity) {
        isLoading = true
        try {
            repository.refreshDatabase(profile)
        } catch (e: Exception) {
            failedProfileToReload = profile
        } finally {
            isLoading = false
        }
    }

    suspend fun exportDatabaseToJson(): String {
        return repository.exportDatabaseToJson()
    }

    suspend fun importDatabaseFromJson(json: String) {
        repository.importDatabaseFromJson(json)
        searchHistory = repository.getSearchHistory()
    }
}
