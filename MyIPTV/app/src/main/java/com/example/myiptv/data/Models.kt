package com.example.myiptv.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Entity(tableName = "profile")
@JsonClass(generateAdapter = true)
data class XtreamProfile(
    @ColumnInfo(defaultValue = "'Profil principal'") val name: String = "Profil principal",
    val serverUrl: String,
    val username: String,
    val password: String,
    @ColumnInfo(defaultValue = "1") val countryGroupingEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = false,
    val lastSyncedAt: Long? = null,
    @PrimaryKey val id: Int = 0,
) {
    companion object {
        const val LEGACY_PROFILE_ID = 1
    }
}

@Entity(
    tableName = "channels",
    primaryKeys = ["profileId", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
@JsonClass(generateAdapter = true)
data class SavedChannel(
    val profileId: Int = XtreamProfile.LEGACY_PROFILE_ID,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val countryCode: String,
    val iconUrl: String?,
    val extension: String,
    val epgChannelId: String?,
    @ColumnInfo(defaultValue = "-1") val categoryOrder: Int,
    @ColumnInfo(defaultValue = "-1") val providerOrder: Int,
    val streamId: Int,
)

@Entity(
    tableName = "recent_channels",
    primaryKeys = ["profileId", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedChannel::class,
            parentColumns = ["profileId", "streamId"],
            childColumns = ["profileId", "streamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("profileId", "lastWatchedAt")],
)
data class RecentChannel(
    val profileId: Int,
    val streamId: Int,
    val lastWatchedAt: Long,
)

@Entity(
    tableName = "search_history",
    primaryKeys = ["profileId", "query"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("profileId", "searchedAt")],
)
data class SearchHistoryEntry(
    val profileId: Int,
    val query: String,
    val searchedAt: Long,
)

@Entity(
    tableName = "favorite_groups",
    foreignKeys = [
        ForeignKey(
            entity = XtreamProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class FavoriteGroup(
    val name: String,
    val profileId: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "favorite_memberships",
    primaryKeys = ["profileId", "groupId", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SavedChannel::class,
            parentColumns = ["profileId", "streamId"],
            childColumns = ["profileId", "streamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("profileId", "streamId")],
)
data class FavoriteMembership(
    val profileId: Int,
    val groupId: Long,
    val streamId: Int,
)

@JsonClass(generateAdapter = true)
data class XtreamCategoryDto(
    @param:Json(name = "category_id") val categoryId: String?,
    @param:Json(name = "category_name") val categoryName: String?,
)

@JsonClass(generateAdapter = true)
data class XtreamChannelDto(
    @param:Json(name = "stream_id") val streamId: Int,
    val name: String?,
    @param:Json(name = "category_id") val categoryId: String?,
    @param:Json(name = "stream_icon") val streamIcon: String?,
    @param:Json(name = "epg_channel_id") val epgChannelId: String?,
    @param:Json(name = "container_extension") val containerExtension: String?,
)

@JsonClass(generateAdapter = true)
data class XtreamEpgResponse(
    @param:Json(name = "epg_listings") val listings: List<XtreamEpgDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class XtreamEpgDto(
    val title: String? = null,
    val description: String? = null,
    val start: String? = null,
    val end: String? = null,
    @param:Json(name = "start_timestamp") val startTimestamp: Any? = null,
    @param:Json(name = "stop_timestamp") val stopTimestamp: Any? = null,
)

data class EpgProgram(
    val title: String,
    val description: String,
    val timeRange: String,
    val startEpochSeconds: Long? = null,
    val stopEpochSeconds: Long? = null,
)

@JsonClass(generateAdapter = true)
data class MyIptvBackup(
    val version: Int = 2,
    val date: Long = System.currentTimeMillis(),
    // Champs V1 conservés pour pouvoir lire les anciennes sauvegardes.
    val profile: XtreamProfile? = null,
    val favoriteGroups: List<BackupFavoriteGroup> = emptyList(),
    // Format V2 multi-profils.
    val profiles: List<BackupProfile> = emptyList(),
    val activeProfileId: Int? = null,
)

@JsonClass(generateAdapter = true)
data class BackupProfile(
    val profile: XtreamProfile,
    val favoriteGroups: List<BackupFavoriteGroup> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupFavoriteGroup(
    val name: String,
    val channels: List<SavedChannel> = emptyList(),
)

enum class BrowseMode {
    LIVE,
    RECENT,
    FAVORITES,
    SEARCH,
    EPG_SEARCH,
}
