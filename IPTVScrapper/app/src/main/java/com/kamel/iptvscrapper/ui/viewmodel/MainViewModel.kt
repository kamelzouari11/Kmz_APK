package com.kamel.iptvscrapper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamel.iptvscrapper.data.IptvRepository
import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.kamel.iptvscrapper.data.api.IptvCategory
import com.kamel.iptvscrapper.data.api.IptvChannel
import com.kamel.iptvscrapper.data.api.IptvClient

class MainViewModel(
    private val repository: IptvRepository,
    private val iptvClient: IptvClient = IptvClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allLinks.collect { links ->
                _uiState.value = _uiState.value.copy(links = links)
            }
        }
    }

    fun browseLink(link: LinkEntity) {
        _uiState.value = _uiState.value.copy(
            selectedLink = link,
            screenState = ScreenState.CATEGORIES,
            isLoading = true
        )
        viewModelScope.launch {
            val categories = iptvClient.getCategories(link)
            _uiState.value = _uiState.value.copy(
                categories = categories,
                isLoading = false
            )
        }
    }

    fun selectCategory(category: IptvCategory) {
        val link = _uiState.value.selectedLink ?: return
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            screenState = ScreenState.CHANNELS,
            isLoading = true
        )
        viewModelScope.launch {
            val channels = iptvClient.getChannels(link, category.id)
            _uiState.value = _uiState.value.copy(
                channels = channels,
                isLoading = false
            )
        }
    }

    fun playChannel(channel: IptvChannel) {
        val link = _uiState.value.selectedLink ?: return
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            screenState = ScreenState.PLAYER,
            isLoading = true
        )
        viewModelScope.launch {
            val streamUrl = iptvClient.getStreamUrl(link, channel)
            _uiState.value = _uiState.value.copy(
                currentStreamUrl = streamUrl,
                isLoading = false
            )
        }
    }

    fun goBack() {
        _uiState.value = when (_uiState.value.screenState) {
            ScreenState.CATEGORIES -> _uiState.value.copy(screenState = ScreenState.HOME, selectedLink = null)
            ScreenState.CHANNELS -> _uiState.value.copy(screenState = ScreenState.CATEGORIES, selectedCategory = null)
            ScreenState.PLAYER -> _uiState.value.copy(screenState = ScreenState.CHANNELS, selectedChannel = null, currentStreamUrl = null)
            else -> _uiState.value
        }
    }

    fun scrapeLinks(onFinished: (Int) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScraping = true)
            val count = repository.scrapeAndSave()
            _uiState.value = _uiState.value.copy(isScraping = false)
            onFinished(count)
        }
    }

    fun testAllLinks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            val pendingLinks = _uiState.value.links.filter { it.status == "PENDING" }
            pendingLinks.forEach { link ->
                repository.testLink(link)
            }
            _uiState.value = _uiState.value.copy(isTesting = false)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun importManualText(text: String, onFinished: (Int, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val count = repository.importFromText(text)
                onFinished(count, null)
            } catch (e: Exception) {
                e.printStackTrace()
                onFinished(0, e.message ?: "Unknown error")
            }
        }
    }

    fun toggleFilterWorking() {
        _uiState.value = _uiState.value.copy(isFilterWorking = !_uiState.value.isFilterWorking)
    }

    fun getWorkingLinksText(): String {
        val workingLinks = _uiState.value.links.filter { it.status == "WORKING" }
        return workingLinks.joinToString("\n\n") { link ->
            val sb = StringBuilder()
            sb.append("URL: ${link.url}\n")
            when (link.type) {
                "XTREAM" -> {
                    sb.append("User: ${link.username}\n")
                    sb.append("Pass: ${link.password}")
                }
                "STALKER" -> {
                    sb.append("MAC: ${link.mac}")
                }
                "M3U" -> {
                    sb.append("Type: M3U Playlist")
                }
            }
            sb.toString()
        }
    }
}

enum class ScreenState { HOME, CATEGORIES, CHANNELS, PLAYER }

data class MainUiState(
    val links: List<LinkEntity> = emptyList(),
    val isScraping: Boolean = false,
    val isTesting: Boolean = false,
    val screenState: ScreenState = ScreenState.HOME,
    val selectedLink: LinkEntity? = null,
    val categories: List<IptvCategory> = emptyList(),
    val selectedCategory: IptvCategory? = null,
    val channels: List<IptvChannel> = emptyList(),
    val selectedChannel: IptvChannel? = null,
    val currentStreamUrl: String? = null,
    val isLoading: Boolean = false,
    val isFilterWorking: Boolean = false
)
