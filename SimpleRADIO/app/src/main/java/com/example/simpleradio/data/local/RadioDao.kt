package com.example.simpleradio.data.local

import androidx.room.*
import com.example.simpleradio.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioDao {

    // --- WEB RADIOS stations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRadioStations(stations: List<RadioStationEntity>)

    @Query("SELECT * FROM radio_stations") fun getAllRadioStations(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM radio_stations WHERE name LIKE '%' || :query || '%'")
    fun searchRadioStations(query: String): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM radio_stations WHERE stationuuid = :uuid")
    suspend fun getStationByUuid(uuid: String): RadioStationEntity?

    // --- WEB RADIOS Favorite Lists ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRadioFavoriteList(list: RadioFavoriteListEntity)

    @Query("SELECT * FROM radio_favorite_lists")
    fun getAllRadioFavoriteLists(): Flow<List<RadioFavoriteListEntity>>

    @Delete suspend fun deleteRadioFavoriteList(list: RadioFavoriteListEntity)

    // --- WEB RADIOS Favorite CrossRef ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRadioToFavorite(crossRef: RadioFavoriteCrossRef)

    @Delete suspend fun removeRadioFromFavorite(crossRef: RadioFavoriteCrossRef)

    @Query(
            """
        SELECT radio_stations.* FROM radio_stations 
        INNER JOIN radio_favorites ON radio_stations.stationuuid = radio_favorites.stationuuid 
        WHERE radio_favorites.listId = :listId
    """
    )
    fun getRadiosByFavoriteList(listId: Int): Flow<List<RadioStationEntity>>

    @Query("SELECT listId FROM radio_favorites WHERE stationuuid = :uuid")
    suspend fun getListIdsForRadio(uuid: String): List<Int>

    // --- WEB RADIOS Recents ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRadioRecent(recent: RadioRecentEntity)

    @Query(
            """
        SELECT radio_stations.* FROM radio_stations 
        INNER JOIN radio_recent ON radio_stations.stationuuid = radio_recent.stationuuid 
        ORDER BY radio_recent.timestamp DESC 
        LIMIT 100
    """
    )
    fun getRecentRadios(): Flow<List<RadioStationEntity>>

    @Query(
            """
        DELETE FROM radio_recent WHERE stationuuid NOT IN (
            SELECT stationuuid FROM radio_recent ORDER BY timestamp DESC LIMIT 100
        )
    """
    )
    suspend fun trimRadioRecents()
}
