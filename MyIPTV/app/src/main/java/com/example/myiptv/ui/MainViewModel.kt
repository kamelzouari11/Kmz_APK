package com.example.myiptv.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiptv.data.BrowseMode
import com.example.myiptv.data.EpgCountrySelection
import com.example.myiptv.data.EpgGuidePage
import com.example.myiptv.data.EpgSearchPage
import com.example.myiptv.data.EpgSearchRepository
import com.example.myiptv.data.EpgProgram
import com.example.myiptv.data.FavoriteGroup
import com.example.myiptv.data.MyIptvDatabase
import com.example.myiptv.data.MyIptvRepository
import com.example.myiptv.data.ProfileProtocol
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.data.XtreamProfile
import com.example.myiptv.utils.GitHubBackupClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CategoryItem(
    val id: String,
    val name: String,
    val providerOrder: Int = 0,
    val epgChannelCount: Int = 0,
)

const val ALL_COUNTRIES_CODE = "**"

data class EpgSearchUiState(
    val countries: EpgCountrySelection? = null,
    val countriesLoading: Boolean = false,
    val guideCountries: EpgCountrySelection? = null,
    val loading: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val page: EpgSearchPage? = null,
    val guide: EpgGuidePage? = null,
    val query: String = "",
)

data class MainUiState(
    val initializing: Boolean = true,
    val profile: XtreamProfile? = null,
    val profiles: List<XtreamProfile> = emptyList(),
    val countries: List<String> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),
    val visibleChannels: List<SavedChannel> = emptyList(),
    val selectedCountry: String? = null,
    val selectedCategoryId: String? = null,
    val selectedChannel: SavedChannel? = null,
    val playingChannel: SavedChannel? = null,
    val streamUrl: String? = null,
    val epg: List<EpgProgram> = emptyList(),
    val cachedEpgByStreamId: Map<Int, EpgProgram> = emptyMap(),
    /** Channels whose latest short EPG request returned at least one usable programme. */
    val epgAvailableChannels: Set<String> = emptySet(),
    val fullEpgChannel: SavedChannel? = null,
    val fullEpg: List<EpgProgram> = emptyList(),
    val fullEpgLoading: Boolean = false,
    val fullEpgError: String? = null,
    val favoriteGroups: List<FavoriteGroup> = emptyList(),
    val favoriteGroupIdsByChannel: Map<Int, Set<Long>> = emptyMap(),
    val favoriteGroupIdsForChannel: Set<Long> = emptySet(),
    val selectedFavoriteGroup: FavoriteGroup? = null,
    val browseMode: BrowseMode = BrowseMode.RECENT,
    val searchQuery: String = "",
    val epgSearchQuery: String = "",
    val epgSearchResultCount: Int = 0,
    val searchHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isFullScreen: Boolean = false,
    val message: String? = null,
)

internal fun SavedChannel.epgAvailabilityKey(): String = "$profileId:$streamId"

internal fun MainUiState.currentEpgProgramFor(
    channel: SavedChannel,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
): EpgProgram? {
    val selected = selectedChannel ?: return null
    if (selected.profileId != channel.profileId || selected.streamId != channel.streamId) return null

    val current = epg.firstOrNull { program ->
        val start = program.startEpochSeconds
        val stop = program.stopEpochSeconds
        start != null && stop != null && nowEpochSeconds >= start && nowEpochSeconds < stop
    }
    if (current != null) return current
    val hasCompleteSchedule = epg.any {
        it.startEpochSeconds != null && it.stopEpochSeconds != null
    }
    return if (hasCompleteSchedule) null else epg.firstOrNull()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MyIptvRepository(MyIptvDatabase.get(application))
    private val epgSearchRepository = EpgSearchRepository(
        application,
        MyIptvDatabase.get(application),
        repository,
    )
    private val _epgSearch = MutableStateFlow(EpgSearchUiState())
    val epgSearch: StateFlow<EpgSearchUiState> = _epgSearch.asStateFlow()
    private var pendingEpgChannel: SavedChannel? = null
    private var epgOperationJob: Job? = null
    private var lastEpgProgressUpdate = 0L
    private var visibleChannelSource = emptyList<SavedChannel>()
    private var visibleChannelLimit = 20

    fun loadMoreVisibleChannels() {
        if (visibleChannelLimit >= visibleChannelSource.size) return
        visibleChannelLimit = (visibleChannelLimit + 20).coerceAtMost(visibleChannelSource.size)
        val page = visibleChannelSource.take(visibleChannelLimit)
        _state.value = _state.value.copy(
            visibleChannels = page,
        )
        cachedVisibleEpgJob?.cancel()
        cachedVisibleEpgJob = viewModelScope.launch {
            val cached = repository.loadCachedEpg(page).mapNotNull { (streamId, programs) ->
                val now = System.currentTimeMillis() / 1_000L
                programs.firstOrNull { program ->
                    val start = program.startEpochSeconds
                    val stop = program.stopEpochSeconds
                    start != null && stop != null && now >= start && now < stop
                }?.let { streamId to it }
            }.toMap()
            if (_state.value.visibleChannels == page) {
                val latest = _state.value
                _state.value = latest.copy(
                    cachedEpgByStreamId = cached,
                    epgAvailableChannels = latest.epgAvailableChannels +
                        page.filter { it.streamId in cached }.map(SavedChannel::epgAvailabilityKey),
                )
            }
        }
    }

    private fun publishEpgStatus(status: String) {
        val now = SystemClock.uptimeMillis()
        val progressMatch = Regex("EPG court · (\\d+)/(\\d+)").find(status)
        val isFinalProgress = progressMatch?.let {
            it.groupValues[1] == it.groupValues[2]
        } == true
        if (status.startsWith("EPG court") && !isFinalProgress &&
            now - lastEpgProgressUpdate < 1_000L
        ) return
        lastEpgProgressUpdate = now
        _epgSearch.value = _epgSearch.value.copy(status = status)
    }

    fun loadEpgCountries() {
        if (_epgSearch.value.loading || _epgSearch.value.countriesLoading) return
        viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(countriesLoading = true)
            try {
                val countries = epgSearchRepository.countries()
                val previous = _epgSearch.value
                val sameSelection = previous.countries?.profileId == countries.profileId &&
                    previous.countries?.selectedCountries == countries.selectedCountries &&
                    previous.countries?.selectedCategories == countries.selectedCategories
                _epgSearch.value = previous.copy(countries = countries, countriesLoading = false,
                    guideCountries = null,
                    error = null,
                    page = if (sameSelection) previous.page else null,
                    guide = if (sameSelection) previous.guide else null)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _epgSearch.value = _epgSearch.value.copy(countriesLoading = false, countries = null,
                    error = "Impossible de lire les pays. Vérifiez le profil actif et la synchronisation de ses chaînes.")
            }
        }
    }

    fun applyEpgFilters(selectedCountries: Set<String>, selectedCategories: Set<String>) {
        val current = _epgSearch.value
        val countries = current.countries ?: return
        if (current.loading || current.countriesLoading ||
            (selectedCountries == countries.selectedCountries &&
                selectedCategories == countries.selectedCategories)
        ) return
        viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(countriesLoading = true)
            try {
                epgSearchRepository.saveFilters(countries, selectedCountries, selectedCategories)
                _epgSearch.value = _epgSearch.value.copy(
                    countries = countries.copy(
                        selectedCountries = selectedCountries.toSet(),
                        selectedCategories = selectedCategories.toSet(),
                    ),
                    countriesLoading = false,
                    guideCountries = null,
                    page = null,
                    guide = null,
                    error = null,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _epgSearch.value = _epgSearch.value.copy(countriesLoading = false,
                    error = "Impossible d’enregistrer les pays. Réessayez.")
            }
        }
    }

    fun searchEpg(query: String, refresh: Boolean = false) {
        if (_epgSearch.value.loading || _epgSearch.value.countriesLoading || _epgSearch.value.countries?.hasEpgChannels != true) return
        epgOperationJob = viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(
                loading = true,
                query = query,
                page = null,
                guide = null,
                error = null,
            )
            try {
                val page = epgSearchRepository.search(
                    query,
                    refresh,
                    _state.value.playingChannel?.let { "${it.countryCode}\u0000${it.categoryId}" },
                ) { status ->
                    publishEpgStatus(status)
                }
                storeEpgSearchResults(query, page.results.flatMap { it.channels })
                _epgSearch.value = _epgSearch.value.copy(loading = false, page = page, status = "")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _epgSearch.value = _epgSearch.value.copy(loading = false, status = "Chargement annulé")
                throw cancelled
            } catch (error: Exception) {
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    error = epgErrorMessage(error),
                    status = "",
                )
            }
        }
    }

    fun loadEpgGuideChannels(refresh: Boolean = false) {
        if (_epgSearch.value.loading || _epgSearch.value.countriesLoading) return
        epgOperationJob = viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(
                loading = true, guideCountries = null, error = null,
                status = "Vérification des programmes disponibles…",
            )
            try {
                val countries = epgSearchRepository.guideCountries(refresh) { status ->
                    publishEpgStatus(status)
                }
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false, guideCountries = countries, status = "",
                )
                refreshCachedEpgAvailability()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _epgSearch.value = _epgSearch.value.copy(loading = false, status = "Chargement annulé")
                throw cancelled
            } catch (error: Exception) {
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false, error = epgErrorMessage(error), status = "",
                )
            }
        }
    }

    fun loadEpgGuide(channel: SavedChannel, refresh: Boolean = false) {
        if (_epgSearch.value.loading || _epgSearch.value.countriesLoading) return
        epgOperationJob = viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(
                loading = true,
                page = null,
                error = null,
            )
            try {
                val selection = _epgSearch.value.countries ?: epgSearchRepository.countries()
                check(selection.hasEpgChannels) {
                    "Sélectionnez au moins un pays et une catégorie contenant des chaînes EPG."
                }
                _epgSearch.value = _epgSearch.value.copy(countries = selection)
                val guide = epgSearchRepository.guide(channel, refresh) { status ->
                    publishEpgStatus(status)
                }
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    guide = guide,
                    status = "",
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _epgSearch.value = _epgSearch.value.copy(loading = false, status = "Chargement annulé")
                throw cancelled
            } catch (error: Exception) {
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    error = epgErrorMessage(error),
                    status = "",
                )
            }
        }
    }

    fun cancelEpgLoading() {
        epgOperationJob?.cancel()
        epgOperationJob = null
    }

    fun exportEpgDatabaseToGitHub() {
        if (_epgSearch.value.loading) return
        epgOperationJob = viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(
                loading = true,
                status = "Compression et export de la base EPG…",
                error = null,
            )
            try {
                githubBackupClient.uploadEpgDatabase(epgSearchRepository.exportDatabase())
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    status = "✓ Base EPG exportée avec succès vers GitHub.",
                )
                _state.value = _state.value.copy(message = "Base EPG exportée vers GitHub.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _epgSearch.value = _epgSearch.value.copy(loading = false, status = "")
                throw cancelled
            } catch (_: Exception) {
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    status = "",
                    error = "Impossible d’exporter la base EPG vers GitHub.",
                )
            }
        }
    }

    fun importEpgDatabaseFromGitHub() {
        if (_epgSearch.value.loading) return
        epgOperationJob = viewModelScope.launch {
            _epgSearch.value = _epgSearch.value.copy(
                loading = true,
                status = "Téléchargement et validation de la base EPG…",
                error = null,
            )
            try {
                epgSearchRepository.importDatabase(githubBackupClient.downloadEpgDatabase())
                val restoredCountries = epgSearchRepository.countries()
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    status = "✓ Base EPG importée avec succès depuis GitHub.",
                    countries = restoredCountries,
                    guideCountries = null,
                    page = null,
                    guide = null,
                )
                refreshCachedEpgAvailability()
                _state.value = _state.value.copy(message = "Base EPG importée depuis GitHub.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _epgSearch.value = _epgSearch.value.copy(loading = false, status = "")
                throw cancelled
            } catch (_: Exception) {
                _epgSearch.value = _epgSearch.value.copy(
                    loading = false,
                    status = "",
                    error = "Impossible d’importer la base EPG depuis GitHub.",
                )
            }
        }
    }

    private fun epgErrorMessage(error: Exception): String {
        // Network exceptions may contain the credential-bearing URL: never display them.
        if (error !is IllegalStateException && error !is IllegalArgumentException) {
            return "Impossible de charger le guide EPG. Vérifiez la connexion et réessayez."
        }
        return when {
            error.message?.startsWith("Aucun profil IPTV actif") == true ->
                "Aucun profil IPTV actif n’est disponible."
            error.message?.startsWith("Sélectionnez") == true ->
                "Sélectionnez au moins un pays et une catégorie avec des chaînes EPG."
            error.message?.contains("exclue par la sélection") == true ->
                "Cette chaîne est exclue par les filtres EPG actuels."
            error.message?.contains("identifiant EPG") == true ->
                "Cette chaîne ne possède pas d’identifiant EPG."
            error.message?.startsWith("Synchronisez") == true ->
                "Synchronisez d’abord les chaînes du profil actif."
            else -> "Le guide reçu est vide, incomplet ou incompatible. Réessayez."
        }
    }

    fun playEpgChannel(channel: SavedChannel) {
        if (_state.value.profile?.id == channel.profileId) {
            play(channel)
        } else {
            pendingEpgChannel = channel
            viewModelScope.launch {
                try {
                    repository.activateProfile(channel.profileId)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    pendingEpgChannel = null
                    _state.value = _state.value.copy(message = "Impossible d’activer le profil IPTV sélectionné.")
                }
            }
        }
    }

    private val githubBackupClient = GitHubBackupClient()
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var allChannels: List<SavedChannel> = emptyList()
    private var recentChannels: List<SavedChannel> = emptyList()
    private var favoriteChannels: List<SavedChannel> = emptyList()
    private var epgSearchChannels: List<SavedChannel> = emptyList()
    private var cinemaSearchChannels: List<SavedChannel> = emptyList()
    private var liveCountries: List<String> = emptyList()
    private var liveCategoriesByCountry: Map<String, List<CategoryItem>> = emptyMap()
    private var liveChannelsBySelection: Map<Pair<String, String>, List<SavedChannel>> = emptyMap()
    private var favoriteChannelsJob: Job? = null
    private var favoriteMembershipsJob: Job? = null
    private var focusSelectionJob: Job? = null
    private var categorySelectionJob: Job? = null
    private var epgJob: Job? = null
    private var cachedVisibleEpgJob: Job? = null
    private var fullEpgJob: Job? = null
    private var recentChannelsLoaded = false
    private var startupRestored = false
    private var observedProfileId: Int? = null

    init {
        viewModelScope.launch {
            repository.profile.collect { profile ->
                val groupingChanged = _state.value.profile?.countryGroupingEnabled !=
                    profile?.countryGroupingEnabled
                val profileChanged = observedProfileId != profile?.id
                observedProfileId = profile?.id
                if (profileChanged) resetForProfileSwitch()
                _state.value = _state.value.copy(
                    profile = profile,
                    initializing = false,
                )
                if (profileChanged) refreshCachedEpgAvailability()
                pendingEpgChannel?.takeIf { it.profileId == profile?.id }?.let { channel ->
                    pendingEpgChannel = null
                    startupRestored = true
                    play(channel)
                }
                updateStreamUrl()
                if (!profileChanged && groupingChanged) {
                    rebuildLiveIndex()
                    rebuildHierarchy()
                }
                restoreStartupSelection()
            }
        }
        viewModelScope.launch {
            repository.profiles.collect { profiles ->
                _state.value = _state.value.copy(profiles = profiles)
            }
        }
        viewModelScope.launch {
            repository.channels.collect { channels ->
                allChannels = channels
                rebuildLiveIndex()
                rebuildHierarchy()
                refreshCachedEpgAvailability()
            }
        }
        viewModelScope.launch {
            repository.recentChannels.collect { channels ->
                recentChannels = channels
                recentChannelsLoaded = true
                if (_state.value.browseMode == BrowseMode.RECENT) rebuildHierarchy()
                restoreStartupSelection()
            }
        }
        viewModelScope.launch {
            repository.recentSearches.collect { searches ->
                _state.value = _state.value.copy(searchHistory = searches)
            }
        }
        viewModelScope.launch {
            repository.favoriteGroups.collect { groups ->
                val selected = _state.value.selectedFavoriteGroup?.let { current ->
                    groups.firstOrNull { it.id == current.id }
                }
                _state.value = _state.value.copy(
                    favoriteGroups = groups,
                    selectedFavoriteGroup = selected,
                )
                if (_state.value.browseMode == BrowseMode.FAVORITES && selected == null) {
                    showLive()
                } else if (_state.value.browseMode == BrowseMode.FAVORITES) {
                    rebuildHierarchy()
                }
            }
        }
        viewModelScope.launch {
            repository.favoriteMemberships.collect { memberships ->
                val byChannel = memberships.groupBy { it.streamId }
                    .mapValues { (_, entries) -> entries.map { it.groupId }.toSet() }
                _state.value = _state.value.copy(favoriteGroupIdsByChannel = byChannel)
            }
        }
    }

    private fun refreshCachedEpgAvailability() {
        viewModelScope.launch {
            val available = epgSearchRepository.cachedAvailableChannelKeys()
            _state.value = _state.value.copy(epgAvailableChannels = available)
        }
    }

    fun saveProfile(
        profileId: Int?,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        protocol: ProfileProtocol,
        macAddress: String?,
        countryGroupingEnabled: Boolean,
    ) {
        runLoading {
            repository.saveProfile(
                profileId,
                name,
                serverUrl,
                username,
                password,
                protocol,
                macAddress,
                countryGroupingEnabled,
            )
            _state.value = _state.value.copy(
                message = "Profil enregistré. Utilisez Sync pour charger ses données.",
            )
        }
    }

    fun activateProfile(profile: XtreamProfile) {
        if (profile.id == _state.value.profile?.id) return
        runLoading {
            repository.activateProfile(profile.id)
            _state.value = _state.value.copy(message = "Profil « ${profile.name} » activé localement.")
        }
    }

    fun deleteProfile(profile: XtreamProfile) {
        runLoading {
            repository.deleteProfile(profile)
            _state.value = _state.value.copy(message = "Profil « ${profile.name} » supprimé.")
        }
    }

    fun refresh() {
        runLoading {
            repository.refresh()
            _state.value = _state.value.copy(message = "Synchronisation du profil terminée.")
        }
    }

    fun showLive() {
        favoriteChannelsJob?.cancel()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.LIVE,
            selectedFavoriteGroup = null,
            searchQuery = "",
        )
        rebuildHierarchy()
    }

    fun showRecent() {
        favoriteChannelsJob?.cancel()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.RECENT,
            selectedFavoriteGroup = null,
            searchQuery = "",
        )
        rebuildHierarchy()
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            runCatching { repository.clearRecentHistory() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        message = "Historique des chaînes récentes effacé.",
                    )
                }
                .onFailure(::showError)
        }
    }

    fun showFavoriteGroup(group: FavoriteGroup) {
        favoriteChannelsJob?.cancel()
        favoriteChannels = emptyList()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.FAVORITES,
            selectedFavoriteGroup = group,
            searchQuery = "",
        )
        rebuildHierarchy()
        favoriteChannelsJob = viewModelScope.launch {
            repository.favoriteChannels(group.id).collect { channels ->
                favoriteChannels = channels
                rebuildHierarchy()
            }
        }
    }

    fun selectCountry(country: String) {
        categorySelectionJob?.cancel()
        if (
            _state.value.browseMode == BrowseMode.LIVE &&
            _state.value.selectedCountry == country
        ) return
        val firstCategory = liveCategories(country).firstOrNull()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.LIVE,
            selectedFavoriteGroup = null,
            searchQuery = "",
            epgSearchQuery = "",
            selectedCountry = country,
            selectedCategoryId = firstCategory?.id,
        )
        rebuildHierarchy(loadSelectedDetails = false)
    }

    fun focusCategory(category: CategoryItem) {
        categorySelectionJob?.cancel()
        if (_state.value.selectedCategoryId == category.id) return
        categorySelectionJob = viewModelScope.launch {
            delay(120)
            applyCategorySelection(category)
        }
    }

    fun selectCategory(category: CategoryItem) {
        categorySelectionJob?.cancel()
        applyCategorySelection(category)
    }

    private fun applyCategorySelection(category: CategoryItem) {
        if (_state.value.browseMode != BrowseMode.LIVE) return
        if (_state.value.selectedCategoryId == category.id) return
        _state.value = _state.value.copy(selectedCategoryId = category.id)
        rebuildHierarchy(loadSelectedDetails = false)
    }

    fun search(query: String) {
        favoriteChannelsJob?.cancel()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.SEARCH,
            selectedFavoriteGroup = null,
            searchQuery = query,
        )
        rebuildHierarchy()
    }

    fun submitSearch(query: String) {
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) return
        search(normalizedQuery)
        viewModelScope.launch {
            repository.rememberSearch(normalizedQuery)
        }
    }

    private fun storeEpgSearchResults(query: String, channels: List<SavedChannel>) {
        epgSearchChannels = channels
            .distinctBy { "${it.profileId}:${it.streamId}" }
            .sortedWith(compareBy<SavedChannel> { it.providerOrder }.thenBy { it.streamId })
        _state.value = _state.value.copy(
            epgSearchQuery = query,
            epgSearchResultCount = epgSearchChannels.size,
        )
    }

    /** Opens the temporary channel source created by the last EPG programme search. */
    fun showEpgSearchResults() {
        if (epgSearchChannels.isEmpty()) return
        favoriteChannelsJob?.cancel()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.EPG_SEARCH,
            selectedFavoriteGroup = null,
            searchQuery = "",
        )
        rebuildHierarchy()
    }

    fun showCinemaSearchResults(query: String, channels: List<SavedChannel>) {
        val selected = channels.distinctBy { "${it.profileId}:${it.streamId}" }
        if (query.isBlank() || selected.isEmpty()) return
        cinemaSearchChannels = selected
        favoriteChannelsJob?.cancel()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.CINEMA_SEARCH,
            selectedFavoriteGroup = null,
            searchQuery = query,
        )
        rebuildHierarchy()
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            runCatching { repository.clearSearchHistory() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        message = "Historique des recherches effacé.",
                    )
                }
                .onFailure(::showError)
        }
    }

    fun focusChannel(channel: SavedChannel) {
        focusSelectionJob?.cancel()
        if (_state.value.selectedChannel?.streamId == channel.streamId) return
        focusSelectionJob = viewModelScope.launch {
            delay(100)
            commitFocusedChannel(channel)
        }
    }

    fun focusChannelImmediately(channel: SavedChannel) {
        focusSelectionJob?.cancel()
        // Portrait focus events are also emitted while moving the DPAD.
        // "Immediately" means immediate local selection, never an EPG request.
        commitFocusedChannel(channel)
    }

    fun play(channel: SavedChannel) {
        focusSelectionJob?.cancel()
        val current = _state.value
        if (current.playingChannel?.profileId == channel.profileId && current.playingChannel?.streamId == channel.streamId && current.streamUrl != null) {
            _state.value = current.copy(isFullScreen = true)
            return
        }
        _state.value = _state.value.copy(
            selectedChannel = channel,
            playingChannel = channel,
            streamUrl = null,
            epg = emptyList(),
            favoriteGroupIdsForChannel = emptySet(),
        )
        viewModelScope.launch {
            runCatching {
                val profile = checkNotNull(_state.value.profile)
                repository.streamUrl(profile, channel)
            }.onSuccess { url ->
                if (_state.value.playingChannel?.streamId == channel.streamId) {
                    _state.value = _state.value.copy(streamUrl = url)
                }
            }.onFailure(::showError)
        }
        loadChannelDetailsNow(channel)
        viewModelScope.launch { repository.markRecent(channel) }
    }

    fun stopStreaming() {
        pendingEpgChannel = null
        startupRestored = true
        focusSelectionJob?.cancel()
        _state.value = _state.value.copy(
            playingChannel = null,
            streamUrl = null,
            isFullScreen = false,
            message = "Streaming arrêté",
        )
    }

    fun showFullEpg(channel: SavedChannel) {
        if (channel.epgChannelId.isNullOrBlank()) return
        fullEpgJob?.cancel()
        val before = _state.value
        val sameOpenChannel =
            before.fullEpgChannel?.profileId == channel.profileId &&
                before.fullEpgChannel?.streamId == channel.streamId
        val refresh = sameOpenChannel && !before.fullEpgLoading
        val selectedPrograms = before.epg.takeIf {
            !refresh &&
                before.selectedChannel?.profileId == channel.profileId &&
                before.selectedChannel?.streamId == channel.streamId &&
                it.isNotEmpty()
        }
        if (selectedPrograms != null) {
            _state.value = before.copy(
                fullEpgChannel = channel,
                fullEpg = selectedPrograms,
                fullEpgLoading = false,
                fullEpgError = null,
            )
            return
        }
        _state.value = _state.value.copy(
            fullEpgChannel = channel,
            fullEpg = emptyList(),
            fullEpgLoading = true,
            fullEpgError = null,
        )
        fullEpgJob = viewModelScope.launch {
            try {
                val programs = repository.loadEpg(channel, refresh = refresh)
                if (_state.value.fullEpgChannel?.streamId == channel.streamId) {
                    val current = _state.value
                    val selectedSameChannel =
                        current.selectedChannel?.profileId == channel.profileId &&
                            current.selectedChannel?.streamId == channel.streamId
                    _state.value = current.copy(
                        fullEpg = programs,
                        fullEpgLoading = false,
                        fullEpgError = if (programs.isEmpty()) {
                            "Aucun programme EPG disponible pour cette chaîne."
                        } else {
                            null
                        },
                        epg = if (selectedSameChannel) programs else current.epg,
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_state.value.fullEpgChannel?.streamId == channel.streamId) {
                    _state.value = _state.value.copy(
                        fullEpgLoading = false,
                        fullEpgError = "Impossible de charger l’EPG de cette chaîne.",
                    )
                }
            }
        }
    }

    fun dismissFullEpg() {
        fullEpgJob?.cancel()
        fullEpgJob = null
        _state.value = _state.value.copy(
            fullEpgChannel = null,
            fullEpg = emptyList(),
            fullEpgLoading = false,
            fullEpgError = null,
        )
    }

    fun playNextChannel() {
        changeChannel(step = 1)
    }

    fun playPreviousChannel() {
        changeChannel(step = -1)
    }

    fun setFullScreen(enabled: Boolean) {
        if (enabled && _state.value.streamUrl == null) return
        _state.value = _state.value.copy(isFullScreen = enabled)
    }

    private fun changeChannel(step: Int) {
        val channels = _state.value.visibleChannels
        if (channels.isEmpty()) return
        val currentId = _state.value.playingChannel?.streamId
            ?: _state.value.selectedChannel?.streamId
        val currentIndex = channels.indexOfFirst { it.streamId == currentId }
        val baseIndex = if (currentIndex >= 0) currentIndex else if (step > 0) -1 else 0
        val targetIndex = Math.floorMod(baseIndex + step, channels.size)
        play(channels[targetIndex])
    }

    fun createFavoriteGroup(name: String, addSelectedChannel: Boolean = false) {
        runLoading {
            val groupId = repository.createFavoriteGroup(name)
            if (addSelectedChannel) {
                _state.value.selectedChannel?.let { channel ->
                    repository.toggleFavorite(groupId, channel.streamId)
                }
            }
            _state.value = _state.value.copy(message = "Groupe de favoris créé.")
        }
    }

    fun renameFavoriteGroup(group: FavoriteGroup, name: String) {
        runLoading {
            repository.renameFavoriteGroup(group, name)
            _state.value = _state.value.copy(message = "Groupe de favoris renommé.")
        }
    }

    fun toggleFavorite(group: FavoriteGroup) {
        val channel = _state.value.selectedChannel ?: return
        viewModelScope.launch {
            runCatching { repository.toggleFavorite(group.id, channel.streamId) }
                .onFailure { showError(it) }
        }
    }

    fun deleteFavoriteGroup(group: FavoriteGroup) {
        runLoading {
            repository.deleteFavoriteGroup(group)
            _state.value = _state.value.copy(message = "Groupe de favoris supprimé.")
        }
    }

    fun uploadBackupToGitHub() {
        runLoading {
            val json = repository.exportBackupJson(githubBackupClient.downloadIfExists())
            githubBackupClient.upload(json)
            _state.value = _state.value.copy(
                message = "Profils et favoris sauvegardés sur GitHub.",
            )
        }
    }

    fun downloadBackupFromGitHub() {
        runLoading {
            val json = githubBackupClient.download()
            repository.importBackupJson(json)
            favoriteChannelsJob?.cancel()
            favoriteChannels = emptyList()
            _state.value = _state.value.copy(
                playingChannel = null,
                streamUrl = null,
                isFullScreen = false,
            )
            showLive()
            _state.value = _state.value.copy(
                message = "Profils GitHub fusionnés localement. Utilisez Sync pour actualiser Live.",
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun rebuildHierarchy(loadSelectedDetails: Boolean = true) {
        val current = _state.value
        val countries = liveCountries
        when (current.browseMode) {
            BrowseMode.LIVE -> {
                val country = current.selectedCountry?.takeIf(countries::contains) ?: countries.firstOrNull()
                val categories = country?.let(::liveCategories).orEmpty()
                val categoryId = current.selectedCategoryId
                    ?.takeIf { id -> categories.any { it.id == id } }
                    ?: categories.firstOrNull()?.id
                val visible = if (country != null && categoryId != null) {
                    liveChannelsBySelection[country to categoryId].orEmpty()
                } else {
                    emptyList()
                }
                applyVisible(
                    countries,
                    categories,
                    country,
                    categoryId,
                    visible,
                    loadSelectedDetails,
                )
            }
            BrowseMode.RECENT -> applyVisible(
                countries = countries,
                categories = listOf(
                    CategoryItem(
                        id = "@recent",
                        name = "Récents",
                        epgChannelCount = recentChannels.countWithEpg(),
                    ),
                ),
                country = current.selectedCountry,
                categoryId = "@recent",
                channels = recentChannels,
                loadSelectedDetails = loadSelectedDetails,
            )
            BrowseMode.FAVORITES -> applyVisible(
                countries = countries,
                categories = listOf(
                    CategoryItem(
                        id = "@favorites",
                        name = current.selectedFavoriteGroup?.name ?: "Favoris",
                        epgChannelCount = favoriteChannels.countWithEpg(),
                    ),
                ),
                country = current.selectedCountry,
                categoryId = "@favorites",
                channels = favoriteChannels,
                loadSelectedDetails = loadSelectedDetails,
            )
            BrowseMode.SEARCH -> {
                val channels = searchChannels(current.searchQuery)
                applyVisible(
                    countries = countries,
                    categories = listOf(
                        CategoryItem(
                            id = "@search",
                            name = "Recherche",
                            epgChannelCount = channels.countWithEpg(),
                        ),
                    ),
                    country = current.selectedCountry,
                    categoryId = "@search",
                    channels = channels,
                    loadSelectedDetails = loadSelectedDetails,
                )
            }
            BrowseMode.EPG_SEARCH -> applyVisible(
                countries = countries,
                categories = listOf(
                    CategoryItem(
                        id = "@epg-search",
                        name = "Résultats EPG",
                        epgChannelCount = epgSearchChannels.size,
                    ),
                ),
                country = current.selectedCountry,
                categoryId = "@epg-search",
                channels = epgSearchChannels,
                loadSelectedDetails = loadSelectedDetails,
            )
            BrowseMode.CINEMA_SEARCH -> applyVisible(
                countries = countries,
                categories = listOf(
                    CategoryItem(
                        id = "@cinema-search",
                        name = "Résultats Cinéma",
                        epgChannelCount = cinemaSearchChannels.countWithEpg(),
                    ),
                ),
                country = current.selectedCountry,
                categoryId = "@cinema-search",
                channels = cinemaSearchChannels,
                loadSelectedDetails = loadSelectedDetails,
            )
        }
    }

    private fun applyVisible(
        countries: List<String>,
        categories: List<CategoryItem>,
        country: String?,
        categoryId: String?,
        channels: List<SavedChannel>,
        loadSelectedDetails: Boolean,
    ) {
        focusSelectionJob?.cancel()
        val current = _state.value
        // A search result can be rebuilt after a profile/database update. Enforce the
        // provider's numeric channel sequence at the final UI boundary as well.
        val orderedChannels = when (current.browseMode) {
            // Les catégories et les recherches suivent toujours la séquence
            // numérique fournie par le provider. Les résultats dérivés gardent
            // également cet ordre pour rester cohérents.
            BrowseMode.LIVE, BrowseMode.FAVORITES, BrowseMode.SEARCH,
            BrowseMode.EPG_SEARCH, BrowseMode.CINEMA_SEARCH -> channels.sortedWith(
                compareBy<SavedChannel> { it.providerOrder }.thenBy { it.streamId },
            )
            else -> channels
        }
        visibleChannelSource = orderedChannels
        visibleChannelLimit = 20.coerceAtMost(orderedChannels.size)
        val pagedChannels = orderedChannels.take(visibleChannelLimit)
        val selected = current.selectedChannel
            ?.takeIf { old -> orderedChannels.any { it.streamId == old.streamId } }
            ?: orderedChannels.firstOrNull()
        val selectionChanged = selected?.streamId != current.selectedChannel?.streamId
        _state.value = current.copy(
            countries = countries,
            categories = categories,
            visibleChannels = pagedChannels,
            selectedCountry = country,
            selectedCategoryId = categoryId,
            selectedChannel = selected,
            epg = if (selectionChanged) emptyList() else current.epg,
            favoriteGroupIdsForChannel = if (selectionChanged) {
                emptySet()
            } else {
                current.favoriteGroupIdsForChannel
            },
            cachedEpgByStreamId = emptyMap(),
        )
        cachedVisibleEpgJob?.cancel()
        cachedVisibleEpgJob = viewModelScope.launch {
            val cached = repository.loadCachedEpg(pagedChannels).mapNotNull { (streamId, programs) ->
                val now = System.currentTimeMillis() / 1_000L
                programs.firstOrNull { program ->
                    val start = program.startEpochSeconds
                    val stop = program.stopEpochSeconds
                    start != null && stop != null && now >= start && now < stop
                }?.let { streamId to it }
            }.toMap()
            if (_state.value.visibleChannels == pagedChannels) {
                val latest = _state.value
                _state.value = latest.copy(
                    cachedEpgByStreamId = cached,
                    epgAvailableChannels = latest.epgAvailableChannels +
                        pagedChannels.filter { it.streamId in cached }.map(SavedChannel::epgAvailabilityKey),
                )
            }
        }
        if (selectionChanged) {
            if (selected == null) {
                cancelChannelDetails()
            } else if (!loadSelectedDetails) {
                cancelChannelDetails()
            } else {
                loadEpg(selected)
            }
        }
    }

    private fun rebuildLiveIndex() {
        if (_state.value.profile?.countryGroupingEnabled == false) {
            liveCountries = if (allChannels.isEmpty()) emptyList() else listOf(ALL_COUNTRIES_CODE)
            liveCategoriesByCountry = if (allChannels.isEmpty()) {
                emptyMap()
            } else {
                mapOf(
                    ALL_COUNTRIES_CODE to categoryItems(allChannels),
                )
            }
            liveChannelsBySelection = allChannels.groupBy {
                ALL_COUNTRIES_CODE to it.categoryId
            }
            return
        }
        val providerCountries = allChannels.map(SavedChannel::countryCode).distinct()
        liveCountries = providerCountries.filter(::startsWithLetterOrDigit) +
            providerCountries.filterNot(::startsWithLetterOrDigit)
        liveCategoriesByCountry = allChannels
            .groupBy(SavedChannel::countryCode)
            .mapValues { (_, channels) ->
                categoryItems(channels)
            }
        liveChannelsBySelection = allChannels.groupBy { it.countryCode to it.categoryId }
    }

    private fun categoryItems(channels: List<SavedChannel>): List<CategoryItem> =
        channels
            .groupBy(SavedChannel::categoryId)
            .values
            .map { categoryChannels ->
                val first = categoryChannels.first()
                CategoryItem(
                    id = first.categoryId,
                    name = first.categoryName,
                    providerOrder = first.categoryOrder,
                    epgChannelCount = categoryChannels.countWithEpg(),
                )
            }
            .sortedBy(CategoryItem::providerOrder)

    private fun List<SavedChannel>.countWithEpg(): Int =
        count { !it.epgChannelId.isNullOrBlank() }

    private fun liveCategories(country: String): List<CategoryItem> =
        liveCategoriesByCountry[country].orEmpty()

    private fun searchChannels(query: String): List<SavedChannel> {
        val terms = query.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()
        return allChannels
            .filter { channel -> terms.all { term -> channel.name.contains(term, ignoreCase = true) } }
    }

    private fun startsWithLetterOrDigit(country: String): Boolean =
        country.firstOrNull()?.isLetterOrDigit() == true

    private fun observeFavoriteMemberships(channel: SavedChannel) {
        favoriteMembershipsJob?.cancel()
        favoriteMembershipsJob = viewModelScope.launch {
            repository.favoriteGroupIds(channel.streamId).collect { groupIds ->
                val memberships = groupIds.toSet()
                val current = _state.value
                if (
                    current.selectedChannel?.streamId == channel.streamId &&
                    current.favoriteGroupIdsForChannel != memberships
                ) {
                    _state.value = current.copy(favoriteGroupIdsForChannel = memberships)
                }
            }
        }
    }

    private fun loadEpg(channel: SavedChannel, allowNetwork: Boolean = false) {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            // A focus move must remain local; network is reserved for playback.
            val cached = repository.loadCachedEpg(channel)
            if (cached.isNotEmpty() && _state.value.selectedChannel?.streamId == channel.streamId) {
                val current = _state.value
                _state.value = current.copy(
                    epg = cached,
                    epgAvailableChannels = current.epgAvailableChannels + channel.epgAvailabilityKey(),
                )
            }
            if (!allowNetwork) return@launch
            // Playback uses the normal cache path first; a forced refresh is
            // never tied to DPAD navigation.
            runCatching { repository.loadEpg(channel, refresh = false) }
                .onSuccess { programs ->
                    val current = _state.value
                    val key = channel.epgAvailabilityKey()
                    val available = if (programs.isEmpty()) {
                        current.epgAvailableChannels - key
                    } else {
                        current.epgAvailableChannels + key
                    }
                    if (
                        current.selectedChannel?.streamId == channel.streamId &&
                        (current.epg != programs || current.epgAvailableChannels != available)
                    ) {
                        _state.value = current.copy(epg = programs, epgAvailableChannels = available)
                    } else if (current.epgAvailableChannels != available) {
                        _state.value = current.copy(epgAvailableChannels = available)
                    }
                    if (programs.isNotEmpty()) {
                        val visible = _state.value.visibleChannels
                        val shared = repository.loadCachedEpg(visible)
                        val latest = _state.value
                        if (latest.visibleChannels == visible) {
                            val now = java.time.Instant.now().epochSecond
                            val currentPrograms = shared.mapNotNull { (streamId, entries) ->
                                entries.firstOrNull { entry ->
                                    val start = entry.startEpochSeconds
                                    val stop = entry.stopEpochSeconds
                                    start != null && stop != null && now >= start && now < stop
                                }?.let { streamId to it }
                            }.toMap()
                            _state.value = latest.copy(
                                cachedEpgByStreamId = currentPrograms,
                                epgAvailableChannels = latest.epgAvailableChannels +
                                    visible.filter { it.streamId in shared.keys }.map { it.epgAvailabilityKey() },
                            )
                        }
                    }
                }
        }
    }

    private fun commitFocusedChannel(channel: SavedChannel) {
        if (_state.value.selectedChannel?.streamId == channel.streamId) return
        _state.value = _state.value.copy(
            selectedChannel = channel,
            epg = emptyList(),
            favoriteGroupIdsForChannel = emptySet(),
        )
        loadEpg(channel)
    }

    private fun loadChannelDetailsNow(channel: SavedChannel) {
        cancelChannelDetails()
        observeFavoriteMemberships(channel)
        loadEpg(channel, allowNetwork = true)
    }

    private fun cancelChannelDetails() {
        favoriteMembershipsJob?.cancel()
        favoriteMembershipsJob = null
        epgJob?.cancel()
        epgJob = null
    }

    private fun updateStreamUrl() {
        val current = _state.value
        val profile = current.profile ?: return
        val channel = current.playingChannel ?: return
        viewModelScope.launch {
            runCatching { repository.streamUrl(profile, channel) }
                .onSuccess { url -> _state.value = _state.value.copy(streamUrl = url) }
                .onFailure(::showError)
        }
    }

    private fun restoreStartupSelection() {
        if (startupRestored || !recentChannelsLoaded || _state.value.profile == null) return
        startupRestored = true
        val mostRecent = recentChannels.firstOrNull()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.RECENT,
            selectedFavoriteGroup = null,
            searchQuery = "",
            selectedChannel = mostRecent,
            playingChannel = null,
            streamUrl = null,
            isFullScreen = false,
        )
        rebuildHierarchy()
    }

    private fun resetForProfileSwitch() {
        fullEpgJob?.cancel()
        fullEpgJob = null
        categorySelectionJob?.cancel()
        favoriteChannelsJob?.cancel()
        favoriteChannels = emptyList()
        epgSearchChannels = emptyList()
        allChannels = emptyList()
        recentChannels = emptyList()
        liveCountries = emptyList()
        liveCategoriesByCountry = emptyMap()
        liveChannelsBySelection = emptyMap()
        recentChannelsLoaded = false
        startupRestored = false
        cancelChannelDetails()
        _state.value = _state.value.copy(
            browseMode = BrowseMode.LIVE,
            countries = emptyList(),
            categories = emptyList(),
            visibleChannels = emptyList(),
            selectedCountry = null,
            selectedCategoryId = null,
            selectedChannel = null,
            playingChannel = null,
            streamUrl = null,
            epg = emptyList(),
            fullEpgChannel = null,
            fullEpg = emptyList(),
            fullEpgLoading = false,
            fullEpgError = null,
            favoriteGroups = emptyList(),
            favoriteGroupIdsForChannel = emptySet(),
            selectedFavoriteGroup = null,
            searchQuery = "",
            epgSearchQuery = "",
            epgSearchResultCount = 0,
            searchHistory = emptyList(),
            isFullScreen = false,
        )
    }

    private fun runLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, message = null)
            runCatching { block() }
                .onFailure(::showError)
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun showError(error: Throwable) {
        val detail = error.message?.takeIf { it.isNotBlank() }
            ?: error.cause?.message?.takeIf { it.isNotBlank() }
            ?: error::class.simpleName
        _state.value = _state.value.copy(
            message = detail ?: "Une erreur inattendue est survenue.",
        )
    }
}
