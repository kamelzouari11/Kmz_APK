package com.example.simpleiptv.data.services

import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.ChannelFavoriteCrossRef
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.data.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first

class BackupService(private val dao: IptvDao) {
    private val moshi = Moshi.Builder().build()

    suspend fun exportFavoritesToJson(profileId: Int): String {
        val lists =
                (dao.getAllFavoriteLists("LIVE").first() +
                        dao.getAllFavoriteLists("VOD").first())
        val backupLists =
                lists.map { list ->
                    val channels =
                            dao.getChannelsByFavoriteList(list.id, profileId, list.type).first()
                    BackupFavoriteList(name = list.name, type = list.type, channels = channels)
                }
        val backup = IptvBackup(favoriteLists = backupLists)
        return moshi.adapter(IptvBackup::class.java).toJson(backup)
    }

    suspend fun importFavoritesFromJson(profileId: Int, json: String) {
        val backup = moshi.adapter(IptvBackup::class.java).fromJson(json) ?: return
        importBackupData(profileId, backup.favoriteLists)
    }

    /**
     * Exporte les profils, favoris et historique UNIQUEMENT.
     * Les catalogues catégories/chaînes, volumineux et récupérables depuis les
     * serveurs IPTV, ne sont pas envoyés sur GitHub. Après un Download, ils sont
     * rechargés pour le profil actif puis via « Load All » pour les autres.
     */
    suspend fun exportDatabaseToJson(): String {
        val profiles = dao.getAllProfiles().first()
        val globalLists =
                (dao.getAllFavoriteLists("LIVE").first() +
                        dao.getAllFavoriteLists("VOD").first())
        val profileBackups =
                profiles.map { profile ->
                    val backupLists =
                            globalLists.mapNotNull { list ->
                                val channels =
                                        dao.getChannelsByFavoriteList(
                                                        list.id,
                                                        profile.id,
                                                        list.type
                                                )
                                                .first()
                                if (channels.isEmpty()) null
                                else BackupFavoriteList(
                                            name = list.name,
                                            type = list.type,
                                            channels = channels
                                    )
                            }
                    ProfileBackup(
                            profile = profile.copy(id = 0),
                            favoriteLists = backupLists
                    )
                }
        val searchHistoryEntries = dao.getSearchHistory().map { it.query }
        val backup = FullDatabaseBackup(
                profileBackups = profileBackups,
                searchHistory = searchHistoryEntries,
                globalFavoriteGroups = globalLists.map {
                    BackupFavoriteGroup(name = it.name, type = it.type)
                }
        )
        return moshi.adapter(FullDatabaseBackup::class.java).toJson(backup)
    }

    /**
     * Remplace toutes les données locales par le contenu du backup GitHub.
     * Le JSON est validé avant l'effacement et le remplacement est transactionnel.
     */
    suspend fun importDatabaseFromJson(json: String) {
        val backup = moshi.adapter(FullDatabaseBackup::class.java).fromJson(json)
                ?: throw IllegalArgumentException("Backup GitHub invalide")
        dao.replaceAllFromBackup(backup)
    }

    private suspend fun importBackupData(profileId: Int, favoriteLists: List<BackupFavoriteList>) {
        favoriteLists.forEach { backupList ->
            val mediaType = backupList.type
            val targetList = getOrCreateFavoriteList(
                    name = backupList.name,
                    type = mediaType
            )

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
                                    mediaType,
                                    index
                            )
                    )
                }
            }
        }
    }

    private suspend fun getOrCreateFavoriteList(
            name: String,
            type: String
    ): FavoriteListEntity? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null
        dao.findFavoriteList(cleanName, type)?.let { return it }
        dao.insertFavoriteList(
                FavoriteListEntity(
                        name = cleanName,
                        profileId = null,
                        type = type
                )
        )
        return dao.findFavoriteList(cleanName, type)
    }
}
