package com.example.simpleiptv.ui.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.entities.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class GeneratorType {
    SEARCH,
    GLOBAL_SEARCH,
    RECENTS,
    FAVORITES,
    CATEGORY
}

enum class MediaMode {
    LIVE,
    VOD
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
            GeneratorType.RECENTS -> "Récents"
            GeneratorType.SEARCH -> "Recherche : $searchQuery"
            GeneratorType.GLOBAL_SEARCH -> "Recherche globale : $searchQuery"
            GeneratorType.FAVORITES -> favoriteLists.find { it.id == selectedFavoriteListId }?.name ?: "Favoris"
            GeneratorType.CATEGORY -> filteredCategories.find { it.category_id == selectedCategoryId }?.category_name ?: "Catégorie"
        }
    }

    var activeProfileId by mutableIntStateOf(-1)
    var selectedCategoryId by mutableStateOf<String?>(null)
    var selectedFavoriteListId by mutableIntStateOf(-1)
    var searchQuery by mutableStateOf("")
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
    var isSearchVisibleOnMobile by mutableStateOf(false)
    var isShowingChannelsPortrait by mutableStateOf(false)
    var lastGeneratorType by mutableStateOf(GeneratorType.RECENTS)

    // Dialog States
    var showProfileManager by mutableStateOf(false)
    var showAddProfileDialog by mutableStateOf(false)
    var profileToEdit by mutableStateOf<ProfileEntity?>(null)
    var showAddListDialog by mutableStateOf(false)
    var channelToFavorite by mutableStateOf<ChannelEntity?>(null)
    var showRestoreConfirmDialog by mutableStateOf(false)
    var backupJsonToRestore by mutableStateOf("")
    var syncError by mutableStateOf<String?>(null)
    var failedProfileToReload by mutableStateOf<ProfileEntity?>(null)

    // Historique des 20 dernières recherches
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set

    var loadedProfileIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    // Coroutine Jobs to avoid multiple collectors
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var favoritesJob: kotlinx.coroutines.Job? = null

    init {
        observeProfiles()
        observeLoadedProfiles()
        viewModelScope.launch { searchHistory = repository.getSearchHistory() }
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

            // Auto-sync if DB is empty for this profile (meaning no categories are loaded)
            val count = repository.getCategoryCount(id)
            if (count == 0) {
                val profile = profiles.find { it.id == id }
                if (profile != null) {
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
                }
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
                repository.getFavoriteLists(id, currentMediaMode.name).collect {
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

        channelsJob =
                viewModelScope.launch {
                    if (debounce && lastGeneratorType == GeneratorType.SEARCH) {
                        searchDebounceJob = launch {
                            kotlinx.coroutines.delay(300)
                            executeRefresh()
                        }
                    } else {
                        executeRefresh()
                    }
                }
    }

    private suspend fun executeRefresh() {
        if (lastGeneratorType == GeneratorType.GLOBAL_SEARCH) {
            // Recherche globale multi-profils
            globalSearchResults = emptyList()
            repository.searchChannelsAllProfiles(searchQuery, currentMediaMode.name)
                    .collect { globalSearchResults = it }
            return
        }
        // Reset global results when not in global search
        globalSearchResults = emptyList()
        val flow =
                when (lastGeneratorType) {
                    GeneratorType.SEARCH ->
                            repository.searchChannels(
                                    searchQuery,
                                    activeProfileId,
                                    currentMediaMode.name
                            )
                    GeneratorType.RECENTS ->
                            repository.getRecentChannels(activeProfileId, currentMediaMode.name)
                    GeneratorType.FAVORITES ->
                            repository.getChannelsByFavoriteList(
                                    selectedFavoriteListId,
                                    activeProfileId,
                                    currentMediaMode.name
                            )
                    GeneratorType.CATEGORY ->
                            repository.getChannelsByCategory(
                                    selectedCategoryId ?: "",
                                    activeProfileId,
                                    currentMediaMode.name
                            )
                    GeneratorType.GLOBAL_SEARCH -> return // handled above
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

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            searchHistory = emptyList()
        }
    }

    fun addFavoriteList(name: String) {
        viewModelScope.launch {
            repository.addFavoriteList(name, activeProfileId, currentMediaMode.name)
        }
    }

    fun removeFavoriteList(list: FavoriteListEntity) {
        viewModelScope.launch { repository.removeFavoriteList(list) }
    }

    fun addChannelToFavoriteList(streamId: String, listId: Int) {
        viewModelScope.launch {
            repository.addChannelToFavoriteList(streamId, listId, activeProfileId)
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
