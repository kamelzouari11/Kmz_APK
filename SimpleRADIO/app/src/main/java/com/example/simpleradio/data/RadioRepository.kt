package com.example.simpleradio.data

import com.example.simpleradio.data.api.RadioBrowserApi
import com.example.simpleradio.data.local.RadioDao
import com.example.simpleradio.data.local.entities.*
import com.example.simpleradio.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RadioRepository(
        private val api: RadioBrowserApi,
        private val dao: RadioDao,
        private val csvDataLoader: CsvDataLoader
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listAdapter =
            moshi.adapter<List<RadioStationEntity>>(
                    com.squareup.moshi.Types.newParameterizedType(
                            List::class.java,
                            RadioStationEntity::class.java
                    )
            )

    suspend fun saveCacheList(file: java.io.File, list: List<RadioStationEntity>) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val json = listAdapter.toJson(list)
            file.writeText(json)
        }
    }

    suspend fun loadCacheList(file: java.io.File): List<RadioStationEntity> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                val json = file.readText()
                listAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val allFavoriteLists: Flow<List<RadioFavoriteListEntity>> = dao.getAllRadioFavoriteLists()
    val recentRadios: Flow<List<RadioStationEntity>> = dao.getRecentRadios()

    // --- SAUVEGARDE / EXPORT ---
    suspend fun exportFavoritesToJson(): String {
        val lists = dao.getAllRadioFavoriteLists().first()
        val backupLists =
                lists.map { list ->
                    val stations = dao.getRadiosByFavoriteList(list.id).first()
                    BackupFavoriteList(name = list.name, stations = stations)
                }
        val backup = RadioBackup(favoriteLists = backupLists)
        val adapter = moshi.adapter(RadioBackup::class.java)
        return adapter.toJson(backup)
    }

    suspend fun importFavoritesFromJson(json: String) {
        val adapter = moshi.adapter(RadioBackup::class.java)
        val backup = adapter.fromJson(json) ?: return

        backup.favoriteLists.forEach { backupList ->
            // 1. Créer la liste (ou l'ignorer si déjà là avec le même nom - gestion simplifiée)
            dao.insertRadioFavoriteList(RadioFavoriteListEntity(name = backupList.name))

            // On récupère l'ID de la liste qu'on vient d'insérer (ou qui existait)
            // Note: Simplification ici, on cherche par nom
            val allLists = dao.getAllRadioFavoriteLists().first()
            val targetList = allLists.find { it.name == backupList.name }

            if (targetList != null) {
                // 2. Insérer les stations en base
                dao.insertRadioStations(backupList.stations)

                // 3. Créer les liens favoris
                backupList.stations.forEach { station ->
                    dao.addRadioToFavorite(
                            RadioFavoriteCrossRef(station.stationuuid, targetList.id)
                    )
                }
            }
        }
    }

    // Charger les pays depuis le CSV local (ordre préservé)
    suspend fun getCountries(): List<RadioCountry> =
            withContext(kotlinx.coroutines.Dispatchers.IO) { csvDataLoader.loadCountries() }

    // Charger les genres depuis le CSV local (ordre préservé)
    suspend fun getTags(): List<RadioTag> =
            withContext(kotlinx.coroutines.Dispatchers.IO) { csvDataLoader.loadGenres() }

    suspend fun getTagsFiltered(filter: String): List<RadioTag> =
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                csvDataLoader.loadGenres().filter { it.name.contains(filter, ignoreCase = true) }
            }

    suspend fun searchStations(
            countryCode: String? = null,
            tag: String? = null,
            query: String? = null,
            bitrateMin: Int? = null,
            bitrateMax: Int? = null,
            order: String = "clickcount",
            limit: Int = 200
    ): List<RadioStationEntity> {
        val stations =
                api.searchStations(
                        countryCode = countryCode,
                        tag = tag,
                        name = query,
                        bitrateMin = bitrateMin,
                        bitrateMax = bitrateMax,
                        order = order
                )
        val entities =
                stations.take(limit).map {
                    RadioStationEntity(
                            stationuuid = it.stationuuid,
                            name = it.name,
                            url =
                                    it.url_resolved.ifBlank {
                                        it.url
                                    }, // Meilleure compatibilité avec les flux modernes
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

    suspend fun getStationByUuid(uuid: String): RadioStationEntity? {
        return dao.getStationByUuid(uuid)
    }

    suspend fun addCustomRadio(name: String, url: String) {
        val listName = "mes urls"
        // 1. Ensure list exists (Insert IGNORE)
        dao.insertRadioFavoriteList(RadioFavoriteListEntity(name = listName))

        // 2. Get list ID
        val allLists = dao.getAllRadioFavoriteLists().first()
        val customList = allLists.find { it.name == listName } ?: return

        // 3. Create & Insert Station
        val uuid = java.util.UUID.randomUUID().toString()
        val station =
                RadioStationEntity(
                        stationuuid = uuid,
                        name = name,
                        url = url,
                        favicon = null,
                        country = "Custom",
                        tags = "custom",
                        bitrate = 0
                )
        dao.insertRadioStations(listOf(station))

        // 4. Link to Favorites
        dao.addRadioToFavorite(RadioFavoriteCrossRef(uuid, customList.id))
    }
}
