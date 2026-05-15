package com.kamel.iptvscrapper.data.local

import androidx.room.*
import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY id DESC")
    fun getAllLinks(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE status = :status")
    fun getLinksByStatus(status: String): Flow<List<LinkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<LinkEntity>)

    @Update
    suspend fun updateLink(link: LinkEntity)

    @Query("DELETE FROM links")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM links WHERE url = :url AND (username = :user OR mac = :mac)")
    suspend fun findDuplicate(url: String, user: String?, mac: String?): LinkEntity?
}
