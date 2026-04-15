package com.example.simpleiptv.data

import com.example.simpleiptv.data.api.XtreamApi
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.services.BackupService
import com.example.simpleiptv.data.services.StreamService
import com.example.simpleiptv.data.services.SyncService
import kotlinx.coroutines.flow.Flow

class IptvRepository(private val api: XtreamApi, private val dao: IptvDao) {

        // Sub-services
        private val backupService = BackupService(dao)
        private val syncService = SyncService(dao)
        private val streamService = StreamService(dao)

        // --- Basic DAO Access ---
        fun getCategories(profileId: Int, type: String = "LIVE"): Flow<List<CategoryEntity>> =
                dao.getAllCategories(profileId, type)
        fun getFavoriteLists(
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<FavoriteListEntity>> = dao.getAllFavoriteLists(profileId, type)
        fun getRecentChannels(profileId: Int, type: String = "LIVE"): Flow<List<ChannelEntity>> =
                dao.getRecentChannels(profileId, type)
        fun getChannelsByCategory(
                categoryId: String,
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> = dao.getChannelsByCategory(categoryId, profileId, type)
        fun getChannelsByFavoriteList(
                listId: Int,
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> = dao.getChannelsByFavoriteList(listId, profileId, type)
        fun searchChannels(
                query: String,
                profileId: Int,
                type: String = "LIVE"
        ): Flow<List<ChannelEntity>> = dao.searchChannels(query, profileId, type)

        /**
         * Recherche intelligente multi-profils.
         * Les mots de [query] sont mis en séquence : "abc xyz" → LIKE '%abc%xyz%'
         * Chaque résultat contient le nom de la chaîne + le nom du profil + l'URL du serveur.
         */
        fun searchChannelsAllProfiles(
                query: String,
                type: String = "LIVE"
        ): Flow<List<com.example.simpleiptv.data.local.ChannelWithProfile>> {
                val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                // Construit le pattern séquentiel : %tok1%tok2%teg%
                val pattern = "%" + tokens.joinToString("%") + "%"
                val sql = androidx.sqlite.db.SimpleSQLiteQuery(
                        """
                        SELECT
                            c.stream_id, c.name, c.stream_icon,
                            c.profileId, c.type, c.extraParams, c.sortOrder,
                            p.profileName, p.url AS profileUrl
                        FROM channels c
                        INNER JOIN profiles p ON c.profileId = p.id
                        WHERE c.type = ?
                          AND c.name LIKE ? ESCAPE '\'
                        ORDER BY p.profileName ASC, c.sortOrder ASC
                        LIMIT 200
                        """.trimIndent(),
                        arrayOf(type, pattern)
                )
                return dao.searchChannelsAllProfilesRaw(sql)
        }
        suspend fun getChannelCount(profileId: Int, type: String = "LIVE"): Int =
                dao.getChannelCount(profileId, type)
        suspend fun getCategoryCount(profileId: Int, type: String = "LIVE"): Int =
                dao.getCategoryCount(profileId, type)

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
        suspend fun addFavoriteList(name: String, profileId: Int, type: String = "LIVE") =
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
                dao.clearCategories(profile.id)
                dao.clearChannels(profile.id)
                dao.clearChannelCategoryLinks(profile.id)
                dao.clearFavoriteLists(profile.id)
                dao.clearChannelFavorites(profile.id)
                dao.clearRecents(profile.id)
                dao.deleteProfile(profile)
        }
        suspend fun selectProfile(profileId: Int) {
                dao.deselectAllProfiles()
                dao.selectProfile(profileId)
        }

        // --- Delegated to SyncService ---
        suspend fun refreshDatabase(profile: ProfileEntity) = syncService.refreshDatabase(profile)

        // --- Delegated to StreamService ---
        suspend fun getStreamUrl(profile: ProfileEntity, channelId: String): String =
                streamService.getStreamUrl(profile, channelId)

        // --- Delegated to BackupService ---
        suspend fun exportFavoritesToJson(profileId: Int): String =
                backupService.exportFavoritesToJson(profileId)
        suspend fun importFavoritesFromJson(profileId: Int, json: String) =
                backupService.importFavoritesFromJson(profileId, json)
        suspend fun exportDatabaseToJson(): String = backupService.exportDatabaseToJson()
        suspend fun importDatabaseFromJson(json: String) =
                backupService.importDatabaseFromJson(json)
}
