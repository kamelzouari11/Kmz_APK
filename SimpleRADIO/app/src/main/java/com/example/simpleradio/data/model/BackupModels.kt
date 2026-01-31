package com.example.simpleradio.data.model

import com.example.simpleradio.data.local.entities.RadioStationEntity

data class RadioBackup(
        val version: Int = 1,
        val date: Long = System.currentTimeMillis(),
        val favoriteLists: List<BackupFavoriteList>
)

data class BackupFavoriteList(val name: String, val stations: List<RadioStationEntity>)
