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

    // Coroutine Jobs to avoid multiple collectors
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var favoritesJob: kotlinx.coroutines.Job? = null

    init {
        observeProfiles()
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

    fun selectProfile(id: Int) {
        activeProfileId = id
        viewModelScope.launch {
            repository.selectProfile(id)

            // Auto-sync if DB is empty for this profile
            val count = repository.getChannelCount(id)
            if (count == 0) {
                val profile = profiles.find { it.id == id }
                if (profile != null) {
                    isLoading = true
                    try {
                        repository.refreshDatabase(profile)
                    } catch (e: Exception) {
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
        } finally {
            isLoading = false
        }
    }

    suspend fun exportDatabaseToJson(): String {
        return repository.exportDatabaseToJson()
    }

    suspend fun importDatabaseFromJson(json: String) {
        repository.importDatabaseFromJson(json)
    }
}
