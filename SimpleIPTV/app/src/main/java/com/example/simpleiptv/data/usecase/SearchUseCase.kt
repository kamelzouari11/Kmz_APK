package com.example.simpleiptv.data.usecase

import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.ChannelWithProfile
import kotlinx.coroutines.flow.Flow

class SearchUseCase(private val repository: IptvRepository) {

    fun searchLocal(
        query: String,
        profileId: Int,
        mediaMode: String
    ): Flow<List<com.example.simpleiptv.data.local.entities.ChannelEntity>> {
        return repository.searchChannels(query, profileId, mediaMode)
    }

    fun searchGlobal(
        query: String,
        mediaMode: String
    ): Flow<List<ChannelWithProfile>> {
        return repository.searchChannelsAllProfiles(query, mediaMode)
    }
}
