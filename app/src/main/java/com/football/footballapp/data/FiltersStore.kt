package com.football.footballapp.data

import android.content.Context
import android.content.SharedPreferences

class FiltersStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    // === Toggles header ===

    var liveOnly: Boolean
        get() = prefs.getBoolean(KEY_LIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE, value).apply()

    var showAll: Boolean
        get() = prefs.getBoolean(KEY_ALL, false)
        set(value) = prefs.edit().putBoolean(KEY_ALL, value).apply()

    // === Settings : pays activés (visibles en drapeaux dans main screen) ===

    var settingsCountries: Set<String>
        get() = try {
            prefs.getStringSet(KEY_SETTINGS_COUNTRIES, emptySet()) ?: emptySet()
        } catch (_: ClassCastException) {
            prefs.edit().remove(KEY_SETTINGS_COUNTRIES).apply()
            emptySet()
        }
        set(value) = prefs.edit().putStringSet(KEY_SETTINGS_COUNTRIES, value).apply()

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
        val key = leagueName.lowercase().trim()
        val current = getSettingsLeagues(country).toMutableSet()
        if (!current.add(key)) current.remove(key)
        setSettingsLeagues(country, current)
        return current
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

    private fun leagueKey(country: String) = "settings_leagues_" + country.lowercase()

    companion object {
        private const val KEY_LIVE = "live_only"
        private const val KEY_ALL = "show_all"
        private const val KEY_SETTINGS_COUNTRIES = "settings_countries"
        private const val KEY_FLAG_FILTERS = "flag_filters"
    }
}
