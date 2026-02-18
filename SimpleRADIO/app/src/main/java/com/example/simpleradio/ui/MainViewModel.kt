package com.example.simpleradio.ui

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(
        private val radioRepository: RadioRepository,
        private val prefs: SharedPreferences
) : ViewModel() {

    // --- STATE: Player ---
    var playingRadio by mutableStateOf<RadioStationEntity?>(null)
    var navRadioList by mutableStateOf<List<RadioStationEntity>>(emptyList())
    var radioStations by mutableStateOf<List<RadioStationEntity>>(emptyList())
    var isFullScreenPlayer by mutableStateOf(false)
    var playerIsPlaying by mutableStateOf(false)
    var sleepTimerTimeLeft by mutableStateOf<Long?>(null)

    // --- STATE: Metadata ---
    var currentArtist by mutableStateOf<String?>(null)
    var currentTitle by mutableStateOf<String?>(null)
    var currentArtworkUrl by mutableStateOf<String?>(null)

    // --- STATE: Filters & Search ---
    var radioCountries by mutableStateOf<List<RadioCountry>>(emptyList())
    var radioTags by mutableStateOf<List<RadioTag>>(emptyList())

    var selectedRadioCountry by mutableStateOf(prefs.getString("selectedRadioCountry", null))
        private set
    var selectedRadioTag by mutableStateOf(prefs.getString("selectedRadioTag", null))
        private set
    var selectedRadioBitrate by
            mutableStateOf(prefs.getInt("selectedRadioBitrate", -1).takeIf { it != -1 })
        private set
    var selectedRadioFavoriteListId by
            mutableStateOf(prefs.getInt("selectedRadioFavoriteListId", -1).takeIf { it != -1 })
        private set
    var radioSearchQuery by mutableStateOf(prefs.getString("radioSearchQuery", "") ?: "")
        private set

    var radioSortOrder by mutableStateOf("clickcount")
    var isViewingRadioResults by mutableStateOf(false)
    var showRecentRadiosOnly by mutableStateOf(prefs.getBoolean("showRecentRadiosOnly", true))
    var searchTrigger by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)

    // --- STATE: UI Memory ---
    var isQualityExpanded by mutableStateOf(false)
    var isCountryExpanded by mutableStateOf(false)
    var isGenreExpanded by mutableStateOf(false)

    var radioToFavorite by mutableStateOf<RadioStationEntity?>(null)
    var showAddListDialog by mutableStateOf(false)

    init {
        loadInitialData()
        startSleepTimerJob()
    }

    fun setSelectedCountry(code: String?) {
        selectedRadioCountry = code
        prefs.edit { putString("selectedRadioCountry", code) }
    }

    fun setSelectedTag(tag: String?) {
        selectedRadioTag = tag
        prefs.edit { putString("selectedRadioTag", tag) }
    }

    fun setSelectedBitrate(bitrate: Int?) {
        selectedRadioBitrate = bitrate
        if (bitrate != null) {
            prefs.edit { putInt("selectedRadioBitrate", bitrate) }
        } else {
            prefs.edit { remove("selectedRadioBitrate") }
        }
    }

    fun updateSearchQuery(query: String) {
        radioSearchQuery = query
        prefs.edit { putString("radioSearchQuery", query) }
    }

    fun searchStations(context: android.content.Context) {
        if (showRecentRadiosOnly || selectedRadioFavoriteListId != null) return

        viewModelScope.launch {
            isLoading = true
            try {
                val bitrateMax =
                        when (selectedRadioBitrate) {
                            64 -> 127
                            128 -> 191
                            0 -> 63
                            else -> null
                        }
                val results =
                        radioRepository.searchStations(
                                selectedRadioCountry,
                                selectedRadioTag,
                                radioSearchQuery.takeIf { it.isNotBlank() },
                                selectedRadioBitrate,
                                bitrateMax,
                                radioSortOrder
                        )
                radioStations = results
                // Save to cache
                radioRepository.saveCacheList(
                        java.io.File(context.filesDir, "last_radio_list.json"),
                        results
                )
            } catch (_: Exception) {
                radioStations = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun onRecentClick() {
        showRecentRadiosOnly = true
        selectedRadioCountry = null
        selectedRadioTag = null
        selectedRadioBitrate = null
        radioSearchQuery = ""
        isViewingRadioResults = true

        // Load recents from repo if needed, but here they are observed via Flow in repo
        // which we can also observe in ViewModel
    }

    fun onResetFilters() {
        selectedRadioCountry = null
        selectedRadioTag = null
        selectedRadioBitrate = null
        radioSearchQuery = ""
        showRecentRadiosOnly = false
        isViewingRadioResults = false
        isQualityExpanded = false
        isCountryExpanded = false
        isGenreExpanded = false
        searchTrigger++
    }

    fun onApplyFilters() {
        showRecentRadiosOnly = false
        selectedRadioFavoriteListId = null
        isViewingRadioResults = true
        searchTrigger++
    }

    fun incrementSearchTrigger() {
        searchTrigger++
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            isLoading = true
            try {
                radioCountries =
                        radioRepository.getCountries().sortedByDescending { it.stationcount }
                radioTags =
                        radioRepository
                                .getTags()
                                .sortedByDescending { it.stationcount }
                                .filter { it.stationcount > 10 }
                                .take(100)
            } finally {
                isLoading = false
            }
        }
    }

    private fun startSleepTimerJob() {
        viewModelScope.launch {
            while (true) {
                val timeLeft = sleepTimerTimeLeft
                if (timeLeft != null && timeLeft > 0) {
                    delay(1000)
                    sleepTimerTimeLeft = timeLeft - 1000
                    if (sleepTimerTimeLeft!! <= 0) {
                        // Action de pause gérée par l'UI qui observe cet état
                        sleepTimerTimeLeft = null
                    }
                } else {
                    delay(5000)
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerTimeLeft = minutes?.let { it * 60 * 1000L }
    }
}
