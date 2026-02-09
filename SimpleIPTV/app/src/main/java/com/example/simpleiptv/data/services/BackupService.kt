package com.example.simpleiptv.data.services

import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.ChannelFavoriteCrossRef
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first

class BackupService(private val dao: IptvDao) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun exportFavoritesToJson(profileId: Int): String {
        val lists = dao.getAllFavoriteLists(profileId).first()
        val backupLists =
                lists.map { list ->
                    val channels = dao.getChannelsByFavoriteList(list.id, profileId).first()
                    BackupFavoriteList(name = list.name, channels = channels)
                }
        val backup = IptvBackup(favoriteLists = backupLists)
        return moshi.adapter(IptvBackup::class.java).toJson(backup)
    }

    suspend fun importFavoritesFromJson(profileId: Int, json: String) {
        val backup = moshi.adapter(IptvBackup::class.java).fromJson(json) ?: return
        importBackupData(profileId, backup.favoriteLists)
    }

    suspend fun exportDatabaseToJson(): String {
        val profiles = dao.getAllProfiles().first()
        val profileBackups =
                profiles.map { profile ->
                    val lists = dao.getAllFavoriteLists(profile.id).first()
                    val backupLists =
                            lists.map { list ->
                                val channels =
                                        dao.getChannelsByFavoriteList(list.id, profile.id).first()
                                BackupFavoriteList(name = list.name, channels = channels)
                            }
                    ProfileBackup(
                            profile = profile.copy(id = 0, isSelected = false),
                            favoriteLists = backupLists
                    )
                }
        val backup = FullDatabaseBackup(profileBackups = profileBackups)
        return moshi.adapter(FullDatabaseBackup::class.java).toJson(backup)
    }

    suspend fun importDatabaseFromJson(json: String) {
        val backup = moshi.adapter(FullDatabaseBackup::class.java).fromJson(json) ?: return
        backup.profileBackups.forEach { profileBackup ->
            dao.insertProfile(profileBackup.profile)
            val allProfiles = dao.getAllProfiles().first()
            val newProfile =
                    allProfiles.find {
                        it.profileName == profileBackup.profile.profileName &&
                                it.url == profileBackup.profile.url
                    }
            if (newProfile != null) {
                importBackupData(newProfile.id, profileBackup.favoriteLists)
            }
        }
    }

    private suspend fun importBackupData(profileId: Int, favoriteLists: List<BackupFavoriteList>) {
        favoriteLists.forEach { backupList ->
            dao.insertFavoriteList(
                    FavoriteListEntity(name = backupList.name, profileId = profileId)
            )
            val allLists = dao.getAllFavoriteLists(profileId).first()
            val targetList = allLists.find { it.name == backupList.name }

            if (targetList != null) {
                backupList.channels.forEach { channel ->
                    dao.insertChannel(channel.copy(profileId = profileId))
                }
                backupList.channels.forEachIndexed { index, channel ->
                    dao.addChannelToFavorite(
                            ChannelFavoriteCrossRef(
                                    channel.stream_id,
                                    targetList.id,
                                    profileId,
                                    index
                            )
                    )
                }
            }
        }
    }
}
