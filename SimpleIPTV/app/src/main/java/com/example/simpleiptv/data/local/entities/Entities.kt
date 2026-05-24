package com.example.simpleiptv.data.local.entities

import androidx.compose.runtime.Immutable
import androidx.room.*
import com.squareup.moshi.JsonClass

// --- IPTV ---
@Immutable
@Entity(
        tableName = "categories",
        primaryKeys = ["category_id", "profileId", "type"],
        indices = [Index(value = ["profileId", "type"])]
)
data class CategoryEntity(
        val category_id: String,
        val category_name: String,
        val profileId: Int,
        val type: String = "LIVE", // LIVE, VOD
        val sortOrder: Int = 0
)

@Immutable
@JsonClass(generateAdapter = true)
@Entity(
        tableName = "channels",
        indices = [
            Index(value = ["stream_id", "profileId", "type"], unique = true),
            Index(value = ["profileId", "type"]),
            Index(value = ["name"])
        ]
)
data class ChannelEntity(
        @PrimaryKey(autoGenerate = true) val rowid: Int = 0,
        val stream_id: String,
        val name: String,
        val stream_icon: String?,
        val profileId: Int,
        val type: String = "LIVE", // LIVE, VOD
        val extraParams: String? = null,
        val sortOrder: Int = 0
)

/** Table FTS pour la recherche ultra-rapide sur le nom des chaînes. */
@Entity(tableName = "channels_fts")
@Fts4(contentEntity = ChannelEntity::class)
data class ChannelFtsEntity(
    val name: String
)

@Immutable
@Entity(
        tableName = "channel_category_links",
        primaryKeys = ["channelId", "categoryId", "profileId", "type"],
        indices = [Index(value = ["categoryId", "profileId", "type"])]
)
data class ChannelCategoryCrossRef(
        val channelId: String,
        val categoryId: String,
        val profileId: Int,
        val type: String = "LIVE"
)

@Immutable
@Entity(tableName = "favorite_lists", indices = [Index(value = ["profileId"])])
data class FavoriteListEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val name: String,
        val profileId: Int?,  // null = liste multi-profils, sinon liée à un profil spécifique
        val type: String = "LIVE" // Which type of media this list is for
)

@Immutable
@Entity(
        tableName = "channel_favorites",
        primaryKeys = ["channelId", "listId", "profileId", "type"],
        indices = [
            Index(value = ["listId", "profileId", "type"]),
            Index(value = ["channelId", "profileId", "type"])
        ]
)
data class ChannelFavoriteCrossRef(
        val channelId: String,
        val listId: Int,
        val profileId: Int,
        val type: String = "LIVE",
        val sortPosition: Int = 0
)

@Immutable
@Entity(
        tableName = "recent_channels",
        primaryKeys = ["channelId", "profileId", "type"],
        indices = [Index(value = ["profileId", "type"])]
)
data class RecentChannelEntity(
        val channelId: String,
        val timestamp: Long,
        val profileId: Int,
        val type: String = "LIVE"
)

@Immutable
@JsonClass(generateAdapter = true)
@Entity(tableName = "profiles")
data class ProfileEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val profileName: String,
        val url: String,
        val username: String,
        val password: String,
        val macAddress: String? = null,
        val type: String = "xtream", // xtream, stalker
        val isSelected: Boolean = false
)

/** Historique global des 20 dernières recherches textuelles. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
        @PrimaryKey val query: String,
        val timestamp: Long = System.currentTimeMillis()
)
