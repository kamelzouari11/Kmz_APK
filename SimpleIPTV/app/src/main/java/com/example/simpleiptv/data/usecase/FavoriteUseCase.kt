package com.example.simpleiptv.data.usecase

import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.FavoriteListEntity

class FavoriteUseCase(private val repository: IptvRepository) {

    suspend fun addFavoriteList(name: String, profileId: Int?, mediaMode: String) {
        repository.addFavoriteList(name, profileId, mediaMode)
    }

    suspend fun removeFavoriteList(list: FavoriteListEntity) {
        repository.removeFavoriteList(list)
    }

    suspend fun toggleFavorite(
        streamId: String,
        listId: Int,
        profileId: Int,
        mediaMode: String
    ) {
        repository.toggleChannelFavorite(streamId, listId, profileId, mediaMode)
    }

    suspend fun addChannelToFavoriteList(
        channel: ChannelEntity,
        listId: Int,
        profileId: Int,
        mediaMode: String
    ) {
        repository.addChannelToFavoriteList(channel.stream_id, listId, profileId, mediaMode)
    }
}
