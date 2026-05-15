package com.example.simpleiptv.data

import com.example.simpleiptv.data.api.XtreamApi
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.services.BackupService
import com.example.simpleiptv.data.services.StreamService
import com.example.simpleiptv.data.services.SyncService
import kotlinx.coroutines.flow.Flow

class IptvRepository(private val dao: IptvDao) {

        // Sub-services
        private val backupService = BackupService(dao)
        private val syncService = SyncService(dao)
        private val streamService = StreamService(dao)

        /**
         * Crée et retourne l'API Xtream pour un profil donné.
         * Chaque profil a sa propre URL de serveur.
         */
        fun getXtreamApi(profile: ProfileEntity): XtreamApi {
                return XtreamClient.create(profile.url)
        }

        /**
         * Crée un profil par défaut si aucun profil n'existe.
         * Appelé au premier lancement de l'application.
         */
        suspend fun createDefaultProfileIfNeeded() {
                val count = dao.getProfileCount()
                if (count == 0) {
                        val defaultProfile = ProfileEntity(
                                profileName = "Mon Serveur IPTV",
                                url = "",
                                username = "",
                                password = "",
                                type = "xtream",
                                isSelected = true
                        )
                        dao.insertProfile(defaultProfile)
                }
        }

        // --- Basic DAO Access ---
        fun getCategories(profileId: Int, type: String = "LIVE"): Flow<List<CategoryEntity>> =
                dao.getAllCategories(profileId, type)
        fun getFavoriteLists(
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<FavoriteListEntity>> = dao.getAllFavoriteLists(profileId, type)
        fun getAllFavoriteListsIncludingGlobal(
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<FavoriteListEntity>> = dao.getAllFavoriteListsIncludingGlobal(profileId, type)
        fun getChannelsByFavoriteList(
                listId: Int,
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> = dao.getChannelsByFavoriteList(listId, profileId, type)

        fun getChannelsByFavoriteListPaginated(
                listId: Int,
                profileId: Int,
                type: String = "LIVE",
                offset: Int = 0,
                limit: Int = 50
        ): Flow<List<ChannelEntity>> = dao.getChannelsByFavoriteListPaginated(listId, profileId, type, offset, limit)


        fun getAllProfileChannelsByFavoriteList(
                listId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> = dao.getAllProfileChannelsByFavoriteList(listId, type)

        fun getAllProfileChannelsByFavoriteListPaginated(
                listId: Int,
                type: String = "LIVE",
                offset: Int = 0,
                limit: Int = 50
        ): Flow<List<ChannelEntity>> = dao.getAllProfileChannelsByFavoriteListPaginated(listId, type, offset, limit)


        fun getRecentChannels(profileId: Int, type: String = "LIVE"): Flow<List<ChannelEntity>> =
                dao.getRecentChannels(profileId, type)

        fun getRecentChannelsPaginated(
                profileId: Int,
                type: String = "LIVE",
                offset: Int = 0,
                limit: Int = 50
        ): Flow<List<ChannelEntity>> = dao.getRecentChannelsPaginated(profileId, type, offset, limit)


        fun getAllRecentChannels(type: String = "LIVE"): Flow<List<ChannelEntity>> =
                dao.getAllRecentChannels(type)

        fun getAllRecentChannelsPaginated(
                type: String = "LIVE",
                offset: Int = 0,
                limit: Int = 50
        ): Flow<List<ChannelEntity>> = dao.getAllRecentChannelsPaginated(type, offset, limit)

        fun getChannelsByCategory(
                categoryId: String,
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> = dao.getChannelsByCategory(categoryId, profileId, type)

        fun getChannelsByCategoryPaginated(
                categoryId: String,
                profileId: Int,
                type: String = "LIVE",
                offset: Int = 0,
                limit: Int = 50
        ): Flow<List<ChannelEntity>> = dao.getChannelsByCategoryPaginated(categoryId, profileId, type, offset, limit)

        suspend fun getChannelCount(profileId: Int, type: String = "LIVE"): Int =
                dao.getChannelCount(profileId, type)
        suspend fun getCategoryCount(profileId: Int, type: String = "LIVE"): Int =
                dao.getCategoryCount(profileId, type)

        fun searchChannels(
                query: String,
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> {
            // FTS4 : chaque mot reçoit le suffixe '*' pour le prefix-match
            val ftsQuery = buildFtsQuery(query)
            return if (ftsQuery.isNotEmpty()) {
                dao.searchChannelsFts(ftsQuery, profileId, type)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }

        fun searchChannelsPaginated(
                query: String,
                profileId: Int,
                type: String = "LIVE",
                offset: Int = 0,
                limit: Int = 50
        ): Flow<List<ChannelEntity>> {
            val ftsQuery = buildFtsQuery(query)
            return if (ftsQuery.isNotEmpty()) {
                dao.searchChannelsFtsPaginated(ftsQuery, profileId, type, offset, limit)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }

        /**
         * Recherche intelligente multi-profils via FTS4.
         * Chaque résultat contient le nom de la chaîne + le nom du profil + l'URL du serveur.
         */
        fun searchChannelsAllProfiles(
                query: String,
                type: String = "LIVE"
        ): Flow<List<com.example.simpleiptv.data.local.ChannelWithProfile>> {
                val ftsQuery = buildFtsQuery(query)
                if (ftsQuery.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
                val sql = androidx.sqlite.db.SimpleSQLiteQuery(
                        """
                        SELECT
                            c.stream_id, c.name, c.stream_icon,
                            c.profileId, c.type, c.extraParams, c.sortOrder,
                            p.profileName, p.url AS profileUrl
                        FROM channels c
                        INNER JOIN channels_fts fts ON c.rowid = fts.rowid
                        INNER JOIN profiles p ON c.profileId = p.id
                        WHERE c.type = ?
                          AND channels_fts MATCH ?
                          AND p.isEnabled = 1
                        ORDER BY p.profileName ASC, c.sortOrder ASC
                        LIMIT 200
                        """.trimIndent(),
                        arrayOf(type, ftsQuery)
                )
                return dao.searchChannelsAllProfilesRaw(sql)
        }


        /**
         * Construit une requête FTS4 à partir de l'entrée utilisateur.
         * "abc xyz" → "abc* xyz*"  (prefix-match sur chaque mot, tous doivent être présents).
         * Les caractères spéciaux FTS (guillemets, parenthèses…) sont supprimés.
         */
        private fun buildFtsQuery(raw: String): String {
            return raw.trim()
                .replace(Regex("[\"(){}\\[\\]*^]"), "")   // nettoyage des opérateurs FTS
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .joinToString(" ") { "$it*" }
        }

        // --- Historique de recherche ---
        suspend fun getSearchHistory(): List<String> =
                dao.getSearchHistory().map { it.query }

        suspend fun addToSearchHistory(query: String) {
                val q = query.trim()
                if (q.isEmpty()) return
                dao.insertSearchHistory(
                        com.example.simpleiptv.data.local.entities.SearchHistoryEntity(query = q)
                )
                dao.trimSearchHistory()
        }

        suspend fun clearSearchHistory() = dao.clearSearchHistory()

        // --- Favorites Logic ---
        suspend fun addFavoriteList(name: String, profileId: Int?, type: String = "LIVE") =
                dao.insertFavoriteList(
                        FavoriteListEntity(name = name, profileId = profileId, type = type)
                )
        suspend fun removeFavoriteList(list: FavoriteListEntity) = dao.deleteFavoriteList(list)
        suspend fun addChannelToFavoriteList(
                streamId: String,
                listId: Int,
                profileId: Int,
                type: String = "LIVE"
        ) {
                val maxPos = dao.getMaxPositionForList(listId, profileId, type) ?: -1
                dao.addChannelToFavorite(
                        ChannelFavoriteCrossRef(streamId, listId, profileId, type, maxPos + 1)
                )
        }

        val allFavoriteIdsFlow: Flow<List<String>> = dao.getAllFavoriteIdsFlow()

        suspend fun toggleChannelFavorite(
                channelId: String,
                listId: Int,
                profileId: Int,
                type: String = "LIVE"
        ) {
                val currentLists = dao.getListIdsForChannel(channelId, profileId, type)
                if (currentLists.contains(listId)) {
                        dao.removeChannelFromFavorite(
                                ChannelFavoriteCrossRef(channelId, listId, profileId, type)
                        )
                } else {
                        val maxPos = dao.getMaxPositionForList(listId, profileId, type) ?: -1
                        dao.addChannelToFavorite(
                                ChannelFavoriteCrossRef(
                                        channelId,
                                        listId,
                                        profileId,
                                        type,
                                        maxPos + 1
                                )
                        )
                }
        }

        // --- Recents Logic ---
        suspend fun addToRecents(channelId: String, profileId: Int, type: String = "LIVE") {
                dao.insertRecent(
                        RecentChannelEntity(channelId, System.currentTimeMillis(), profileId, type)
                )
                dao.trimRecents(profileId, type)
        }

        suspend fun clearRecents(profileId: Int, type: String = "LIVE") =
                dao.clearRecents(profileId, type)

        // --- Profiles Logic ---
        val allProfiles: Flow<List<ProfileEntity>> = dao.getAllProfiles()
        val loadedProfileIds: Flow<List<Int>> = dao.getLoadedProfileIdsFlow()
        suspend fun getSelectedProfile(): ProfileEntity? = dao.getSelectedProfile()
        suspend fun addProfile(profile: ProfileEntity) = dao.insertProfile(profile)
        suspend fun updateProfile(profile: ProfileEntity) = dao.updateProfile(profile)
        suspend fun deleteProfile(profile: ProfileEntity) {
                // Nettoyage LIVE
                dao.clearCategories(profile.id, "LIVE")
                dao.clearChannels(profile.id, "LIVE")
                dao.clearChannelCategoryLinks(profile.id, "LIVE")
                dao.clearFavoriteLists(profile.id, "LIVE")
                dao.clearChannelFavorites(profile.id, "LIVE")
                dao.clearRecents(profile.id, "LIVE")
                // Nettoyage VOD
                dao.clearCategories(profile.id, "VOD")
                dao.clearChannels(profile.id, "VOD")
                dao.clearChannelCategoryLinks(profile.id, "VOD")
                dao.clearFavoriteLists(profile.id, "VOD")
                dao.clearChannelFavorites(profile.id, "VOD")
                dao.clearRecents(profile.id, "VOD")
                dao.deleteProfile(profile)
        }
        suspend fun selectProfile(profileId: Int) {
                dao.deselectAllProfiles()
                dao.selectProfile(profileId)
                dao.forceProfileEnabled(profileId) // actif => toujours ON
        }

        suspend fun setProfileEnabled(profileId: Int, enabled: Boolean) {
                dao.setProfileEnabled(profileId, enabled)
        }

        // --- Delegated to SyncService ---
        suspend fun refreshDatabase(profile: ProfileEntity) = syncService.refreshDatabase(profile)

        // --- Delegated to StreamService ---
        suspend fun getStreamUrl(profile: ProfileEntity, channel: ChannelEntity): String {
        return streamService.getStreamUrl(profile, channel)
        }

        // --- Delegated to BackupService ---
        suspend fun exportFavoritesToJson(profileId: Int): String =
                backupService.exportFavoritesToJson(profileId)
        suspend fun importFavoritesFromJson(profileId: Int, json: String) =
                backupService.importFavoritesFromJson(profileId, json)
        suspend fun exportDatabaseToJson(): String = backupService.exportDatabaseToJson()
        suspend fun importDatabaseFromJson(json: String) =
                backupService.importDatabaseFromJson(json)
}
