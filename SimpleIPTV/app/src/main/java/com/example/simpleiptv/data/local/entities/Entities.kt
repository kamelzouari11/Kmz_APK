package com.example.simpleiptv.data.local.entities

import androidx.room.*

// --- IPTV ---
@Entity(
        tableName = "categories",
        primaryKeys = ["category_id", "profileId", "type"],
        indices = [Index(value = ["profileId"]), Index(value = ["type"])]
)
data class CategoryEntity(
        val category_id: String,
        val category_name: String,
        val profileId: Int,
        val type: String = "LIVE", // LIVE, VOD
        val sortOrder: Int = 0
)

@Entity(
        tableName = "channels",
        primaryKeys = ["stream_id", "profileId", "type"],
        indices = [Index(value = ["profileId"]), Index(value = ["type"]), Index(value = ["name"])]
)
data class ChannelEntity(
        val stream_id: String,
        val name: String,
        val stream_icon: String?,
        val profileId: Int,
        val type: String = "LIVE", // LIVE, VOD
        val extraParams: String? = null,
        val sortOrder: Int = 0
)

@Entity(
        tableName = "channel_category_links",
        primaryKeys = ["channelId", "categoryId", "profileId", "type"],
        indices =
                [
                        Index(value = ["categoryId", "profileId", "type"]),
                        Index(value = ["profileId"]),
                        Index(value = ["type"])]
)
data class ChannelCategoryCrossRef(
        val channelId: String,
        val categoryId: String,
        val profileId: Int,
        val type: String = "LIVE"
)

@Entity(tableName = "favorite_lists", indices = [Index(value = ["profileId"])])
data class FavoriteListEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val name: String,
        val profileId: Int,
        val type: String = "LIVE" // Which type of media this list is for
)

@Entity(
        tableName = "channel_favorites",
        primaryKeys = ["channelId", "listId", "profileId", "type"],
        indices =
                [
                        Index(value = ["listId", "profileId", "type"]),
                        Index(value = ["profileId"]),
                        Index(value = ["type"])]
)
data class ChannelFavoriteCrossRef(
        val channelId: String,
        val listId: Int,
        val profileId: Int,
        val type: String = "LIVE",
        val sortPosition: Int = 0
)

@Entity(
        tableName = "recent_channels",
        primaryKeys = ["channelId", "profileId", "type"],
        indices = [Index(value = ["profileId"]), Index(value = ["type"])]
)
data class RecentChannelEntity(
        val channelId: String,
        val timestamp: Long,
        val profileId: Int,
        val type: String = "LIVE"
)

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
