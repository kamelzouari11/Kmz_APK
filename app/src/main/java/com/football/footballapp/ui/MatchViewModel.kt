package com.football.footballapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.football.footballapp.data.ApiFootballCountryDto
import com.football.footballapp.data.ApiFootballLeagueEntryDto
import com.football.footballapp.data.FiltersStore
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchStatus
import com.football.footballapp.repository.MatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MatchUiState(
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val matches: List<Match> = emptyList(),
    val liveOnly: Boolean = false,
    val showAll: Boolean = false,
    val settingsCountries: Set<String> = emptySet(),
    val settingsLeaguesByCountry: Map<String, Set<String>> = emptyMap(),
    val flagFilters: Set<String> = emptySet(),
    val error: String? = null
) {
    val filteredMatches: List<Match>
        get() {
            val byCountry = matches.filter { match ->
                val country = match.competitionCountry ?: return@filter false
                if (country !in flagFilters) return@filter false
                val leagues = settingsLeaguesByCountry[country] ?: return@filter false
                val low = match.competitionName.lowercase().trim()
                leagues.any { sel ->
                    low == sel || low.startsWith("$sel ") ||
                        low.startsWith("$sel-") || low.startsWith("$sel(")
                }
            }
            val byLive = if (liveOnly) {
                byCountry.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME }
            } else byCountry
            return if (showAll) byLive.sortedBy { it.utcDate } else byLive
        }

    val groupedByCompetition: Map<String, List<Match>>
        get() {
            if (showAll) return mapOf("Tous les matchs" to filteredMatches)
            return filteredMatches.groupBy { it.competitionName }
        }
}

class MatchViewModel(
    private val repository: MatchRepository,
    private val filtersStore: FiltersStore
) : ViewModel() {

    private val _state = MutableStateFlow(
        MatchUiState(
            liveOnly = filtersStore.liveOnly,
            showAll = filtersStore.showAll,
            settingsCountries = filtersStore.settingsCountries,
            settingsLeaguesByCountry = filtersStore.settingsCountries
                .associateWith { filtersStore.getSettingsLeagues(it) },
            flagFilters = filtersStore.flagFilters
        )
    )
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private val _availableCountries = MutableStateFlow<List<ApiFootballCountryDto>>(emptyList())
    val availableCountries: StateFlow<List<ApiFootballCountryDto>> = _availableCountries.asStateFlow()

    private val _leaguesByCountry = MutableStateFlow<Map<String, List<ApiFootballLeagueEntryDto>>>(emptyMap())
    val leaguesByCountry: StateFlow<Map<String, List<ApiFootballLeagueEntryDto>>> = _leaguesByCountry.asStateFlow()

    private var loadJob: Job? = null

    init { load(refresh = false) }  // au démarrage : cache uniquement, AUCUN réseau

    /**
     * @param refresh false = cache only (aucun appel réseau, économise le quota).
     *                true = fetch réseau forcé (sur action manuelle de l'utilisateur).
     */
    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = !refresh, isRefreshing = refresh, error = null) }
            val result = repository.getMatchesForDate(_state.value.date.toString(), forceRefresh = refresh)
            result.fold(
                onSuccess = { list ->
                    _state.update { it.copy(matches = list, isLoading = false, isRefreshing = false) }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            error = err.localizedMessage ?: "Erreur réseau",
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                }
            )
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
        it.copy(liveOnly = v)
    }

    fun toggleShowAll() = _state.update {
        val v = !it.showAll
        filtersStore.showAll = v
        it.copy(showAll = v)
    }

    // === Flag filters (main screen) ===

    fun toggleFlag(country: String) {
        val next = filtersStore.toggleFlag(country)
        _state.update { it.copy(flagFilters = next) }
    }

    fun setAllFlags(allOn: Boolean) {
        val next = filtersStore.setAllFlags(allOn)
        _state.update { it.copy(flagFilters = next) }
    }

    // === Settings ===

    fun loadSettingsCountries() {
        if (_availableCountries.value.isNotEmpty()) return
        viewModelScope.launch {
            _availableCountries.value = repository.getAllCountries()
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
            st.copy(
                settingsCountries = nextCountries,
                settingsLeaguesByCountry = newLeagues,
                flagFilters = filtersStore.flagFilters
            )
        }
    }

    fun toggleSettingsLeague(country: String, leagueName: String) {
        val nextLeagues = filtersStore.toggleSettingsLeague(country, leagueName)
        _state.update { st ->
            st.copy(settingsLeaguesByCountry = st.settingsLeaguesByCountry + (country to nextLeagues))
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
