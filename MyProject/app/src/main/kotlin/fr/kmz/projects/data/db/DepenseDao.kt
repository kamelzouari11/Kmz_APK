package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import fr.kmz.projects.data.model.Depense
import kotlinx.coroutines.flow.Flow

@Dao
interface DepenseDao {
    @Insert
    suspend fun insert(depense: Depense): Long

    @Delete
    suspend fun delete(depense: Depense)

    @Query("SELECT * FROM depenses ORDER BY date DESC")
    fun getAllDepenses(): Flow<List<Depense>>

    @Query("SELECT * FROM depenses WHERE id = :id")
    suspend fun getDepenseById(id: Long): Depense?

    @Query("DELETE FROM depenses")
    suspend fun clearAll()
}
