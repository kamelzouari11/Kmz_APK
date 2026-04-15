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
        val lists =
                (dao.getAllFavoriteLists(profileId, "LIVE").first() +
                        dao.getAllFavoriteLists(profileId, "VOD").first())
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
     * Les catégories/chaînes NE SONT PAS exportées — elles restent en cache local
     * et ne sont rechargées que lors d'un Sync manuel ou pour un nouveau profil.
     */
    suspend fun exportDatabaseToJson(): String {
        val profiles = dao.getAllProfiles().first()
        val profileBackups =
                profiles.map { profile ->
                    val lists =
                            (dao.getAllFavoriteLists(profile.id, "LIVE").first() +
                                    dao.getAllFavoriteLists(profile.id, "VOD").first())
                    val backupLists =
                            lists.map { list ->
                                val channels =
                                        dao.getChannelsByFavoriteList(
                                                        list.id,
                                                        profile.id,
                                                        list.type
                                                )
                                                .first()
                                BackupFavoriteList(
                                        name = list.name,
                                        type = list.type,
                                        channels = channels
                                )
                            }
                    ProfileBackup(
                            profile = profile.copy(id = 0, isSelected = false),
                            favoriteLists = backupLists
                    )
                }
        val searchHistoryEntries = dao.getSearchHistory().map { it.query }
        val backup = FullDatabaseBackup(profileBackups = profileBackups, searchHistory = searchHistoryEntries)
        return moshi.adapter(FullDatabaseBackup::class.java).toJson(backup)
    }

    /**
     * Importe les profils, favoris et historique depuis un backup GitHub.
     * 
     * IMPORTANT : Les catégories et chaînes en cache local sont PRÉSERVÉES.
     * Seuls les profils (et leurs favoris) qui n'existent pas encore sont ajoutés.
     * Les profils existants (même URL+user ou URL+MAC) sont mis à jour sans toucher
     * à leur base de données de chaînes.
     */
    suspend fun importDatabaseFromJson(json: String) {
        val backup = moshi.adapter(FullDatabaseBackup::class.java).fromJson(json) ?: return
        
        val existingProfiles = dao.getAllProfiles().first()

        backup.profileBackups.forEach { profileBackup ->
            val incoming = profileBackup.profile
            
            // Chercher un profil existant avec la même identité
            val existingProfile = existingProfiles.find { existing ->
                if (incoming.type == "stalker") {
                    existing.url == incoming.url && existing.macAddress == incoming.macAddress
                } else {
                    existing.url == incoming.url && existing.username == incoming.username && existing.password == incoming.password
                }
            }

            val targetProfileId: Int

            if (existingProfile != null) {
                // Profil déjà existant : mettre à jour le nom seulement, GARDER la base intacte
                dao.updateProfile(existingProfile.copy(profileName = incoming.profileName))
                targetProfileId = existingProfile.id
            } else {
                // Nouveau profil : l'insérer (la base de chaînes sera chargée automatiquement
                // par le ViewModel grâce au check getCategoryCount == 0)
                dao.insertProfile(incoming)
                val allProfiles = dao.getAllProfiles().first()
                val newProfile = allProfiles.find {
                    if (incoming.type == "stalker") {
                        it.url == incoming.url && it.macAddress == incoming.macAddress
                    } else {
                        it.url == incoming.url && it.username == incoming.username && it.password == incoming.password
                    }
                }
                targetProfileId = newProfile?.id ?: return@forEach
            }

            // Importer les favoris (fusionner sans écraser les existants)
            importBackupData(targetProfileId, profileBackup.favoriteLists)
        }

        // Restaurer l'historique de recherche (fusionner)
        backup.searchHistory.forEach { query ->
            dao.insertSearchHistory(
                com.example.simpleiptv.data.local.entities.SearchHistoryEntity(
                    query = query,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        dao.trimSearchHistory()
    }

    private suspend fun importBackupData(profileId: Int, favoriteLists: List<BackupFavoriteList>) {
        favoriteLists.forEach { backupList ->
            val mediaType = backupList.type
            dao.insertFavoriteList(
                    FavoriteListEntity(
                            name = backupList.name,
                            profileId = profileId,
                            type = mediaType
                    )
            )
            val allLists = dao.getAllFavoriteLists(profileId, mediaType).first()
            val targetList = allLists.find { it.name == backupList.name && it.type == mediaType }

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
}
