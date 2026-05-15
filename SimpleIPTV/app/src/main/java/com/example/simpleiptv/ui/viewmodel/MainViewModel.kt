package com.example.simpleiptv.ui.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.data.usecase.ChannelListUseCase
import com.example.simpleiptv.data.usecase.FavoriteUseCase
import com.example.simpleiptv.data.usecase.ProfileUseCase
import com.example.simpleiptv.data.usecase.SearchUseCase
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(private val repository: IptvRepository) : ViewModel() {

    // UseCases
    private val profileUseCase = ProfileUseCase(repository)
    private val channelListUseCase = ChannelListUseCase(repository)
    private val searchUseCase = SearchUseCase(repository)
    private val favoriteUseCase = FavoriteUseCase(repository)

    // State centralisé — accessible publiquement pour l'UI
    val uiState: MainUiState = MainUiState()

    private inline fun updateUiState(block: MainUiState.() -> Unit) {
        uiState.block()
    }

    // Coroutine Jobs to avoid multiple collectors
    private var channelsJob: kotlinx.coroutines.Job? = null
    private var categoriesJob: kotlinx.coroutines.Job? = null
    private var favoritesJob: kotlinx.coroutines.Job? = null

    init {
        observeProfiles()
        observeFavorites()
        observeLoadedProfiles()
        viewModelScope.launch { loadSearchHistory() }
    }



    // --- Observers ---

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.allFavoriteIdsFlow.distinctUntilChanged().collect {
                updateUiState {
                allFavoriteIds = it.toSet()
            }
            }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            try {
                repository.allProfiles.distinctUntilChanged().collect { profiles ->
                    updateUiState {
                this.profiles = profiles
            }
                    if (uiState.activeProfileId == -1 && profiles.isNotEmpty()) {
                        val selected = profiles.find { it.isSelected } ?: profiles.first()
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
            repository.loadedProfileIds.distinctUntilChanged().collect {
                updateUiState {
                loadedProfileIds = it.toSet()
            }
            }
        }
    }

    private suspend fun loadSearchHistory() {
        updateUiState {
                searchHistory = repository.getSearchHistory()
            }
    }

    // --- Actions: Media Mode ---

    fun setMediaMode(mode: MediaMode) {
        if (uiState.currentMediaMode == mode) return
        updateUiState {
                currentMediaMode = mode
                selectedCategoryId = null
                selectedFavoriteListId = -1
                lastGeneratorType = GeneratorType.RECENTS
                searchQuery = ""
            }
        selectProfile(uiState.activeProfileId)
    }

    // --- Actions: Profile ---

    // Court appui — toggle ON/OFF (bloqué si actif : actif => toujours ON)
    fun toggleProfileEnabled(id: Int) {
        if (id == uiState.activeProfileId) return
        val profile = uiState.profiles.find { it.id == id } ?: return
        val turningOn = !profile.isEnabled
        viewModelScope.launch {
            repository.setProfileEnabled(id, turningOn)
            // Si on active un profil non chargé, on le charge
            if (turningOn && !uiState.loadedProfileIds.contains(id)) {
                uiState.profiles.find { it.id == id }?.let { p ->
                    updateUiState { loadingProfileId = id }
                    try { repository.refreshDatabase(p) } catch (e: Exception) { android.util.Log.e("VM", "load error", e) }
                    updateUiState { loadingProfileId = -1 }
                }
            }
            // Relancer la recherche globale si active
            if (uiState.searchQuery.isNotBlank() && uiState.searchScope == SearchScope.ALL_PROFILES) {
                refreshChannels()
            }
        }
    }

    // Long appui — rend ce profil actif (master) + force ON
    fun selectProfile(id: Int) {
        updateUiState {
            activeProfileId = id
            loadingProfileId = id
        }
        viewModelScope.launch {
            val result = profileUseCase.selectProfile(id, uiState.profiles, uiState.currentMediaMode.name)
            updateUiState {
                loadingProfileId = -1
                isLoading = result.isLoading
                syncError = result.error
                failedProfileToReload = result.failedProfile
            }
            launchCategoriesCollector(id)
            launchFavoritesCollector(id)
            refreshChannels()
        }
    }

    private fun launchCategoriesCollector(profileId: Int) {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            repository.getCategories(profileId, uiState.currentMediaMode.name).distinctUntilChanged().collect { cats ->
                updateUiState { this.categories = cats }
            }
        }
    }

    private fun launchFavoritesCollector(profileId: Int) {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            repository.getAllFavoriteListsIncludingGlobal(profileId, uiState.currentMediaMode.name).distinctUntilChanged().collect { lists ->
                updateUiState {
                favoriteLists = lists
            }
            }
        }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileUseCase.deleteProfile(profile) }
    }

    fun addProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            repository.addProfile(profile)
            // Récupérer le profil nouvellement créé (avec son id auto-généré)
            val created = repository.allProfiles.first().find {
                it.profileName == profile.profileName && it.url == profile.url
            } ?: return@launch
            // Charger ses données et le mettre ON
            updateUiState { loadingProfileId = created.id }
            try { repository.refreshDatabase(created) } catch (e: Exception) { android.util.Log.e("VM", "new profile load error", e) }
            repository.setProfileEnabled(created.id, true)
            updateUiState { loadingProfileId = -1 }
        }
    }

    fun updateProfile(profile: ProfileEntity) {
        viewModelScope.launch { repository.updateProfile(profile) }
    }

    fun purgeProfiles() {
        viewModelScope.launch { profileUseCase.purgeProfiles(uiState.profiles) }
    }

    // --- Actions: Channel List Navigation ---

    fun selectCategory(categoryId: String?) {
        updateUiState {
                selectedCategoryId = categoryId
                lastGeneratorType = GeneratorType.CATEGORY
                selectedFavoriteListId = -1
                searchQuery = ""
            }
        resetPaginationAndRefresh()
    }

    fun selectFavoriteList(listId: Int) {
        updateUiState {
                selectedFavoriteListId = listId
                lastGeneratorType = GeneratorType.FAVORITES
                selectedCategoryId = null
                searchQuery = ""
            }
        resetPaginationAndRefresh()
    }

    fun showRecents() {
        updateUiState {
                lastGeneratorType = GeneratorType.RECENTS
                selectedCategoryId = null
                selectedFavoriteListId = -1
                searchQuery = ""
            }
        resetPaginationAndRefresh()
    }

    // --- Actions: Filters ---

    fun toggleSearchScope() {
        val newScope = if (uiState.searchScope == SearchScope.ALL_PROFILES) {
            SearchScope.ACTIVE_PROFILE
        } else {
            SearchScope.ALL_PROFILES
        }
        updateUiState {
                searchScope = newScope
            }
        if (uiState.searchQuery.isNotBlank()) refreshChannels()
    }

    fun toggleRecentScope() {
        val newScope = if (uiState.recentScope == SearchScope.ALL_PROFILES) {
            SearchScope.ACTIVE_PROFILE
        } else {
            SearchScope.ALL_PROFILES
        }
        updateUiState {
                recentScope = newScope
            }
        if (uiState.lastGeneratorType == GeneratorType.RECENTS) refreshChannels()
    }

    fun toggleFavoriteScope() {
        val newScope = if (uiState.favoriteScope == SearchScope.ALL_PROFILES) {
            SearchScope.ACTIVE_PROFILE
        } else {
            SearchScope.ALL_PROFILES
        }
        updateUiState {
                favoriteScope = newScope
            }
        if (uiState.lastGeneratorType == GeneratorType.FAVORITES) refreshChannels()
    }

    fun toggleFavoriteListScope() {
        val newScope = if (uiState.favoriteListScope == FavoriteListScope.ALL_LISTS) {
            FavoriteListScope.PROFILE_ONLY
        } else {
            FavoriteListScope.ALL_LISTS
        }
        updateUiState {
                favoriteListScope = newScope
            }
        if (uiState.lastGeneratorType == GeneratorType.FAVORITES) refreshChannels()
    }

    /** Réinitialise la pagination et rafraîchit la liste. */
    private fun resetPaginationAndRefresh() {
        updateUiState {
            pageOffset = 0
            hasMore = true
            isLoadingMore = false
        }
        refreshChannels()
    }

    // --- Actions: Search ---

    private var searchDebounceJob: kotlinx.coroutines.Job? = null

    fun setSearchQuery(query: String) {
        updateUiState {
                searchQuery = query
            }
        refreshChannels(debounce = true)
    }

    fun refreshChannels(debounce: Boolean = false) {
        if (uiState.activeProfileId == -1) return

        channelsJob?.cancel()
        searchDebounceJob?.cancel()

        val isSearch = uiState.searchQuery.isNotBlank() &&
            (uiState.lastGeneratorType == GeneratorType.SEARCH || uiState.lastGeneratorType == GeneratorType.GLOBAL_SEARCH)

        channelsJob = viewModelScope.launch {
            if (debounce && isSearch) {
                searchDebounceJob = launch {
                    kotlinx.coroutines.delay(400)
                    executeRefresh()
                }
            } else {
                executeRefresh()
            }
        }
    }

    private suspend fun executeRefresh() {
        if (uiState.searchQuery.isBlank()) {
            executeNormalRefresh()
            return
        }

        if (uiState.searchScope == SearchScope.ALL_PROFILES) {
            updateUiState {
                lastGeneratorType = GeneratorType.GLOBAL_SEARCH
                globalSearchResults = emptyList()
            }
            searchUseCase.searchGlobal(uiState.searchQuery, uiState.currentMediaMode.name)
                .collect { updateUiState { globalSearchResults = it } }
        } else {
            updateUiState {
                lastGeneratorType = GeneratorType.SEARCH
                globalSearchResults = emptyList()
            }
            searchUseCase.searchLocal(uiState.searchQuery, uiState.activeProfileId, uiState.currentMediaMode.name)
                .collect {
                    updateUiState { channels = it }
                }
        }
    }

    private suspend fun executeNormalRefresh() {
        updateUiState {
                globalSearchResults = emptyList()
            }

        val flow = channelListUseCase.getChannelsForType(
            type = uiState.lastGeneratorType,
            categoryId = uiState.selectedCategoryId,
            favoriteListId = uiState.selectedFavoriteListId,
            profileId = uiState.activeProfileId,
            mediaMode = uiState.currentMediaMode.name,
            recentScope = uiState.recentScope,
            favoriteScope = uiState.favoriteScope,
            favoriteLists = uiState.favoriteLists
        )

        flow.distinctUntilChanged().collect { channels ->
            // En mode pagination, on ajoute les nouveaux items aux existants
            if (uiState.pageOffset > 0 && uiState.isLoadingMore) {
                updateUiState {
                    this.channels = this.channels + channels
                    isLoadingMore = false
                    hasMore = channels.size >= uiState.pageSize
                }
            } else {
                updateUiState {
                    this.channels = channels
                }
            }
        }
    }

    /** Charge plus de chaînes pour le chargement infini. */
    fun loadMoreChannels() {
        if (!uiState.hasMore || uiState.isLoadingMore || uiState.activeProfileId == -1) return

        updateUiState {
            isLoadingMore = true
            pageOffset = pageOffset + uiState.pageSize
        }

        viewModelScope.launch {
            val flow = channelListUseCase.getChannelsForTypePaginated(
                type = uiState.lastGeneratorType,
                categoryId = uiState.selectedCategoryId,
                favoriteListId = uiState.selectedFavoriteListId,
                profileId = uiState.activeProfileId,
                mediaMode = uiState.currentMediaMode.name,
                recentScope = uiState.recentScope,
                favoriteScope = uiState.favoriteScope,
                favoriteLists = uiState.favoriteLists,
                offset = uiState.pageOffset,
                limit = uiState.pageSize,
                searchQuery = uiState.searchQuery
            )
            flow.distinctUntilChanged().collect { channels ->
                updateUiState {
                    this.channels = this.channels + channels
                    isLoadingMore = false
                    hasMore = channels.size >= uiState.pageSize
                }
            }
        }
    }

    fun commitSearchToHistory() {
        val q = uiState.searchQuery.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            repository.addToSearchHistory(q)
            loadSearchHistory()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            updateUiState {
                searchHistory = emptyList()
            }
        }
    }

    // --- Actions: Favorites ---

    fun initFavoriteAction(channel: ChannelEntity) {
        viewModelScope.launch {
            val targetLists = repository.getFavoriteLists(channel.profileId, uiState.currentMediaMode.name).first()
            updateUiState {
                dialogState = dialogState.copy(
                    channelToFavorite = channel,
                    targetFavoriteLists = targetLists
                )
            }
        }
    }

    fun addFavoriteList(name: String, isGlobal: Boolean) {
        viewModelScope.launch {
            val targetProfileId = if (isGlobal) null else uiState.activeProfileId
            repository.addFavoriteList(name, targetProfileId, uiState.currentMediaMode.name)
        }
    }

    fun requestRemoveFavoriteList(list: FavoriteListEntity) {
        updateUiState {
            dialogState = dialogState.copy(
                favoriteListToDelete = list,
                showDeleteFavoriteListConfirm = true
            )
        }
    }

    fun confirmRemoveFavoriteList() {
        val list = uiState.dialogState.favoriteListToDelete
        if (list != null) {
            removeFavoriteList(list)
        }
        hideDeleteFavoriteListConfirm()
    }

    fun hideDeleteFavoriteListConfirm() {
        updateUiState {
            dialogState = dialogState.copy(
                showDeleteFavoriteListConfirm = false,
                favoriteListToDelete = null
            )
        }
    }

    fun removeFavoriteList(list: FavoriteListEntity) {
        viewModelScope.launch { repository.removeFavoriteList(list) }
    }

    fun toggleFavorite(streamId: String, listId: Int) {
        viewModelScope.launch {
            favoriteUseCase.toggleFavorite(streamId, listId, uiState.activeProfileId, uiState.currentMediaMode.name)
        }
    }

    fun addChannelToFavoriteList(channel: ChannelEntity, listId: Int) {
        viewModelScope.launch {
            favoriteUseCase.addChannelToFavoriteList(channel, listId, channel.profileId, uiState.currentMediaMode.name)
        }
    }

    fun addToRecents(streamId: String) {
        viewModelScope.launch {
            repository.addToRecents(streamId, uiState.activeProfileId, uiState.currentMediaMode.name)
        }
    }

    fun clearRecents() {
        viewModelScope.launch {
            repository.clearRecents(uiState.activeProfileId, uiState.currentMediaMode.name)
            if (uiState.lastGeneratorType == GeneratorType.RECENTS) refreshChannels()
        }
    }

    // --- Actions: Player ---

    fun setPlayingChannel(channel: ChannelEntity?) {
        updateUiState {
                playingChannel = channel
            }
    }

    fun setFullScreenPlayer(fullScreen: Boolean) {
        updateUiState {
                isFullScreenPlayer = fullScreen
            }
    }

    // --- Actions: Dialogs ---

    fun showProfileManager() {
        updateUiState {
                dialogState = dialogState.copy(showProfileManager = true)
            }
    }

    fun hideProfileManager() {
        updateUiState {
                dialogState = dialogState.copy(showProfileManager = false)
            }
    }

    fun showAddProfileDialog(profile: ProfileEntity? = null) {
        updateUiState {
                dialogState = dialogState.copy(showAddProfileDialog = true, profileToEdit = profile)
            }
    }

    fun hideAddProfileDialog() {
        updateUiState {
                dialogState = dialogState.copy(showAddProfileDialog = false, profileToEdit = null)
            }
    }

    fun showAddListDialog() {
        updateUiState {
                dialogState = dialogState.copy(showAddListDialog = true)
            }
    }

    fun hideAddListDialog() {
        updateUiState {
                dialogState = dialogState.copy(showAddListDialog = false)
            }
    }

    fun showRestoreConfirmDialog(json: String) {
        updateUiState {
                dialogState = dialogState.copy(showRestoreConfirmDialog = true, backupJsonToRestore = json)
            }
    }

    fun hideRestoreConfirmDialog() {
        updateUiState {
                dialogState = dialogState.copy(showRestoreConfirmDialog = false, backupJsonToRestore = "")
            }
    }

    fun clearChannelToFavorite() {
        updateUiState {
                dialogState = dialogState.copy(channelToFavorite = null, targetFavoriteLists = emptyList())
            }
    }

    fun clearSyncError() {
        updateUiState {
                syncError = null
                failedProfileToReload = null
            }
    }

    // --- Actions: Backup ---

    suspend fun refreshDatabase(profile: ProfileEntity) {
        updateUiState {
                isLoading = true
            }
        try {
            profileUseCase.refreshDatabase(profile)
        } catch (e: Exception) {
            updateUiState {
                failedProfileToReload = profile
            }
        } finally {
            updateUiState {
                isLoading = false
            }
        }
    }

    suspend fun exportDatabaseToJson(): String = repository.exportDatabaseToJson()

    suspend fun importDatabaseFromJson(json: String) {
        repository.importDatabaseFromJson(json)
        loadSearchHistory()
    }
}
