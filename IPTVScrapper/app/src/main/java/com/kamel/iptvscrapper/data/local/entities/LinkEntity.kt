package com.kamel.iptvscrapper.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links")
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // XTREAM, STALKER, M3U
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val mac: String? = null,
    val status: String = "PENDING", // PENDING, WORKING, DEAD
    val lastTested: Long = 0,
    val latency: Long = -1,
    val error: String? = null,
    val expiryDate: String? = null,
    val rawText: String? = null,
    val sourceUrl: String? = null
)
