package com.example.simpleiptv.data.local.entities

import androidx.room.*

// --- IPTV ---
@Entity(
        tableName = "categories",
        primaryKeys = ["category_id", "profileId"],
        indices = [Index(value = ["profileId"])]
)
data class CategoryEntity(
        val category_id: String,
        val category_name: String,
        val profileId: Int,
        val sortOrder: Int = 0
)

@Entity(
        tableName = "channels",
        primaryKeys = ["stream_id", "profileId"],
        indices = [Index(value = ["profileId"]), Index(value = ["name"])]
)
data class ChannelEntity(
        val stream_id: String,
        val name: String,
        val stream_icon: String?,
        val profileId: Int,
        val extraParams: String? = null,
        val sortOrder: Int = 0
)

@Entity(
        tableName = "channel_category_links",
        primaryKeys = ["channelId", "categoryId", "profileId"],
        indices = [Index(value = ["categoryId", "profileId"]), Index(value = ["profileId"])]
)
data class ChannelCategoryCrossRef(
        val channelId: String,
        val categoryId: String,
        val profileId: Int
)

@Entity(tableName = "favorite_lists", indices = [Index(value = ["profileId"])])
data class FavoriteListEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val name: String,
        val profileId: Int
)

@Entity(
        tableName = "channel_favorites",
        primaryKeys = ["channelId", "listId", "profileId"],
        indices = [Index(value = ["listId", "profileId"]), Index(value = ["profileId"])]
)
data class ChannelFavoriteCrossRef(
        val channelId: String,
        val listId: Int,
        val profileId: Int,
        val sortPosition: Int = 0
)

@Entity(
        tableName = "recent_channels",
        primaryKeys = ["channelId", "profileId"],
        indices = [Index(value = ["profileId"])]
)
data class RecentChannelEntity(val channelId: String, val timestamp: Long, val profileId: Int)

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
