package com.example.myiptv.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class ProfileProtocol {
    XTREAM,
    STALKER,
}

@Entity(tableName = "profile")
@JsonClass(generateAdapter = true)
data class XtreamProfile(
    @ColumnInfo(defaultValue = "'Profil principal'") val name: String = "Profil principal",
    val serverUrl: String,
    val username: String,
    val password: String,
    @ColumnInfo(defaultValue = "'XTREAM'") val protocol: String = ProfileProtocol.XTREAM.name,
    val macAddress: String? = null,
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
    indices = [
        Index("profileId", "providerOrder"),
        Index("profileId", "countryCode", "providerOrder"),
        Index("profileId", "epgChannelId", "providerOrder"),
    ],
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
    tableName = "epg_channel_cache",
    primaryKeys = ["profileId", "epgChannelId"],
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
data class EpgChannelCache(
    val profileId: Int,
    val epgChannelId: String,
    val programsJson: String,
    val attemptedAt: Long,
    val sourceStreamId: Int?,
)

/** Lightweight projection used by EPG lookup; it intentionally excludes icon URLs. */
data class EpgChannelRow(
    val profileId: Int,
    val streamId: Int,
    val name: String,
    val countryCode: String,
    val epgChannelId: String?,
    val providerOrder: Int,
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
    indices = [Index("profileId", "lastWatchedAt")],
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
    indices = [Index("profileId", "searchedAt")],
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
    indices = [Index(value = ["profileId", "id"], unique = true)],
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
            parentColumns = ["profileId", "id"],
            childColumns = ["profileId", "groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SavedChannel::class,
            parentColumns = ["profileId", "streamId"],
            childColumns = ["profileId", "streamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId", "streamId")],
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
data class XtreamVodDto(
    @param:Json(name = "stream_id") val streamId: Int,
    val name: String? = null,
    @param:Json(name = "category_id") val categoryId: String? = null,
    @param:Json(name = "stream_icon") val streamIcon: String? = null,
    @param:Json(name = "container_extension") val containerExtension: String? = null,
    val plot: String? = null,
    val country: String? = null,
    val releasedate: String? = null,
    val duration: String? = null,
    @param:Json(name = "duration_secs") val durationSecs: Int? = null,
    val rating: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfoResponse(
    val info: XtreamVodInfoDto? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfoDto(
    @param:Json(name = "movie_image") val movieImage: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    @param:Json(name = "actors") val actors: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val releasedate: String? = null,
    val duration: String? = null,
    @param:Json(name = "duration_secs") val durationSecs: Int? = null,
)

data class VodMovie(
    val profileId: Int,
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val country: String,
    val posterUrl: String?,
    val extension: String,
    val description: String,
    val releaseDate: String?,
    val duration: String?,
    val durationSecs: Int?,
    val rating: String?,
    val actors: String?,
    val director: String?,
    val genre: String?,
    val categoryOrder: Int,
    val providerOrder: Int,
    val detailsLoaded: Boolean = false,
)

@Entity(
    tableName = "vod_movies",
    primaryKeys = ["profileId", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profileId", "providerOrder"),
        Index("profileId", "categoryId", "providerOrder"),
        Index("profileId", "categoryOrder"),
        Index("profileId", "name"),
    ],
)
data class VodMovieEntity(
    val profileId: Int,
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val country: String,
    val posterUrl: String?,
    val extension: String,
    val description: String,
    val releaseDate: String?,
    val duration: String?,
    val durationSecs: Int?,
    val rating: String?,
    val actors: String?,
    val director: String?,
    val genre: String?,
    val categoryOrder: Int,
    val providerOrder: Int,
    val detailsLoaded: Boolean = false,
)

data class VodCategoryRow(
    val categoryId: String,
    val categoryName: String,
    val categoryOrder: Int,
)

@Entity(
    tableName = "vod_categories",
    primaryKeys = ["profileId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = XtreamProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId", "categoryOrder")],
)
data class VodCategoryEntity(
    val profileId: Int,
    val categoryId: String,
    val categoryName: String,
    val categoryOrder: Int,
)

data class VodTitleRow(
    val streamId: Int,
    val name: String,
)

@Entity(
    tableName = "vod_actor_index",
    primaryKeys = ["profileId", "actorKey", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = VodMovieEntity::class,
            parentColumns = ["profileId", "streamId"],
            childColumns = ["profileId", "streamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId", "actorKey"), Index("profileId", "streamId")],
)
data class VodActorIndexEntity(
    val profileId: Int,
    val actorKey: String,
    val actorName: String,
    val streamId: Int,
)

@Entity(
    tableName = "vod_catalog_meta",
    foreignKeys = [
        ForeignKey(
            entity = XtreamProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VodCatalogMeta(
    @PrimaryKey val profileId: Int,
    val syncedAt: Long,
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
    val version: Int = 4,
    val date: Long = System.currentTimeMillis(),
    // Champs V1 conservés pour pouvoir lire les anciennes sauvegardes.
    val profile: XtreamProfile? = null,
    val favoriteGroups: List<BackupFavoriteGroup> = emptyList(),
    // Format multi-profils.
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
    CINEMA_SEARCH,
}
