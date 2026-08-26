package com.example.simpleiptv.data.usecase

import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.ChannelWithProfile
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SearchUseCase(private val repository: IptvRepository) {

    private val workingCategoryCache = ConcurrentHashMap<ChannelGroupKey, Long>()
    private val failedChannelCache = ConcurrentHashMap<FailedChannelKey, Long>()

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

    suspend fun searchWorkingLocal(
        query: String,
        profileId: Int,
        mediaMode: String,
        profiles: List<ProfileEntity>,
        onProgress: (List<ChannelEntity>, Int, Int) -> Unit
    ): List<ChannelEntity> {
        val candidates = repository.searchChannels(query, profileId, mediaMode).first()
        return keepOnlyWorking(candidates, { it }, profiles, onProgress)
    }

    suspend fun searchWorkingGlobal(
        query: String,
        mediaMode: String,
        profiles: List<ProfileEntity>,
        onProgress: (List<ChannelWithProfile>, Int, Int) -> Unit
    ): List<ChannelWithProfile> {
        val candidates = repository.searchChannelsAllProfiles(query, mediaMode).first()
        return keepOnlyWorking(candidates, { it.toChannelEntity() }, profiles, onProgress)
    }

    private data class ChannelGroupKey(
        val profileId: Int,
        val type: String,
        val categoryId: String,
        val profileSignature: String
    )

    private data class FailedChannelKey(
        val group: ChannelGroupKey,
        val streamId: String,
        val extraParams: String?
    )

    fun invalidateProfile(profileId: Int) {
        workingCategoryCache.keys
            .filter { it.profileId == profileId }
            .forEach { workingCategoryCache.remove(it) }
        failedChannelCache.keys
            .filter { it.group.profileId == profileId }
            .forEach { failedChannelCache.remove(it) }
        repository.invalidateChannelTests(profileId)
    }

    private suspend fun <T> keepOnlyWorking(
        candidates: List<T>,
        toChannel: (T) -> ChannelEntity,
        profiles: List<ProfileEntity>,
        onProgress: (List<T>, Int, Int) -> Unit
    ): List<T> {
        val profilesById = profiles.associateBy { it.id }
        val groupedCandidates = linkedMapOf<ChannelGroupKey, MutableList<T>>()
        val now = System.currentTimeMillis()
        removeExpiredCacheEntries(now)

        candidates.forEach { item ->
            val channel = toChannel(item)
            val profile = profilesById[channel.profileId]
            val categoryId = repository.getCategoryIdForChannel(channel)
                // Sans catégorie, la chaîne forme son propre groupe et sera testée individuellement.
                ?: "__channel__${channel.stream_id}"
            val profileSignature = profile?.let {
                "${it.url}|${it.username}|${it.password}|${it.macAddress}"
            } ?: "missing"
            val key = ChannelGroupKey(
                channel.profileId,
                channel.type,
                categoryId,
                profileSignature
            )
            groupedCandidates.getOrPut(key) { mutableListOf() }.add(item)
        }

        val accepted = mutableSetOf<T>()
        var processed = 0
        onProgress(emptyList(), processed, candidates.size)

        groupedCandidates.forEach groupLoop@{ (groupKey, group) ->
            val failedInGroup = mutableSetOf<T>()
            group.forEach { item ->
                val channel = toChannel(item)
                val failureKey = FailedChannelKey(groupKey, channel.stream_id, channel.extraParams)
                if ((failedChannelCache[failureKey] ?: 0L) > now) failedInGroup += item
            }

            if ((workingCategoryCache[groupKey] ?: 0L) > now) {
                accepted += group.filterNot { it in failedInGroup }
                processed += group.size
                onProgress(candidates.filter { it in accepted }, processed, candidates.size)
                return@groupLoop
            }

            // Test séquentiel. Dès qu'une chaîne fonctionne, les chaînes du même
            // couple (profil, catégorie) sont admises, sauf celles déjà testées en échec.
            for (item in group) {
                if (item in failedInGroup) continue
                val channel = toChannel(item)
                val profile = profilesById[channel.profileId]
                val isWorking = profile != null && repository.isChannelWorking(profile, channel)
                val failureKey = FailedChannelKey(groupKey, channel.stream_id, channel.extraParams)

                if (isWorking) {
                    workingCategoryCache[groupKey] =
                        System.currentTimeMillis() + CATEGORY_SUCCESS_TTL_MS
                    failedChannelCache.remove(failureKey)
                    accepted += group.filterNot { it in failedInGroup }
                    break
                }
                failedInGroup += item
                failedChannelCache[failureKey] =
                    System.currentTimeMillis() + CHANNEL_FAILURE_TTL_MS
            }

            processed += group.size
            onProgress(candidates.filter { it in accepted }, processed, candidates.size)
        }

        return candidates.filter { it in accepted }
    }

    private fun removeExpiredCacheEntries(now: Long) {
        workingCategoryCache.entries
            .filter { it.value <= now }
            .forEach { workingCategoryCache.remove(it.key, it.value) }
        failedChannelCache.entries
            .filter { it.value <= now }
            .forEach { failedChannelCache.remove(it.key, it.value) }
    }

    private companion object {
        const val CATEGORY_SUCCESS_TTL_MS = 30 * 60_000L
        const val CHANNEL_FAILURE_TTL_MS = 5 * 60_000L
    }

}
