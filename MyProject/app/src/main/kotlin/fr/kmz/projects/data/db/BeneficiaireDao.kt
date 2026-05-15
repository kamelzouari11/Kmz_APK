package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.kmz.projects.data.model.Beneficiaire
import kotlinx.coroutines.flow.Flow

@Dao
interface BeneficiaireDao {
    @Insert
    suspend fun insert(beneficiaire: Beneficiaire): Long

    @Update
    suspend fun update(beneficiaire: Beneficiaire)

    @Delete
    suspend fun delete(beneficiaire: Beneficiaire)

    @Query("SELECT * FROM beneficiaires ORDER BY nom ASC")
    fun getAllBeneficiaires(): Flow<List<Beneficiaire>>

    @Query("SELECT * FROM beneficiaires WHERE id = :id")
    suspend fun getBeneficiaireById(id: Long): Beneficiaire?

    @Query("DELETE FROM beneficiaires")
    suspend fun clearAll()
}
