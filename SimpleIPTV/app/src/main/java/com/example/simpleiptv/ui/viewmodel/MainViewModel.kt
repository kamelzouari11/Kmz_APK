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

class MainViewModel(private val repository: IptvRepository) : ViewModel() {
    // UI State
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
        if (selectedCountryFilter == "ALL") categories
        else
                categories.filter {
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

    // Coroutine Jobs to avoid multiple collectors
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var favoritesJob: kotlinx.coroutines.Job? = null

    init {
        observeProfiles()
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            repository.allProfiles.collect {
                profiles = it
                if (activeProfileId == -1 && it.isNotEmpty()) {
                    val selected = it.find { p -> p.isSelected } ?: it.first()
                    selectProfile(selected.id)
                }
            }
        }
    }

    fun selectProfile(id: Int) {
        activeProfileId = id
        viewModelScope.launch {
            repository.selectProfile(id)

            categoriesJob?.cancel()
            categoriesJob = launch {
                repository.getCategories(id).collect { cats ->
                    categories = cats
                    // Process filters off the main thread for better fluidity
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                        val groups =
                                cats
                                        .mapNotNull {
                                            if (it.category_name.length >= 3)
                                                    it.category_name.substring(0, 3).uppercase()
                                            else null
                                        }
                                        .distinct()
                        countryFilters = listOf("ALL") + groups
                    }
                }
            }

            favoritesJob?.cancel()
            favoritesJob = launch { repository.getFavoriteLists(id).collect { favoriteLists = it } }

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
                    GeneratorType.SEARCH -> repository.searchChannels(searchQuery, activeProfileId)
                    GeneratorType.RECENTS -> repository.getRecentChannels(activeProfileId)
                    GeneratorType.FAVORITES ->
                            repository.getChannelsByFavoriteList(
                                    selectedFavoriteListId,
                                    activeProfileId
                            )
                    GeneratorType.CATEGORY ->
                            repository.getChannelsByCategory(
                                    selectedCategoryId ?: "",
                                    activeProfileId
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
            repository.toggleChannelFavorite(streamId, listId, activeProfileId)
        }
    }

    fun addToRecents(streamId: String) {
        viewModelScope.launch { repository.addToRecents(streamId, activeProfileId) }
    }

    fun addFavoriteList(name: String) {
        viewModelScope.launch { repository.addFavoriteList(name, activeProfileId) }
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
