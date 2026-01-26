package com.example.simpleiptv.data

import com.example.simpleiptv.data.api.XtreamApi
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class IptvRepository(
    private val api: XtreamApi,
    private val dao: IptvDao
) {
    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val allFavoriteLists: Flow<List<FavoriteListEntity>> = dao.getAllFavoriteLists()
    val recentChannels: Flow<List<ChannelEntity>> = dao.getRecentChannels()

    fun getChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>> {
        return dao.getChannelsByCategory(categoryId)
    }

    fun getChannelsByFavoriteList(listId: Int): Flow<List<ChannelEntity>> {
        return dao.getChannelsByFavoriteList(listId)
    }

    fun searchChannels(query: String): Flow<List<ChannelEntity>> {
        return dao.searchChannels(query)
    }

    suspend fun refreshDatabase(user: String, pass: String) {
        val apiCategories = api.getLiveCategories(user, pass)
        val apiChannels = api.getLiveStreams(user, pass)

        val categoryEntities = apiCategories.map { CategoryEntity(it.category_id, it.category_name) }
        val channelEntitiesMap = mutableMapOf<Int, ChannelEntity>()
        val linkEntities = mutableListOf<ChannelCategoryCrossRef>()

        apiChannels.forEach {
            channelEntitiesMap[it.stream_id] = ChannelEntity(it.stream_id, it.name, it.stream_icon)
            linkEntities.add(ChannelCategoryCrossRef(it.stream_id, it.category_id))
        }

        // On ne vide plus brutalement categories et channels pour éviter que les favoris sautent
        // Le OnConflictStrategy.REPLACE dans le DAO s'occupera de mettre à jour les infos
        dao.clearChannelCategoryLinks()
        
        dao.insertCategories(categoryEntities)
        dao.insertChannels(channelEntitiesMap.values.toList())
        dao.insertChannelCategoryLinks(linkEntities)
    }

    suspend fun addFavoriteList(name: String) {
        dao.insertFavoriteList(FavoriteListEntity(name = name))
    }

    suspend fun removeFavoriteList(list: FavoriteListEntity) {
        dao.deleteFavoriteList(list)
    }

    suspend fun toggleChannelFavorite(channelId: Int, listId: Int) {
        val currentLists = dao.getListIdsForChannel(channelId)
        if (currentLists.contains(listId)) {
            dao.removeChannelFromFavorite(ChannelFavoriteCrossRef(channelId, listId))
        } else {
            dao.addChannelToFavorite(ChannelFavoriteCrossRef(channelId, listId))
        }
    }
    
    suspend fun getListIdsForChannel(channelId: Int): List<Int> {
        return dao.getListIdsForChannel(channelId)
    }

    suspend fun addToRecents(channelId: Int) {
        dao.insertRecent(RecentChannelEntity(channelId, System.currentTimeMillis()))
        dao.trimRecents()
    }
}
