package com.example.simpleradio.data.local.entities

import androidx.room.*

// --- WEB RADIOS ---
@Entity(tableName = "radio_stations")
data class RadioStationEntity(
    @PrimaryKey val stationuuid: String,
    val name: String,
    val url: String,
    val favicon: String?,
    val country: String?,
    val tags: String?,
    val bitrate: Int?
)


@Entity(tableName = "radio_favorite_lists")
data class RadioFavoriteListEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "radio_favorites", primaryKeys = ["stationuuid", "listId"])
data class RadioFavoriteCrossRef(
    val stationuuid: String,
    val listId: Int
)

@Entity(tableName = "radio_recent")
data class RadioRecentEntity(
    @PrimaryKey val stationuuid: String,
    val timestamp: Long
)
