package com.example.simpleiptv.data.usecase

import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.SearchScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ChannelListUseCase(private val repository: IptvRepository) {

    fun getChannelsForType(
        type: GeneratorType,
        categoryId: String?,
        favoriteListId: Int,
        profileId: Int,
        mediaMode: String,
        recentScope: SearchScope,
        favoriteScope: SearchScope,
        favoriteLists: List<FavoriteListEntity>
    ): Flow<List<ChannelEntity>> {
        return when (type) {
            GeneratorType.RECENTS -> {
                repository.getAllRecentChannels(mediaMode)
            }
            GeneratorType.CATEGORY -> {
                repository.getChannelsByCategory(
                    categoryId ?: "",
                    profileId,
                    mediaMode
                )
            }
            GeneratorType.FAVORITES -> {
                if (favoriteScope == SearchScope.ALL_PROFILES) {
                    repository.getAllProfileChannelsByFavoriteList(favoriteListId, mediaMode)
                } else {
                    repository.getChannelsByFavoriteList(favoriteListId, profileId, mediaMode)
                }
            }
            GeneratorType.SEARCH, GeneratorType.GLOBAL_SEARCH -> {
                emptyFlow()
            }
        }
    }

    /** Version paginée pour le chargement progressif. */
    fun getChannelsForTypePaginated(
        type: GeneratorType,
        categoryId: String?,
        favoriteListId: Int,
        profileId: Int,
        mediaMode: String,
        recentScope: SearchScope,
        favoriteScope: SearchScope,
        favoriteLists: List<FavoriteListEntity>,
        offset: Int = 0,
        limit: Int = 50,
        searchQuery: String = ""
    ): Flow<List<ChannelEntity>> {
        return when (type) {
            GeneratorType.RECENTS -> {
                repository.getAllRecentChannelsPaginated(mediaMode, offset, limit)
            }
            GeneratorType.CATEGORY -> {
                repository.getChannelsByCategoryPaginated(
                    categoryId ?: "",
                    profileId,
                    mediaMode,
                    offset,
                    limit
                )
            }
            GeneratorType.FAVORITES -> {
                if (favoriteScope == SearchScope.ALL_PROFILES) {
                    repository.getAllProfileChannelsByFavoriteListPaginated(favoriteListId, mediaMode, offset, limit)
                } else {
                    repository.getChannelsByFavoriteListPaginated(favoriteListId, profileId, mediaMode, offset, limit)
                }
            }
            GeneratorType.SEARCH -> {
                repository.searchChannelsPaginated(
                    searchQuery,
                    profileId,
                    mediaMode,
                    offset,
                    limit
                )
            }
            GeneratorType.GLOBAL_SEARCH -> {
                // La recherche globale utilise une requête raw SQL, on la garde en version non-paginée pour l'instant
                emptyFlow()
            }
        }
    }

}
