package com.example.simpleiptv.data.model

import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IptvBackup(
        val version: Int = 1,
        val date: Long = System.currentTimeMillis(),
        val favoriteLists: List<BackupFavoriteList>
)

@JsonClass(generateAdapter = true)
data class BackupFavoriteList(
        val name: String,
        val type: String = "LIVE",
        val channels: List<ChannelEntity>
)

@JsonClass(generateAdapter = true)
data class FullDatabaseBackup(
        val version: Int = 3,
        val date: Long = System.currentTimeMillis(),
        val profileBackups: List<ProfileBackup>,
        val searchHistory: List<String> = emptyList(),
        val globalFavoriteGroups: List<BackupFavoriteGroup> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProfileBackup(val profile: ProfileEntity, val favoriteLists: List<BackupFavoriteList>)

@JsonClass(generateAdapter = true)
data class BackupFavoriteGroup(val name: String, val type: String = "LIVE")
