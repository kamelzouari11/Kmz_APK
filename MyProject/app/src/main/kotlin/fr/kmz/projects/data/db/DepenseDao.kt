package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.kmz.projects.data.model.Depense
import kotlinx.coroutines.flow.Flow

@Dao
interface DepenseDao {
    @Insert
    suspend fun insert(depense: Depense): Long

    @Update
    suspend fun update(depense: Depense)

    @Delete
    suspend fun delete(depense: Depense)

    @Query("SELECT * FROM depenses WHERE projetId = :projetId ORDER BY date DESC")
    fun getDepensesByProjet(projetId: Long): Flow<List<Depense>>

    @Query("SELECT * FROM depenses ORDER BY date DESC")
    suspend fun getAllDepensesSnapshot(): List<Depense>

    @Query("SELECT * FROM depenses WHERE id = :id")
    suspend fun getDepenseById(id: Long): Depense?

    @Query("DELETE FROM depenses WHERE projetId = :projetId")
    suspend fun clearAllForProjet(projetId: Long)

    @Query("DELETE FROM depenses")
    suspend fun clearAll()
}
