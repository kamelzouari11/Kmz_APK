package com.kmzapk.mylinks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "saved_links", indices = [Index(value = ["original_url"], unique = true)])
data class SavedLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "original_url") val originalUrl: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "google_maps_url") val googleMapsUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "address") val address: String? = null,
    @ColumnInfo(name = "tags") val tags: String = "",
    @ColumnInfo(name = "profile_url") val profileUrl: String? = null,
    @ColumnInfo(name = "profile_image_url") val profileImageUrl: String? = null,
    @ColumnInfo(name = "import_collection") val importCollection: String? = null,
    @ColumnInfo(name = "import_context") val importContext: String? = null,
    @ColumnInfo(name = "ai_checked_at") val aiCheckedAt: Long? = null,
    @ColumnInfo(name = "ai_check_status") val aiCheckStatus: String? = null,
    @ColumnInfo(name = "published_at") val publishedAt: Long? = null
)
