package com.football.footballapp.data

import android.content.Context
import android.content.SharedPreferences
import com.football.footballapp.data.model.Team
import com.football.footballapp.data.model.TopDivisionCatalog
import com.football.footballapp.data.model.favoriteIdentity
import com.football.footballapp.data.model.identityNames
import com.football.footballapp.data.model.isStructuredFavoriteIdentity
import com.football.footballapp.data.model.matchesFavorite
import java.util.Locale

class FiltersStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    // === Toggles header ===

    var liveOnly: Boolean
        get() = prefs.getBoolean(KEY_LIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE, value).apply()

    var favoritesOnly: Boolean
        get() = prefs.getBoolean(KEY_FAVORITES_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_FAVORITES_ONLY, value).apply()

    var groupByCompetition: Boolean
        get() = prefs.getBoolean(KEY_GROUP_BY_COMPETITION, false)
        set(value) = prefs.edit().putBoolean(KEY_GROUP_BY_COMPETITION, value).apply()

    var favoriteTeamKeys: Set<String>
        get() = try {
            prefs.getStringSet(KEY_FAVORITE_TEAMS, emptySet())?.toSet().orEmpty()
        } catch (_: ClassCastException) {
            prefs.edit().remove(KEY_FAVORITE_TEAMS).apply()
            emptySet()
        }
        private set(value) = prefs.edit().putStringSet(KEY_FAVORITE_TEAMS, value).apply()

    fun toggleFavoriteTeam(team: Team, source: String): Set<String> {
        val aliases = team.identityNames()
        if (aliases.isEmpty()) return favoriteTeamKeys

        val current = favoriteTeamKeys.toMutableSet()
        if (team.matchesFavorite(current, source)) {
            current.removeAll { stored ->
                team.matchesFavorite(setOf(stored), source)
            }
        } else {
            current.add(team.favoriteIdentity(source))
        }
        favoriteTeamKeys = current
        return current
    }

    /** Remplace sans interaction un ancien favori basé sur le nom par son ID réel. */
    fun migrateFavoriteTeam(team: Team, source: String): Set<String> {
        val aliases = team.identityNames()
        if (aliases.isEmpty()) return favoriteTeamKeys
        val current = favoriteTeamKeys.toMutableSet()
        val legacyKeys = current.filterTo(mutableSetOf()) { stored ->
            !stored.isStructuredFavoriteIdentity() && stored in aliases
        }
        if (legacyKeys.isEmpty()) return current
        current.removeAll(legacyKeys)
        current.add(team.favoriteIdentity(source))
        favoriteTeamKeys = current
        return current
    }

    // === Settings : pays activés (visibles en drapeaux dans main screen) ===

    var settingsCountries: Set<String>
        get() = try {
            prefs.getStringSet(KEY_SETTINGS_COUNTRIES, emptySet()) ?: emptySet()
        } catch (_: ClassCastException) {
            prefs.edit().remove(KEY_SETTINGS_COUNTRIES).apply()
            emptySet()
        }
        set(value) = prefs.edit().putStringSet(KEY_SETTINGS_COUNTRIES, value).apply()

    val countrySelectionConfigured: Boolean
        get() = prefs.contains(KEY_SETTINGS_COUNTRIES)

    fun toggleSettingsCountry(country: String): Set<String> {
        val current = settingsCountries.toMutableSet()
        val flags = flagFilters.toMutableSet()
        if (current.add(country)) {
            // newly enabled → drapeau activé en filtre par défaut
            flags.add(country)
        } else {
            current.remove(country)
            flags.remove(country)
        }
        settingsCountries = current
        flagFilters = flags
        return current
    }

    // === Settings : ligues activées par pays (par nom, lowercased) ===

    fun getSettingsLeagues(country: String): Set<String> = try {
        prefs.getStringSet(leagueKey(country), null) ?: emptySet()
    } catch (_: ClassCastException) {
        // Migration : ancien format (CSV de Int) → on wipe la clé et on repart vide.
        // L'utilisateur devra re-cocher les compétitions une fois.
        prefs.edit().remove(leagueKey(country)).apply()
        emptySet()
    }

    fun setSettingsLeagues(country: String, leagueNames: Set<String>) {
        prefs.edit().putStringSet(leagueKey(country), leagueNames).apply()
    }

    fun toggleSettingsLeague(country: String, leagueName: String): Set<String> {
        val key = leagueName.lowercase(Locale.ROOT).trim()
        val current = getSettingsLeagues(country).toMutableSet()
        if (!current.add(key)) current.remove(key)
        setSettingsLeagues(country, current)
        return current
    }

    fun getTopDivisionCatalog(country: String): TopDivisionCatalog? = try {
        val names = prefs.getStringSet(topDivisionTeamNamesKey(country), emptySet()).orEmpty()
        val leagueName = prefs.getString(topDivisionLeagueNameKey(country), null)
        if (names.isEmpty() || leagueName.isNullOrBlank()) null
        else TopDivisionCatalog(leagueName = leagueName, teamNames = names)
    } catch (_: ClassCastException) {
        prefs.edit().remove(topDivisionTeamNamesKey(country)).apply()
        null
    }

    /** Une réponse réseau vide/429 ne doit jamais détruire le dernier référentiel valide. */
    fun setTopDivisionCatalog(country: String, catalog: TopDivisionCatalog) {
        if (catalog.teamNames.isEmpty() || catalog.leagueName.isBlank()) return
        prefs.edit()
            .putStringSet(topDivisionTeamNamesKey(country), catalog.teamNames)
            .putString(topDivisionLeagueNameKey(country), catalog.leagueName)
            .putLong(topDivisionTeamNamesUpdatedKey(country), System.currentTimeMillis())
            .apply()
    }

    fun shouldRefreshTopDivisionTeamNames(
        country: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (country == "World") return false
        if (getTopDivisionCatalog(country) == null) return true
        val updatedAt = prefs.getLong(topDivisionTeamNamesUpdatedKey(country), 0L)
        return updatedAt <= 0L || nowMillis - updatedAt >= TOP_DIVISION_REFRESH_MILLIS
    }

    // === Flag filters : quels drapeaux sont actuellement ON pour filtrer la main screen ===

    var flagFilters: Set<String>
        get() = try {
            prefs.getStringSet(KEY_FLAG_FILTERS, null) ?: settingsCountries
        } catch (_: ClassCastException) {
            prefs.edit().remove(KEY_FLAG_FILTERS).apply()
            settingsCountries
        }
        set(value) = prefs.edit().putStringSet(KEY_FLAG_FILTERS, value).apply()

    fun toggleFlag(country: String): Set<String> {
        val current = flagFilters.toMutableSet()
        if (!current.add(country)) current.remove(country)
        flagFilters = current
        return current
    }

    fun setAllFlags(allOn: Boolean): Set<String> {
        val next = if (allOn) settingsCountries else emptySet()
        flagFilters = next
        return next
    }

    private fun leagueKey(country: String) =
        "settings_leagues_" + country.lowercase(Locale.ROOT)
    private fun topDivisionTeamNamesKey(country: String) =
        "top_division_team_names_v1_" + country.lowercase(Locale.ROOT)
    private fun topDivisionLeagueNameKey(country: String) =
        "top_division_league_name_v1_" + country.lowercase(Locale.ROOT)
    private fun topDivisionTeamNamesUpdatedKey(country: String) =
        "top_division_team_names_updated_v1_" + country.lowercase(Locale.ROOT)

    companion object {
        private const val KEY_LIVE = "live_only"
        private const val KEY_FAVORITES_ONLY = "favorites_only"
        private const val KEY_GROUP_BY_COMPETITION = "group_by_competition"
        private const val KEY_FAVORITE_TEAMS = "favorite_team_keys_v1"
        private const val KEY_SETTINGS_COUNTRIES = "settings_countries"
        private const val KEY_FLAG_FILTERS = "flag_filters"
        private const val TOP_DIVISION_REFRESH_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
