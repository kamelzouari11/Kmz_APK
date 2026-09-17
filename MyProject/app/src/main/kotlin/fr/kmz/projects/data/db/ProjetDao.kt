package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.kmz.projects.data.model.Projet
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjetDao {
    @Insert
    suspend fun insert(projet: Projet): Long

    @Update
    suspend fun update(projet: Projet)

    @Delete
    suspend fun delete(projet: Projet)

    @Query("SELECT * FROM projets ORDER BY nom ASC")
    fun getAllProjets(): Flow<List<Projet>>

    @Query("SELECT * FROM projets ORDER BY nom ASC")
    suspend fun getAllProjetsSnapshot(): List<Projet>

    @Query("SELECT * FROM projets WHERE id = :id")
    suspend fun getProjetById(id: Long): Projet?

    @Query("DELETE FROM projets")
    suspend fun clearAll()
}
