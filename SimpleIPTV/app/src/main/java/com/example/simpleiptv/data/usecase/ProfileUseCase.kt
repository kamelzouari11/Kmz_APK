package com.example.simpleiptv.data.usecase

import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

data class ProfileSelectResult(
    val isLoading: Boolean = false,
    val error: String? = null,
    val failedProfile: ProfileEntity? = null
)

class ProfileUseCase(private val repository: IptvRepository) {

    suspend fun selectProfile(
        profileId: Int,
        profiles: List<ProfileEntity>,
        mediaMode: String
    ): ProfileSelectResult {
        repository.selectProfile(profileId)

        val count = repository.getCategoryCount(profileId)
        val profile = profiles.find { it.id == profileId }
        val hasValidUrl = profile?.url?.isNotBlank() == true

        return when {
            count == 0 && hasValidUrl -> {
                try {
                    repository.refreshDatabase(profile)
                    ProfileSelectResult(isLoading = false)
                } catch (e: Exception) {
                    ProfileSelectResult(
                        isLoading = false,
                        error = "Erreur d'importation : ${e.localizedMessage ?: "Erreur inconnue"}",
                        failedProfile = profile
                    )
                }
            }
            count == 0 && !hasValidUrl -> {
                android.util.Log.w("ProfileUseCase", "Skipping sync for profile without URL: ${profile?.profileName}")
                ProfileSelectResult(isLoading = false)
            }
            else -> ProfileSelectResult(isLoading = false)
        }
    }

    suspend fun refreshDatabase(profile: ProfileEntity) {
        repository.refreshDatabase(profile)
    }

    suspend fun canAccessChannelList(profile: ProfileEntity): Boolean =
        repository.canAccessChannelList(profile)

    suspend fun deleteProfile(profile: ProfileEntity) {
        repository.deleteProfile(profile)
    }

    suspend fun addProfile(profile: ProfileEntity) {
        repository.addProfile(profile)
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        repository.updateProfile(profile)
    }

    fun observeProfiles(): Flow<List<ProfileEntity>> = repository.allProfiles

    suspend fun purgeProfiles(
        profiles: List<ProfileEntity>,
        preferredProfileId: Int? = null
    ): List<ProfileEntity> {
        val toDelete = mutableListOf<ProfileEntity>()
        val retained = mutableListOf<ProfileEntity>()
        val seenXtream = mutableSetOf<String>()
        val seenStalker = mutableSetOf<String>()

        // En cas de doublon, conserver en priorité le profil actuellement actif.
        val orderedProfiles = profiles.sortedBy { if (it.id == preferredProfileId) 0 else 1 }
        orderedProfiles.forEach { profile ->
            val key = if (profile.type == "xtream") {
                "${profile.url}|${profile.username}|${profile.password}"
            } else {
                "${profile.url}|${profile.macAddress}"
            }

            val seenSet = if (profile.type == "xtream") seenXtream else seenStalker

            if (seenSet.contains(key)) {
                toDelete.add(profile)
            } else {
                seenSet.add(key)
                retained.add(profile)
            }
        }

        toDelete.forEach { repository.deleteProfile(it) }
        return retained
    }
}
