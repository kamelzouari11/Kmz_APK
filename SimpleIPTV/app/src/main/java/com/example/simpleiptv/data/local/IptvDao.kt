package com.example.simpleiptv.data.local

import androidx.room.*
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.model.BackupFavoriteGroup
import com.example.simpleiptv.data.model.FullDatabaseBackup
import kotlinx.coroutines.flow.Flow

/** Résultat enrichi d'une recherche globale multi-profils. */
data class ChannelWithProfile(
    val stream_id: String,
    val name: String,
    val stream_icon: String?,
    val profileId: Int,
    val type: String,
    val extraParams: String?,
    val sortOrder: Int,
    val profileName: String,
    val profileUrl: String
) {
    /** Convertit en ChannelEntity pour pouvoir lancer la lecture. */
    fun toChannelEntity() = com.example.simpleiptv.data.local.entities.ChannelEntity(
        stream_id = stream_id,
        name = name,
        stream_icon = stream_icon,
        profileId = profileId,
        type = type,
        extraParams = extraParams,
        sortOrder = sortOrder
    )
}

@Dao
interface IptvDao {

    // --- IPTV Categories ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query(
            "SELECT * FROM categories WHERE profileId = :profileId AND type = :type ORDER BY sortOrder ASC"
    )
    fun getAllCategories(profileId: Int, type: String = "LIVE"): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE profileId = :profileId AND type = :type")
    suspend fun clearCategories(profileId: Int, type: String = "LIVE")

    // --- IPTV Channels ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelCategoryLinks(links: List<ChannelCategoryCrossRef>)

    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN channel_category_links ON
            channels.stream_id = channel_category_links.channelId AND
            channels.profileId = channel_category_links.profileId AND
            channels.type = channel_category_links.type
        WHERE channel_category_links.categoryId = :categoryId
          AND channels.profileId = :profileId
          AND channels.type = :type
        ORDER BY channels.sortOrder ASC
    """
    )
    fun getChannelsByCategory(
            categoryId: String,
            profileId: Int,
            type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN channel_category_links ON
            channels.stream_id = channel_category_links.channelId AND
            channels.profileId = channel_category_links.profileId AND
            channels.type = channel_category_links.type
        WHERE channel_category_links.categoryId = :categoryId
          AND channels.profileId = :profileId
          AND channels.type = :type
        ORDER BY channels.sortOrder ASC
        LIMIT :limit OFFSET :offset
    """
    )
    fun getChannelsByCategoryPaginated(
            categoryId: String,
            profileId: Int,
            type: String = "LIVE",
            offset: Int = 0,
            limit: Int = 50
    ): Flow<List<ChannelEntity>>


    @Query("DELETE FROM channels WHERE profileId = :profileId AND type = :type")
    suspend fun clearChannels(profileId: Int, type: String = "LIVE")

    @Query("SELECT stream_id FROM channels WHERE profileId = :profileId AND type = :type")
    suspend fun getChannelIds(profileId: Int, type: String = "LIVE"): List<String>

    @Query("SELECT COUNT(*) FROM channels WHERE profileId = :profileId AND type = :type")
    suspend fun getChannelCount(profileId: Int, type: String = "LIVE"): Int

    @Query("SELECT COUNT(*) FROM categories WHERE profileId = :profileId AND type = :type")
    suspend fun getCategoryCount(profileId: Int, type: String = "LIVE"): Int

    @Query(
            "DELETE FROM channels WHERE profileId = :profileId AND type = :type AND stream_id IN (:ids)"
    )
    suspend fun deleteChannelsByIds(profileId: Int, type: String, ids: List<String>)

    @Query("DELETE FROM channel_category_links WHERE profileId = :profileId AND type = :type")
    suspend fun clearChannelCategoryLinks(profileId: Int, type: String = "LIVE")

    @Query(
        """
        SELECT categoryId FROM channel_category_links
        WHERE channelId = :channelId
          AND profileId = :profileId
          AND type = :type
        LIMIT 1
        """
    )
    suspend fun getCategoryIdForChannel(
        channelId: String,
        profileId: Int,
        type: String
    ): String?

    // --- IPTV Channels (Recherche FTS) ---
    @Transaction
    @Query(
        """
        SELECT channels.* FROM channels
        JOIN channels_fts ON channels.rowid = channels_fts.rowid
        WHERE channels.profileId = :profileId
          AND channels.type = :type
          AND channels_fts MATCH :query
        ORDER BY channels.sortOrder ASC
        """
    )
    fun searchChannelsFts(
        query: String,
        profileId: Int,
        type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    @Transaction
    @Query(
        """
        SELECT channels.* FROM channels
        JOIN channels_fts ON channels.rowid = channels_fts.rowid
        WHERE channels.profileId = :profileId
          AND channels.type = :type
          AND channels_fts MATCH :query
        ORDER BY channels.sortOrder ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchChannelsFtsPaginated(
        query: String,
        profileId: Int,
        type: String = "LIVE",
        offset: Int = 0,
        limit: Int = 50
    ): Flow<List<ChannelEntity>>




    // --- IPTV Favorite Lists ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteList(list: FavoriteListEntity): Long

    @Query(
            "SELECT * FROM favorite_lists WHERE name = :name AND profileId IS NULL AND type = :type LIMIT 1"
    )
    suspend fun findFavoriteList(
            name: String,
            type: String = "LIVE"
    ): FavoriteListEntity?

    @Query(
            "SELECT * FROM favorite_lists WHERE profileId IS NULL AND type = :type ORDER BY name COLLATE NOCASE ASC"
    )
    fun getAllFavoriteLists(type: String = "LIVE"): Flow<List<FavoriteListEntity>>

    /** Récupère les listes de favoris globales. */
    @Query(
            "SELECT * FROM favorite_lists WHERE profileId IS NULL AND type = :type ORDER BY name COLLATE NOCASE ASC"
    )
    fun getAllFavoriteListsIncludingGlobal(type: String = "LIVE"): Flow<List<FavoriteListEntity>>

    @Delete suspend fun deleteFavoriteList(list: FavoriteListEntity)

    @Query("DELETE FROM channel_favorites WHERE listId = :listId")
    suspend fun deleteFavoritesForList(listId: Int)

    // --- IPTV Channel Favorites ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelToFavorite(crossRef: ChannelFavoriteCrossRef)

    @Delete suspend fun removeChannelFromFavorite(crossRef: ChannelFavoriteCrossRef)

    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN channel_favorites ON
            channels.stream_id = channel_favorites.channelId AND
            channels.profileId = channel_favorites.profileId AND
            channels.type = channel_favorites.type
        WHERE channel_favorites.listId = :listId
          AND channels.profileId = :profileId
          AND channels.type = :type
        ORDER BY channels.sortOrder ASC
    """
    )
    fun getChannelsByFavoriteList(
            listId: Int,
            profileId: Int,
            type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN channel_favorites ON
            channels.stream_id = channel_favorites.channelId AND
            channels.profileId = channel_favorites.profileId AND
            channels.type = channel_favorites.type
        WHERE channel_favorites.listId = :listId
          AND channels.profileId = :profileId
          AND channels.type = :type
        ORDER BY channels.sortOrder ASC
        LIMIT :limit OFFSET :offset
    """
    )
    fun getChannelsByFavoriteListPaginated(
            listId: Int,
            profileId: Int,
            type: String = "LIVE",
            offset: Int = 0,
            limit: Int = 50
    ): Flow<List<ChannelEntity>>


    /** Récupère les chaînes d'une liste de favoris de TOUS les profils (pour listes multi-profils). */
    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN channel_favorites ON
            channels.stream_id = channel_favorites.channelId AND
            channels.profileId = channel_favorites.profileId AND
            channels.type = channel_favorites.type
        WHERE channel_favorites.listId = :listId
          AND channels.type = :type
        ORDER BY channel_favorites.sortPosition ASC
    """
    )
    fun getAllProfileChannelsByFavoriteList(
            listId: Int,
            type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    /** Récupère les chaînes d'une liste de favoris de TOUS les profils, paginées. */
    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN channel_favorites ON
            channels.stream_id = channel_favorites.channelId AND
            channels.profileId = channel_favorites.profileId AND
            channels.type = channel_favorites.type
        WHERE channel_favorites.listId = :listId
          AND channels.type = :type
        ORDER BY channel_favorites.sortPosition ASC
        LIMIT :limit OFFSET :offset
    """
    )
    fun getAllProfileChannelsByFavoriteListPaginated(
            listId: Int,
            type: String = "LIVE",
            offset: Int = 0,
            limit: Int = 50
    ): Flow<List<ChannelEntity>>


    @Query(
            "SELECT MAX(sortPosition) FROM channel_favorites WHERE listId = :listId AND profileId = :profileId AND type = :type"
    )
    suspend fun getMaxPositionForList(listId: Int, profileId: Int, type: String = "LIVE"): Int?

    @Query("SELECT profileId || '_' || channelId FROM channel_favorites")
    fun getAllFavoriteIdsFlow(): Flow<List<String>>

    @Query(
            "SELECT * FROM channel_favorites WHERE channelId = :channelId AND listId = :listId AND profileId = :profileId AND type = :type"
    )
    suspend fun getFavoriteCrossRef(
            channelId: String,
            listId: Int,
            profileId: Int,
            type: String = "LIVE"
    ): ChannelFavoriteCrossRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateFavoriteCrossRef(crossRef: ChannelFavoriteCrossRef)

    @Query(
            "SELECT listId FROM channel_favorites WHERE channelId = :channelId AND profileId = :profileId AND type = :type"
    )
    suspend fun getListIdsForChannel(
            channelId: String,
            profileId: Int,
            type: String = "LIVE"
    ): List<Int>

    @Query(
            "SELECT * FROM channels WHERE stream_id = :channelId AND profileId = :profileId AND type = :type"
    )
    suspend fun getChannelById(
            channelId: String,
            profileId: Int,
            type: String = "LIVE"
    ): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    // --- IPTV Recents ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentChannelEntity)

    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN recent_channels ON
            channels.stream_id = recent_channels.channelId AND
            channels.profileId = recent_channels.profileId AND
            channels.type = recent_channels.type
        WHERE channels.profileId = :profileId AND channels.type = :type
        ORDER BY recent_channels.timestamp DESC
        LIMIT 20
    """
    )
    fun getRecentChannels(profileId: Int, type: String = "LIVE"): Flow<List<ChannelEntity>>

    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN recent_channels ON
            channels.stream_id = recent_channels.channelId AND
            channels.profileId = recent_channels.profileId AND
            channels.type = recent_channels.type
        WHERE channels.profileId = :profileId AND channels.type = :type
        ORDER BY recent_channels.timestamp DESC
        LIMIT :limit OFFSET :offset
    """
    )
    fun getRecentChannelsPaginated(
            profileId: Int,
            type: String = "LIVE",
            offset: Int = 0,
            limit: Int = 50
    ): Flow<List<ChannelEntity>>


    /** Récupère les chaînes récentes de TOUS les profils, classées par timestamp. */
    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN recent_channels ON
            channels.stream_id = recent_channels.channelId AND
            channels.profileId = recent_channels.profileId AND
            channels.type = recent_channels.type
        WHERE channels.type = :type
        ORDER BY recent_channels.timestamp DESC
        LIMIT 100
    """
    )
    fun getAllRecentChannels(type: String = "LIVE"): Flow<List<ChannelEntity>>

    /** Récupère les chaînes récentes de TOUS les profils, paginées. */
    @Query(
            """
        SELECT channels.* FROM channels
        INNER JOIN recent_channels ON
            channels.stream_id = recent_channels.channelId AND
            channels.profileId = recent_channels.profileId AND
            channels.type = recent_channels.type
        WHERE channels.type = :type
        ORDER BY recent_channels.timestamp DESC
        LIMIT :limit OFFSET :offset
    """
    )
    fun getAllRecentChannelsPaginated(
            type: String = "LIVE",
            offset: Int = 0,
            limit: Int = 50
    ): Flow<List<ChannelEntity>>


    @Query(
            """
        DELETE FROM recent_channels WHERE type = :type AND rowid NOT IN (
            SELECT rowid FROM recent_channels WHERE type = :type ORDER BY timestamp DESC LIMIT 100
        )
    """
    )
    suspend fun trimRecents(type: String = "LIVE")

    @Query("DELETE FROM favorite_lists WHERE profileId = :profileId AND type = :type")
    suspend fun clearFavoriteLists(profileId: Int, type: String = "LIVE")

    @Query("DELETE FROM channel_favorites WHERE profileId = :profileId AND type = :type")
    suspend fun clearChannelFavorites(profileId: Int, type: String = "LIVE")

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId AND type = :type")
    suspend fun clearRecents(profileId: Int, type: String = "LIVE")

    @Query("DELETE FROM recent_channels WHERE type = :type")
    suspend fun clearAllRecents(type: String = "LIVE")

    // --- IPTV Profiles ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update suspend fun updateProfile(profile: ProfileEntity)

    @Delete suspend fun deleteProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles") fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT * FROM profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfile(): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: Int): ProfileEntity?

    @Query("UPDATE profiles SET isSelected = 0") suspend fun deselectAllProfiles()

    @Query("UPDATE profiles SET isSelected = 1 WHERE id = :id") suspend fun selectProfile(id: Int)

    @Query("UPDATE profiles SET isEnabled = :enabled WHERE id = :id")
    suspend fun setProfileEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE profiles SET isEnabled = 1 WHERE id = :id")
    suspend fun forceProfileEnabled(id: Int)

    @Transaction
    suspend fun syncProfileData(
            profileId: Int,
            categories: List<CategoryEntity>,
            channels: List<ChannelEntity>,
            links: List<ChannelCategoryCrossRef>,
            type: String = "LIVE"
    ) {
        clearChannelCategoryLinks(profileId, type)
        clearCategories(profileId, type)
        insertCategories(categories)

        clearChannels(profileId, type)

        channels.chunked(500).forEach { insertChannels(it) }
        links.chunked(500).forEach { insertChannelCategoryLinks(it) }
    }

    // --- Recherche globale multi-profils ---
    @androidx.room.RawQuery(observedEntities = [
        com.example.simpleiptv.data.local.entities.ChannelEntity::class,
        com.example.simpleiptv.data.local.entities.ChannelFtsEntity::class,
        com.example.simpleiptv.data.local.entities.ProfileEntity::class
    ])
    fun searchChannelsAllProfilesRaw(
        query: androidx.sqlite.db.SupportSQLiteQuery
    ): kotlinx.coroutines.flow.Flow<List<ChannelWithProfile>>

    // --- Historique de recherche (20 dernières requêtes) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: com.example.simpleiptv.data.local.entities.SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getSearchHistory(): List<com.example.simpleiptv.data.local.entities.SearchHistoryEntity>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    /** Garde uniquement les 20 entrées les plus récentes. */
    @Query(
        "DELETE FROM search_history WHERE query NOT IN " +
        "(SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 20)"
    )
    suspend fun trimSearchHistory()

    @Query("DELETE FROM channel_favorites")
    suspend fun clearEveryChannelFavorite()

    @Query("DELETE FROM favorite_lists")
    suspend fun clearEveryFavoriteList()

    @Query("DELETE FROM recent_channels")
    suspend fun clearEveryRecentChannel()

    @Query("DELETE FROM channel_category_links")
    suspend fun clearEveryChannelCategoryLink()

    @Query("DELETE FROM categories")
    suspend fun clearEveryCategory()

    @Query("DELETE FROM channels")
    suspend fun clearEveryChannel()

    @Query("DELETE FROM search_history")
    suspend fun clearEverySearchHistoryEntry()

    @Query("DELETE FROM profiles")
    suspend fun clearEveryProfile()

    /** Remplace atomiquement toutes les données gérées par le backup GitHub. */
    @Transaction
    suspend fun replaceAllFromBackup(backup: FullDatabaseBackup) {
        clearEveryChannelFavorite()
        clearEveryFavoriteList()
        clearEveryRecentChannel()
        clearEveryChannelCategoryLink()
        clearEveryCategory()
        clearEveryChannel()
        clearEverySearchHistoryEntry()
        clearEveryProfile()

        // Version 3 : groupes explicites. Le second terme assure la compatibilité v2.
        val groups = (
            backup.globalFavoriteGroups +
                backup.profileBackups.flatMap { profileBackup ->
                    profileBackup.favoriteLists.map {
                        BackupFavoriteGroup(it.name, it.type)
                    }
                }
        ).map { BackupFavoriteGroup(it.name.trim(), it.type) }
            .filter { it.name.isNotEmpty() }
            .distinctBy { it.name to it.type }

        val groupIds = mutableMapOf<Pair<String, String>, Int>()
        groups.forEach { group ->
            insertFavoriteList(
                FavoriteListEntity(name = group.name, profileId = null, type = group.type)
            )
            findFavoriteList(group.name, group.type)?.let {
                groupIds[group.name to group.type] = it.id
            }
        }

        val selectedIndex = backup.profileBackups.indexOfFirst { it.profile.isSelected }
            .takeIf { it >= 0 } ?: 0

        backup.profileBackups.forEachIndexed { profileIndex, profileBackup ->
            val restoredProfile = profileBackup.profile.copy(
                id = 0,
                isSelected = profileIndex == selectedIndex
            )
            val restoredProfileId = insertProfile(restoredProfile).toInt()

            profileBackup.favoriteLists.forEach favoriteLoop@{ favoriteBackup ->
                val cleanName = favoriteBackup.name.trim()
                val listId = groupIds[cleanName to favoriteBackup.type] ?: return@favoriteLoop
                favoriteBackup.channels.forEachIndexed { channelIndex, channel ->
                    insertChannel(
                        channel.copy(
                            rowid = 0,
                            profileId = restoredProfileId,
                            type = favoriteBackup.type
                        )
                    )
                    addChannelToFavorite(
                        ChannelFavoriteCrossRef(
                            channelId = channel.stream_id,
                            listId = listId,
                            profileId = restoredProfileId,
                            type = favoriteBackup.type,
                            sortPosition = channelIndex
                        )
                    )
                }
            }
        }

        val historyTimestamp = System.currentTimeMillis()
        backup.searchHistory.distinct().take(20).forEachIndexed { index, query ->
            insertSearchHistory(
                SearchHistoryEntity(query = query, timestamp = historyTimestamp - index)
            )
        }
    }

    @Query("SELECT profileId FROM categories GROUP BY profileId HAVING COUNT(*) > 0")
    fun getLoadedProfileIdsFlow(): Flow<List<Int>>
}
