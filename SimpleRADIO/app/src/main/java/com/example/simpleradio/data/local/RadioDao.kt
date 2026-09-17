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
    suspend fun insertRadioFavoriteList(list: RadioFavoriteListEntity): Long

    @Query("SELECT * FROM radio_favorite_lists")
    fun getAllRadioFavoriteLists(): Flow<List<RadioFavoriteListEntity>>

    @Delete suspend fun deleteRadioFavoriteList(list: RadioFavoriteListEntity)

    @Query("DELETE FROM radio_favorites") suspend fun deleteAllRadioFavorites()

    @Query("DELETE FROM radio_favorite_lists") suspend fun deleteAllRadioFavoriteLists()

    /**
     * Replaces the local favorite hierarchy with the already-normalized cloud hierarchy. Keeping
     * this operation in the DAO makes the destructive part atomic: a failed import cannot leave a
     * half-imported set of lists behind.
     */
    @Transaction
    suspend fun replaceRadioFavorites(lists: List<Pair<String, List<RadioStationEntity>>>) {
        deleteAllRadioFavorites()
        deleteAllRadioFavoriteLists()

        lists.forEach { (name, stations) ->
            val listId = insertRadioFavoriteList(RadioFavoriteListEntity(name = name)).toInt()
            insertRadioStations(stations)
            stations.forEachIndexed { position, station ->
                addRadioToFavorite(
                        RadioFavoriteCrossRef(
                                stationuuid = station.stationuuid,
                                listId = listId,
                                position = position
                        )
                )
            }
        }
    }

    // --- WEB RADIOS Favorite CrossRef ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRadioToFavorite(crossRef: RadioFavoriteCrossRef)

    @Delete suspend fun removeRadioFromFavorite(crossRef: RadioFavoriteCrossRef)

    @Query(
            """
        SELECT radio_stations.* FROM radio_stations 
        INNER JOIN radio_favorites ON radio_stations.stationuuid = radio_favorites.stationuuid 
        WHERE radio_favorites.listId = :listId
        ORDER BY radio_favorites.position ASC
    """
    )
    fun getRadiosByFavoriteList(listId: Int): Flow<List<RadioStationEntity>>

    @Query("SELECT MAX(position) FROM radio_favorites WHERE listId = :listId")
    suspend fun getMaxPositionForList(listId: Int): Int?

    @Query(
            "UPDATE radio_favorites SET position = :newPosition WHERE stationuuid = :uuid AND listId = :listId"
    )
    suspend fun updateRadioPosition(uuid: String, listId: Int, newPosition: Int)

    @Query("SELECT listId FROM radio_favorites WHERE stationuuid = :uuid")
    suspend fun getListIdsForRadio(uuid: String): List<Int>

    @Query("SELECT DISTINCT stationuuid FROM radio_favorites")
    fun getAllFavoriteUuids(): Flow<List<String>>

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

    // --- Persistent station-logo cache ---
    @Query("SELECT * FROM station_logo_cache WHERE stationuuid = :uuid")
    suspend fun getStationLogoCache(uuid: String): StationLogoCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStationLogoCache(cache: StationLogoCacheEntity)

    @Query("DELETE FROM station_logo_cache WHERE stationuuid = :uuid")
    suspend fun deleteStationLogoCache(uuid: String)
}
