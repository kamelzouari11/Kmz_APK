package com.kmzapk.mylinks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SavedLinkDao {
    @Insert
    suspend fun insert(link: SavedLink): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(link: SavedLink): Long

    @Update
    suspend fun update(link: SavedLink)

    @Delete
    suspend fun delete(link: SavedLink)

    @Query("DELETE FROM saved_links")
    suspend fun deleteAll()

    @Query("SELECT * FROM saved_links ORDER BY MAX(created_at, COALESCE(published_at, 0)) DESC, id DESC")
    suspend fun getAll(): List<SavedLink>

    @Query("SELECT * FROM saved_links ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun getLatest(): SavedLink?

    @Query("SELECT * FROM saved_links WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavedLink?

    @Query("SELECT * FROM saved_links WHERE original_url = :url LIMIT 1")
    suspend fun getByUrl(url: String): SavedLink?
}
