package com.example.simpleiptv.ui.viewmodel

import androidx.compose.runtime.*
import com.example.simpleiptv.data.local.ChannelWithProfile
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity

enum class GeneratorType {
    SEARCH,
    GLOBAL_SEARCH,
    RECENTS,
    FAVORITES,
    CATEGORY
}

enum class SearchScope {
    ACTIVE_PROFILE,
    ALL_PROFILES
}

enum class MediaMode {
    LIVE,
    VOD
}

data class DialogState(
    val showProfileManager: Boolean = false,
    val showAddProfileDialog: Boolean = false,
    val profileToEdit: ProfileEntity? = null,
    val showAddListDialog: Boolean = false,
    val channelToFavorite: ChannelEntity? = null,
    val targetFavoriteLists: List<FavoriteListEntity> = emptyList(),
    val showRestoreConfirmDialog: Boolean = false,
    val favoriteListToDelete: FavoriteListEntity? = null,
    val showDeleteFavoriteListConfirm: Boolean = false,
    val backupJsonToRestore: String = "",
)

@Stable
class MainUiState {
    // Données
    var profiles by mutableStateOf<List<ProfileEntity>>(emptyList())
    var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
    var channels by mutableStateOf<List<ChannelEntity>>(emptyList())
    var favoriteLists by mutableStateOf<List<FavoriteListEntity>>(emptyList())
    var globalSearchResults by mutableStateOf<List<ChannelWithProfile>>(emptyList())
    // État courant
    var activeProfileId by mutableIntStateOf(-1)
    var currentMediaMode by mutableStateOf(MediaMode.LIVE)
    var lastGeneratorType by mutableStateOf(GeneratorType.RECENTS)
    // UI state
    var isLoading by mutableStateOf(false)
    var playingChannel by mutableStateOf<ChannelEntity?>(null)
    var isFullScreenPlayer by mutableStateOf(false)
    // Dialogs
    var dialogState by mutableStateOf(DialogState())
    // Recherche
    var searchQuery by mutableStateOf("")
    var searchScope by mutableStateOf(SearchScope.ALL_PROFILES)
    var recentScope by mutableStateOf(SearchScope.ALL_PROFILES)
    var favoriteScope by mutableStateOf(SearchScope.ALL_PROFILES)
    var selectedCategoryId by mutableStateOf<String?>(null)
    var selectedFavoriteListId by mutableIntStateOf(-1)
    // Meta
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    var loadedProfileIds by mutableStateOf<Set<Int>>(emptySet())
    var loadingProfileId by mutableIntStateOf(-1)
    var allFavoriteIds by mutableStateOf<Set<String>>(emptySet())
    // Erreurs
    var syncError by mutableStateOf<String?>(null)
    var failedProfileToReload by mutableStateOf<ProfileEntity?>(null)
    var inaccessibleProfileDuringPurge by mutableStateOf<ProfileEntity?>(null)
    var isPurgingProfiles by mutableStateOf(false)
    var purgeCurrentIndex by mutableIntStateOf(0)
    var purgeTotal by mutableIntStateOf(0)
    var isLoadingAllProfiles by mutableStateOf(false)
    var loadAllCurrentIndex by mutableIntStateOf(0)
    var loadAllTotal by mutableIntStateOf(0)
    var loadAllCurrentProfileId by mutableIntStateOf(-1)
    var isTestingSearchResults by mutableStateOf(false)
    var searchTestedCount by mutableIntStateOf(0)
    var searchTestTotal by mutableIntStateOf(0)

    // Pagination
    var pageOffset by mutableIntStateOf(0)
    val pageSize = 50
    var hasMore by mutableStateOf(true)
    var isLoadingMore by mutableStateOf(false)

    val filteredCategories: List<CategoryEntity> by derivedStateOf {
        categories.filter { !it.category_name.startsWith("-") }
    }

    val filteredFavoriteLists: List<FavoriteListEntity> by derivedStateOf { favoriteLists }

    val lastList: List<ChannelEntity> by derivedStateOf {
        if (lastGeneratorType == GeneratorType.GLOBAL_SEARCH) {
            globalSearchResults.map { it.toChannelEntity() }
        } else {
            channels
        }
    }

    val lastListLabel: String by derivedStateOf {
        when (lastGeneratorType) {
            GeneratorType.RECENTS -> "Récents global"
            GeneratorType.SEARCH, GeneratorType.GLOBAL_SEARCH -> {
                val scopeLabel = if (searchScope == SearchScope.ALL_PROFILES) "globale" else "profil"
                "Recherche $scopeLabel : $searchQuery"
            }
            GeneratorType.FAVORITES -> {
                val listName = favoriteLists.find { it.id == selectedFavoriteListId }?.name ?: "Favoris"
                val scopeLabel = if (favoriteScope == SearchScope.ALL_PROFILES) "global" else "profil"
                "$listName ($scopeLabel)"
            }
            GeneratorType.CATEGORY -> filteredCategories.find { it.category_id == selectedCategoryId }?.category_name ?: "Catégorie"
        }
    }
}
