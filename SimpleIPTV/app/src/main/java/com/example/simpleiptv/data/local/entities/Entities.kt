package com.example.simpleiptv.data.local.entities

import androidx.room.*

// --- IPTV ---
@Entity(tableName = "categories", primaryKeys = ["category_id", "profileId"])
data class CategoryEntity(val category_id: String, val category_name: String, val profileId: Int)

@Entity(tableName = "channels", primaryKeys = ["stream_id", "profileId"])
data class ChannelEntity(
        val stream_id: Int,
        val name: String,
        val stream_icon: String?,
        val profileId: Int
)

@Entity(
        tableName = "channel_category_links",
        primaryKeys = ["channelId", "categoryId", "profileId"]
)
data class ChannelCategoryCrossRef(val channelId: Int, val categoryId: String, val profileId: Int)

@Entity(tableName = "favorite_lists")
data class FavoriteListEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val name: String,
        val profileId: Int
)

@Entity(tableName = "channel_favorites", primaryKeys = ["channelId", "listId", "profileId"])
data class ChannelFavoriteCrossRef(
        val channelId: Int,
        val listId: Int,
        val profileId: Int,
        val sortPosition: Int = 0
)

@Entity(tableName = "recent_channels", primaryKeys = ["channelId", "profileId"])
data class RecentChannelEntity(val channelId: Int, val timestamp: Long, val profileId: Int)

@Entity(tableName = "profiles")
data class ProfileEntity(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        val profileName: String,
        val url: String,
        val username: String,
        val password: String,
        val isSelected: Boolean = false
)
