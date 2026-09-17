package com.football.footballapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.football.footballapp.data.ApiFootballCountryDto
import com.football.footballapp.data.ApiFootballLeagueEntryDto
import com.football.footballapp.data.FiltersStore
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchStatus
import com.football.footballapp.data.model.Team
import com.football.footballapp.data.model.TopDivisionCatalog
import com.football.footballapp.data.model.favoriteIdentitySignature
import com.football.footballapp.data.model.identityNames
import com.football.footballapp.data.model.isReserveOrYouthTeam
import com.football.footballapp.data.model.isReserveOrYouthTeamName
import com.football.footballapp.data.model.isStructuredFavoriteIdentity
import com.football.footballapp.data.model.isWomenCompetitionName
import com.football.footballapp.data.model.isWomenTeam
import com.football.footballapp.data.model.isWomenTeamName
import com.football.footballapp.data.model.localCalendarDate
import com.football.footballapp.data.model.matchesFavorite
import com.football.footballapp.repository.MatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class MatchUiState(
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val matches: List<Match> = emptyList(),
    val liveOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val groupByCompetition: Boolean = false,
    val favoriteTeamKeys: Set<String> = emptySet(),
    val countrySelectionConfigured: Boolean = false,
    val settingsCountries: Set<String> = emptySet(),
    val settingsLeaguesByCountry: Map<String, Set<String>> = emptyMap(),
    val topDivisionCatalogsByCountry: Map<String, TopDivisionCatalog> = emptyMap(),
    val flagFilters: Set<String> = emptySet(),
    val error: String? = null,
    val filteredMatches: List<Match> = emptyList(),
    val cachedSearchMatches: List<Match> = emptyList(),
    val isSearchLoading: Boolean = false
) {
    fun recompute(): MatchUiState {
        val visibleMatches = matches.filterNot { it.isWomenMatch() }
        // Un pays retiré des réglages ne doit jamais rester actif à cause d'un
        // ancien flag mémorisé. Les drapeaux sont un sous-ensemble des pays ON.
        val activeCountries = flagFilters.intersect(settingsCountries)
        val eligibleEliteTeamNames = activeCountries
            .mapNotNull { country ->
                topDivisionCatalogsByCountry[country]
            }
            .flatMap { it.teamNames }
            .filterNot { name ->
                isReserveOrYouthTeamName(name) || isWomenTeamName(name)
            }
            .toSet()
        val bySelectedFilters = visibleMatches.filter { match ->
            val competitionCountry = match.effectiveCompetitionCountry()
            val selectedCompetition = match.isSelectedCompetition(
                activeCountries = activeCountries,
                leaguesByCountry = settingsLeaguesByCountry
            )
            val hasReserveOrYouthTeam =
                match.homeTeam.isReserveOrYouthTeam() || match.awayTeam.isReserveOrYouthTeam()
            val selectedTopDivisionClub =
                match.homeTeam.identityNames().any(eligibleEliteTeamNames::contains) ||
                    match.awayTeam.identityNames().any(eligibleEliteTeamNames::contains)
            val normalizedCompetition = normalizeCompetition(match.competitionName)
            val isClubFriendly = "friendl" in normalizedCompetition &&
                normalizedCompetition.friendlyCategory() == "clubs"
            selectedCompetition && !hasReserveOrYouthTeam &&
                ((competitionCountry == "World" && !isClubFriendly) || selectedTopDivisionClub)
        }
        val bySelectedMode = if (favoritesOnly) {
            // Le mode Favoris affine la sélection courante ; il ne doit pas
            // réintroduire les matchs provenant de pays désactivés.
            bySelectedFilters.filter { match ->
                match.homeTeam.matchesFavorite(favoriteTeamKeys, match.source) ||
                    match.awayTeam.matchesFavorite(favoriteTeamKeys, match.source)
            }
        } else {
            bySelectedFilters
        }
        val shouldApplyLiveOnly = liveOnly && !date.isAfter(LocalDate.now())
        val byLive = if (shouldApplyLiveOnly) {
            bySelectedMode.filter {
                it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME
            }
        } else bySelectedMode
        return copy(filteredMatches = byLive.sortedBy { it.utcDate })
    }
}

private fun Match.isSelectedCompetition(
    activeCountries: Set<String>,
    leaguesByCountry: Map<String, Set<String>>
): Boolean {
    val country = effectiveCompetitionCountry() ?: return false
    if (country !in activeCountries) return false

    val selectedLeagues = leaguesByCountry[country].orEmpty()
    if (selectedLeagues.isEmpty()) return false
    val rawPrefix = competitionName.substringBefore(" - ", missingDelimiterValue = "")
    val normalizedPrefix = normalizeCompetition(rawPrefix)
    val geographicPrefixes = setOf(
        normalizeCompetition(country),
        normalizeCompetition(competitionCountry.orEmpty()),
        "world", "international", "europe", "africa", "asia", "oceania",
        "north america", "south america"
    )
    val rawName = if (
        rawPrefix.isNotBlank() && normalizedPrefix in geographicPrefixes
    ) {
        competitionName.substringAfter(" - ")
    } else {
        competitionName
    }
    val name = normalizeCompetition(rawName)

    return selectedLeagues.any { selected ->
        val fullLeague = normalizeCompetition(selected)
        val selectedRawPrefix = selected.substringBefore(" - ", missingDelimiterValue = "")
        val selectedPrefix = normalizeCompetition(selectedRawPrefix)
        val league = if (
            selectedRawPrefix.isNotBlank() && selectedPrefix in geographicPrefixes
        ) {
            normalizeCompetition(selected.substringAfter(" - "))
        } else {
            fullLeague
        }
        competitionNamesMatch(name, league)
    }
}

private fun Match.effectiveCompetitionCountry(): String? = when {
    competitionName.contains("friendl", ignoreCase = true) -> "World"
    competitionName.contains("uefa", ignoreCase = true) -> "World"
    competitionName.contains("fifa", ignoreCase = true) -> "World"
    else -> competitionCountry
}

private fun competitionNamesMatch(name: String, selected: String): Boolean {
    if (name == selected) return true
    if (name.removePrefix("fifa ") == selected.removePrefix("fifa ")) return true

    val nameIsFriendly = "friendl" in name
    val selectedIsFriendly = "friendl" in selected
    if (nameIsFriendly && selectedIsFriendly) {
        return name.friendlyCategory() == selected.friendlyCategory()
    }

    val conferenceAliases = setOf(
        "uefa conference league",
        "uefa europa conference league"
    )
    return name in conferenceAliases && selected in conferenceAliases
}

private fun Set<String>.matchesCompetition(competitionName: String): Boolean {
    val name = normalizeCompetition(competitionName)
    return any { selected -> competitionNamesMatch(name, normalizeCompetition(selected)) }
}

private fun String.hasWomenMarker(): Boolean =
    split(' ').any { token ->
        token == "women" || token == "woman" || token.startsWith("women") ||
            token.startsWith("fem")
    }

private fun String.friendlyCategory(): String {
    val tokens = split(' ')
    return when {
        hasWomenMarker() -> "women"
        tokens.any { it == "club" || it == "clubs" } -> "clubs"
        else -> "national"
    }
}

private fun normalizeCompetition(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD)
    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    .replace(Regex("""[^a-z0-9]+"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()

class MatchViewModel(
    private val repository: MatchRepository,
    private val filtersStore: FiltersStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        MatchUiState(
            liveOnly = filtersStore.liveOnly,
            favoritesOnly = filtersStore.favoritesOnly,
            groupByCompetition = filtersStore.groupByCompetition,
            favoriteTeamKeys = filtersStore.favoriteTeamKeys,
            countrySelectionConfigured = filtersStore.countrySelectionConfigured,
            settingsCountries = filtersStore.settingsCountries,
            settingsLeaguesByCountry = filtersStore.settingsCountries
                .associateWith { filtersStore.getSettingsLeagues(it) },
            topDivisionCatalogsByCountry = filtersStore.settingsCountries
                .mapNotNull { country ->
                    filtersStore.getTopDivisionCatalog(country)?.let { country to it }
                }
                .toMap(),
            flagFilters = filtersStore.flagFilters
        )
    )
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private val _availableCountries = MutableStateFlow<List<ApiFootballCountryDto>>(emptyList())
    val availableCountries: StateFlow<List<ApiFootballCountryDto>> = _availableCountries.asStateFlow()

    private val _leaguesByCountry = MutableStateFlow<Map<String, List<ApiFootballLeagueEntryDto>>>(emptyMap())
    val leaguesByCountry: StateFlow<Map<String, List<ApiFootballLeagueEntryDto>>> = _leaguesByCountry.asStateFlow()

    private var loadJob: Job? = null
    private var loadingDate: LocalDate? = null
    private var liveRefreshJob: Job? = null
    private var searchRefreshJob: Job? = null
    private var searchCacheLoaded = false

    init {
        load(refresh = false)
    }

    /**
     * Publie immédiatement le dernier cache quotidien, y compris lors d'une
     * actualisation manuelle. La synchronisation continue en arrière-plan et publie
     * les nouvelles données au fur et à mesure qu'elles deviennent disponibles.
     */
    fun load(refresh: Boolean = false) {
        val requestedDate = _state.value.date
        // Un nouvel appui sur Actualiser ne doit pas annuler puis redémarrer la même
        // synchronisation. Un changement de date, lui, remplace bien le chargement.
        if (loadJob?.isActive == true && loadingDate == requestedDate) return
        loadJob?.cancel()
        loadingDate = requestedDate
        loadJob = viewModelScope.launch {
            try {
                val date = requestedDate.toString()
                val cached = repository.getCachedMatchesForDate(date)
                val isPastDate = requestedDate.isBefore(LocalDate.now())
                val needsPastRecovery = isPastDate && (
                    cached.isNullOrEmpty() || cached.any { match ->
                        (match.score.home == null || match.score.away == null)
                    }
                )
                val refreshCachedDate = cached != null && (
                    refresh ||
                        !requestedDate.isBefore(LocalDate.now()) ||
                        needsPastRecovery
                )

                if (cached != null) {
                    publishMatches(
                        requestedDate = requestedDate,
                        matches = cached,
                        refreshing = refreshCachedDate
                    )
                    if (!refreshCachedDate) {
                        refreshMatchMetadata(requestedDate)
                        return@launch
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = !refresh,
                            isRefreshing = refresh,
                            error = null
                        )
                    }
                }

                val needsLiveSoccerTv = !requestedDate.isBefore(LocalDate.now()) ||
                    needsPastRecovery

                val result = repository.getMatchesForDate(
                    date = date,
                    forceRefresh = refresh || cached != null,
                    includeApiFootball = true
                )
                result.fold(
                    onSuccess = { list ->
                        val usesApiFootball = requestedDate.usesApiFootballWindow()
                        val needsRenderSchedule = needsLiveSoccerTv &&
                            (!usesApiFootball || list.isNotEmpty())
                        publishMatches(requestedDate, list, refreshing = needsRenderSchedule)
                        if (needsRenderSchedule) {
                            // J-1/J/J+1 : Render complète seulement les données TV.
                            // Autres jours : Render fournit la liste entière.
                            val liveSoccerMatches = repository.getTvScheduleForDate(date)
                            val merged = repository.mergeSupplementalMatches(
                                date, list, liveSoccerMatches
                            )
                            publishMatches(requestedDate, merged, refreshing = false)
                            refreshMatchMetadata(requestedDate)
                        } else {
                            refreshMatchMetadata(requestedDate)
                        }
                    },
                    onFailure = { err ->
                        if (_state.value.date != requestedDate) return@fold
                        val fallbackMatches = cached.orEmpty()
                        val liveSoccerMatches = if (
                            needsLiveSoccerTv &&
                            (!requestedDate.usesApiFootballWindow() ||
                                fallbackMatches.isNotEmpty())
                        ) {
                            repository.getTvScheduleForDate(date)
                        } else {
                            emptyList()
                        }
                        val enrichedFallback = if (liveSoccerMatches.isNotEmpty()) {
                            repository.mergeSupplementalMatches(
                                date, fallbackMatches, liveSoccerMatches
                            )
                        } else {
                            fallbackMatches
                        }
                        if (enrichedFallback.isNotEmpty()) {
                            publishMatches(requestedDate, enrichedFallback, refreshing = false)
                            refreshMatchMetadata(requestedDate)
                        } else {
                            _state.update {
                                if (refresh && it.matches.isNotEmpty()) {
                                    it.copy(isLoading = false, isRefreshing = false)
                                } else {
                                    it.copy(
                                        error = err.localizedMessage ?: "Erreur réseau",
                                        isLoading = false,
                                        isRefreshing = false
                                    )
                                }
                            }
                        }
                    }
                )
            } finally {
                if (loadingDate == requestedDate) loadingDate = null
            }
        }
    }

    private fun publishMatches(
        requestedDate: LocalDate,
        matches: List<Match>,
        refreshing: Boolean
    ) {
        if (_state.value.date != requestedDate) return
        val visibleMatches = matches.filterNot { it.isWomenMatch() }
        val matchesWithKnownLogos = repository.enrichWithKnownLogos(visibleMatches)
        val migratedFavoriteKeys = migrateLegacyFavoriteIdentities(matchesWithKnownLogos)
        _state.update {
            val searchableMatches = if (searchCacheLoaded) {
                (it.cachedSearchMatches.filterNot { match ->
                    match.localCalendarDate() == requestedDate
                } + matchesWithKnownLogos).distinctBy { match ->
                    "${match.utcDate}|${match.homeTeam.name}|${match.awayTeam.name}"
                }
            } else {
                it.cachedSearchMatches
            }
            it.copy(
                matches = matchesWithKnownLogos,
                favoriteTeamKeys = migratedFavoriteKeys,
                isLoading = false,
                isRefreshing = refreshing,
                error = null,
                cachedSearchMatches = searchableMatches
            ).recompute()
        }
    }

    /**
     * Les anciennes versions sauvegardaient seulement "arsenal". On ne migre ce
     * nom que lorsqu'une seule identité réelle correspond dans les pays actifs.
     */
    private fun migrateLegacyFavoriteIdentities(matches: List<Match>): Set<String> {
        var favorites = filtersStore.favoriteTeamKeys
        val legacyKeys = favorites.filterNot { it.isStructuredFavoriteIdentity() }
        if (legacyKeys.isEmpty()) return favorites

        val activeCountries = _state.value.flagFilters.intersect(_state.value.settingsCountries)
        legacyKeys.forEach { legacyKey ->
            val candidates = matches.asSequence()
                .filter { match -> match.effectiveCompetitionCountry() in activeCountries }
                .flatMap { match ->
                    sequenceOf(match to match.homeTeam, match to match.awayTeam)
                }
                .filter { (_, team) -> legacyKey in team.identityNames() }
                .distinctBy { (match, team) ->
                    team.favoriteIdentitySignature(match.source)
                }
                .toList()
            if (candidates.size == 1) {
                val (match, team) = candidates.single()
                favorites = filtersStore.migrateFavoriteTeam(team, match.source)
            }
        }
        return favorites
    }

    /** Prépare l'index de recherche depuis le disque, sans déclencher le réseau. */
    fun loadCachedMatchesForSearch() {
        if (searchCacheLoaded || _state.value.isSearchLoading) return
        _state.update { it.copy(isSearchLoading = true) }
        viewModelScope.launch {
            val cachedMatches = repository.getAllCachedMatches()
                .filterNot { it.isWomenMatch() }
            searchCacheLoaded = true
            _state.update {
                // Relire la journée affichée dans l'update atomique : elle peut avoir
                // avancé pendant la lecture des fichiers du cache.
                val searchableMatches = (cachedMatches + it.matches)
                    .distinctBy { match ->
                        "${match.utcDate}|${match.homeTeam.name}|${match.awayTeam.name}"
                    }
                it.copy(
                    cachedSearchMatches = repository.enrichWithKnownLogos(searchableMatches),
                    isSearchLoading = false
                )
            }
        }
    }

    /** Complète via LiveSoccerTV les journées incomplètes de la recherche. */
    fun refreshMissingSearchScores(query: String) {
        val normalizedQuery = normalizeSearchQuery(query)
        if (normalizedQuery.length < 2 || _state.value.isSearchLoading) return

        val datesToRefresh = _state.value.cachedSearchMatches
            .filter { match ->
                val matchesTeam = listOfNotNull(
                    match.homeTeam.name,
                    match.homeTeam.shortName,
                    match.awayTeam.name,
                    match.awayTeam.shortName
                ).any { normalizeSearchQuery(it).contains(normalizedQuery) }
                val date = match.localCalendarDate()
                matchesTeam &&
                    date?.isBefore(LocalDate.now()) == true &&
                    (match.score.home == null || match.score.away == null)
            }
            .mapNotNull { it.localCalendarDate()?.toString() }
            .distinct()

        if (datesToRefresh.isEmpty()) return
        searchRefreshJob?.cancel()
        searchRefreshJob = viewModelScope.launch {
            for (date in datesToRefresh) {
                val baseMatches = _state.value.cachedSearchMatches.filter {
                    it.localCalendarDate()?.toString() == date
                }
                val liveSoccerMatches = repository.getTvScheduleForDate(date)
                if (liveSoccerMatches.isEmpty()) continue
                val refreshedMatches = repository.mergeSupplementalMatches(
                    date = date,
                    baseMatches = baseMatches,
                    supplementalMatches = liveSoccerMatches
                ).filterNot { it.isWomenMatch() }
                _state.update { state ->
                    state.copy(
                        cachedSearchMatches = (
                            state.cachedSearchMatches.filterNot {
                                it.localCalendarDate()?.toString() == date
                            } + refreshedMatches
                        ).distinctBy { match ->
                            "${match.utcDate}|${match.homeTeam.name}|${match.awayTeam.name}"
                        }
                    )
                }
            }
        }
    }

    private suspend fun refreshMatchMetadata(requestedDate: LocalDate) {
        if (_state.value.date != requestedDate) return
        val countriesToRefresh = _state.value.flagFilters.filterTo(mutableSetOf()) {
            filtersStore.shouldRefreshTopDivisionTeamNames(it)
        }
        val fetchedTopDivisionTeams = if (countriesToRefresh.isNotEmpty()) {
            repository.getTopDivisionCatalogs(countriesToRefresh)
        } else {
            emptyMap()
        }
        if (_state.value.date != requestedDate) return

        val refreshedTopDivisionTeams = preserveCompleteCatalogs(fetchedTopDivisionTeams)
            .filterValues { it.teamNames.isNotEmpty() }
        refreshedTopDivisionTeams.forEach { (country, catalog) ->
            filtersStore.setTopDivisionCatalog(country, catalog)
        }
        _state.update {
            it.copy(
                topDivisionCatalogsByCountry =
                    it.topDivisionCatalogsByCountry + refreshedTopDivisionTeams
            ).recompute()
        }

        if (_availableCountries.value.size <= _state.value.settingsCountries.size) {
            val loadedCountries = repository.getAllCountries()
            if (_state.value.date != requestedDate) return
            _availableCountries.value = mergeCountryOptions(
                remote = loadedCountries,
                configured = _state.value.settingsCountries
            )
        }
        scheduleLiveRefresh()
    }

    private fun scheduleLiveRefresh() {
        liveRefreshJob?.cancel()
        val hasLive = _state.value.matches.any {
            it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME
        }
        if (!hasLive && !_state.value.liveOnly) return
        liveRefreshJob = viewModelScope.launch {
            delay(60_000)
            load(refresh = true)
        }
    }

    fun changeDate(date: LocalDate) {
        if (date == _state.value.date) return
        _state.update { it.copy(date = date) }
        load()
    }

    fun shiftDate(days: Long) = changeDate(_state.value.date.plusDays(days))

    fun toggleLive() = _state.update {
        val v = !it.liveOnly
        filtersStore.liveOnly = v
        it.copy(liveOnly = v).recompute()
    }

    fun toggleFavoritesOnly() = _state.update {
        val enabled = !it.favoritesOnly
        filtersStore.favoritesOnly = enabled
        it.copy(favoritesOnly = enabled).recompute()
    }

    fun toggleGroupByCompetition() = _state.update {
        val enabled = !it.groupByCompetition
        filtersStore.groupByCompetition = enabled
        it.copy(groupByCompetition = enabled)
    }

    fun toggleFavoriteTeam(match: Match, team: Team) {
        val favorites = filtersStore.toggleFavoriteTeam(team, match.source)
        _state.update { it.copy(favoriteTeamKeys = favorites).recompute() }
    }

    // === Flag filters (main screen) ===

    fun toggleFlag(country: String) {
        val next = filtersStore.toggleFlag(country)
        _state.update { it.copy(flagFilters = next).recompute() }
        if (country in next && filtersStore.shouldRefreshTopDivisionTeamNames(country)) {
            refreshEliteTeamNames(setOf(country))
        }
    }

    fun setAllFlags(allOn: Boolean) {
        val next = filtersStore.setAllFlags(allOn)
        _state.update { it.copy(flagFilters = next).recompute() }
        if (allOn) {
            val missing = next.filterTo(mutableSetOf()) {
                filtersStore.shouldRefreshTopDivisionTeamNames(it)
            }
            refreshEliteTeamNames(missing)
        }
    }

    private fun refreshEliteTeamNames(countries: Set<String>) {
        if (countries.isEmpty()) return
        viewModelScope.launch {
            val refreshed = preserveCompleteCatalogs(
                repository.getTopDivisionCatalogs(countries)
            )
                .filterValues { it.teamNames.isNotEmpty() }
            refreshed.forEach { (country, catalog) ->
                filtersStore.setTopDivisionCatalog(country, catalog)
            }
            _state.update {
                it.copy(
                    topDivisionCatalogsByCountry =
                        it.topDivisionCatalogsByCountry + refreshed
                ).recompute()
            }
        }
    }

    private fun preserveCompleteCatalogs(
        fetched: Map<String, TopDivisionCatalog>
    ): Map<String, TopDivisionCatalog> = fetched.mapValues { (country, incoming) ->
        val cached = filtersStore.getTopDivisionCatalog(country)
        if (
            cached != null &&
            competitionNamesMatch(
                normalizeCompetition(cached.leagueName),
                normalizeCompetition(incoming.leagueName)
            ) &&
            incoming.teamNames.size < cached.teamNames.size
        ) {
            incoming.copy(teamNames = incoming.teamNames + cached.teamNames)
        } else {
            incoming
        }
    }

    // === Settings ===

    fun loadSettingsCountries() {
        if (_availableCountries.value.isNotEmpty()) return
        viewModelScope.launch {
            val loaded = repository.getAllCountries()
            _availableCountries.value = mergeCountryOptions(
                remote = loaded,
                configured = _state.value.settingsCountries
            )
        }
    }

    fun loadLeaguesForCountry(country: String) {
        if (_leaguesByCountry.value.containsKey(country)) return
        viewModelScope.launch {
            val list = repository.getLeaguesForCountry(country)
            _leaguesByCountry.update { it + (country to list) }
        }
    }

    fun toggleSettingsCountry(country: String) {
        val nextCountries = filtersStore.toggleSettingsCountry(country)
        _state.update { st ->
            val newLeagues = if (country in nextCountries) {
                st.settingsLeaguesByCountry + (country to filtersStore.getSettingsLeagues(country))
            } else st.settingsLeaguesByCountry - country
            val newTopDivisionTeams = if (country in nextCountries) {
                filtersStore.getTopDivisionCatalog(country)?.let { catalog ->
                    st.topDivisionCatalogsByCountry + (country to catalog)
                } ?: st.topDivisionCatalogsByCountry
            } else {
                st.topDivisionCatalogsByCountry - country
            }
            st.copy(
                countrySelectionConfigured = true,
                settingsCountries = nextCountries,
                settingsLeaguesByCountry = newLeagues,
                topDivisionCatalogsByCountry = newTopDivisionTeams,
                flagFilters = filtersStore.flagFilters
            ).recompute()
        }
        if (
            country in nextCountries &&
            filtersStore.flagFilters.contains(country) &&
            filtersStore.shouldRefreshTopDivisionTeamNames(country)
        ) {
            refreshEliteTeamNames(setOf(country))
        }
    }

    fun toggleSettingsLeague(country: String, leagueName: String) {
        val nextLeagues = filtersStore.toggleSettingsLeague(country, leagueName)
        _state.update { st ->
            st.copy(
                settingsLeaguesByCountry = st.settingsLeaguesByCountry + (country to nextLeagues)
            ).recompute()
        }
        if (
            country in _state.value.flagFilters &&
            filtersStore.shouldRefreshTopDivisionTeamNames(country)
        ) {
            refreshEliteTeamNames(setOf(country))
        }
    }

    class Factory(
        private val repository: MatchRepository,
        private val filtersStore: FiltersStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MatchViewModel(repository, filtersStore) as T
    }
}

private fun normalizeSearchQuery(value: String): String = Normalizer
    .normalize(value.trim(), Normalizer.Form.NFD)
    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    .lowercase(Locale.ROOT)

private fun LocalDate.usesApiFootballWindow(today: LocalDate = LocalDate.now()): Boolean =
    !isBefore(today.minusDays(1)) && !isAfter(today.plusDays(1))

private fun Match.isWomenMatch(): Boolean =
    homeTeam.isWomenTeam() || awayTeam.isWomenTeam() ||
        isWomenCompetitionName(competitionName)
