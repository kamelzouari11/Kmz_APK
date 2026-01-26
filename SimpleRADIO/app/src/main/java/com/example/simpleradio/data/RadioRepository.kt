package com.example.simpleradio.data

import com.example.simpleradio.data.api.RadioBrowserApi
import com.example.simpleradio.data.local.RadioDao
import com.example.simpleradio.data.local.entities.*
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import kotlinx.coroutines.flow.Flow

class RadioRepository(
    private val api: RadioBrowserApi,
    private val dao: RadioDao
) {
    val allFavoriteLists: Flow<List<RadioFavoriteListEntity>> = dao.getAllRadioFavoriteLists()
    val recentRadios: Flow<List<RadioStationEntity>> = dao.getRecentRadios()

    suspend fun getCountries(): List<RadioCountry> = api.getCountries()
    
    suspend fun getTags(): List<RadioTag> = api.getTags(order = "stationcount", reverse = true)
    suspend fun getTagsFiltered(filter: String): List<RadioTag> = api.getTagsFiltered(filter)

    suspend fun searchStations(
        countryCode: String? = null,
        tag: String? = null,
        query: String? = null,
        bitrateMin: Int? = null,
        bitrateMax: Int? = null,
        order: String = "clickcount"
    ): List<RadioStationEntity> {
        val stations = api.searchStations(
            countryCode = countryCode, 
            tag = tag, 
            name = query, 
            bitrateMin = bitrateMin,
            bitrateMax = bitrateMax,
            order = order
        )
        val entities = stations.map {
            RadioStationEntity(
                stationuuid = it.stationuuid,
                name = it.name,
                url = it.url_resolved.ifBlank { it.url }, // Meilleure compatibilité avec les flux modernes
                favicon = it.favicon,
                country = it.country,
                tags = it.tags,
                bitrate = it.bitrate
            )
        }
        dao.insertRadioStations(entities)
        return entities
    }

    fun getRadiosByFavoriteList(listId: Int): Flow<List<RadioStationEntity>> {
        return dao.getRadiosByFavoriteList(listId)
    }

    suspend fun addFavoriteList(name: String) {
        dao.insertRadioFavoriteList(RadioFavoriteListEntity(name = name))
    }

    suspend fun removeFavoriteList(list: RadioFavoriteListEntity) {
        dao.deleteRadioFavoriteList(list)
    }

    suspend fun toggleRadioFavorite(uuid: String, listId: Int) {
        val currentLists = dao.getListIdsForRadio(uuid)
        if (currentLists.contains(listId)) {
            dao.removeRadioFromFavorite(RadioFavoriteCrossRef(uuid, listId))
        } else {
            dao.addRadioToFavorite(RadioFavoriteCrossRef(uuid, listId))
        }
    }

    suspend fun getListIdsForRadio(uuid: String): List<Int> {
        return dao.getListIdsForRadio(uuid)
    }

    suspend fun addToRecents(uuid: String) {
        dao.insertRadioRecent(RadioRecentEntity(uuid, System.currentTimeMillis()))
        dao.trimRadioRecents()
    }
}
