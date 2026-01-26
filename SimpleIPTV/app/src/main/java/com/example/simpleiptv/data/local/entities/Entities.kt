package com.example.simpleiptv.data.local.entities

import androidx.room.*

// --- IPTV ---
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val category_id: String,
    val category_name: String
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val stream_id: Int,
    val name: String,
    val stream_icon: String?
)

@Entity(tableName = "channel_category_links", primaryKeys = ["channelId", "categoryId"])
data class ChannelCategoryCrossRef(
    val channelId: Int,
    val categoryId: String
)

@Entity(tableName = "favorite_lists")
data class FavoriteListEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "channel_favorites", primaryKeys = ["channelId", "listId"])
data class ChannelFavoriteCrossRef(
    val channelId: Int,
    val listId: Int
)

@Entity(tableName = "recent_channels")
data class RecentChannelEntity(
    @PrimaryKey val channelId: Int,
    val timestamp: Long
)
