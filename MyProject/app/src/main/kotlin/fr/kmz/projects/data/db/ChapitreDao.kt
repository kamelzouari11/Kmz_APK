package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.kmz.projects.data.model.Chapitre
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapitreDao {
    @Insert
    suspend fun insert(chapitre: Chapitre): Long

    @Update
    suspend fun update(chapitre: Chapitre)

    @Delete
    suspend fun delete(chapitre: Chapitre)

    @Query("SELECT * FROM chapitres ORDER BY nom ASC")
    fun getAllChapitres(): Flow<List<Chapitre>>

    @Query("SELECT * FROM chapitres WHERE id = :id")
    suspend fun getChapitreById(id: Long): Chapitre?

    @Query("DELETE FROM chapitres")
    suspend fun clearAll()
}
