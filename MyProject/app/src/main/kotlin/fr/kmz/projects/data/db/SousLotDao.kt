package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import fr.kmz.projects.data.model.SousLot
import fr.kmz.projects.data.model.SousLotWithArticles
import kotlinx.coroutines.flow.Flow

@Dao
interface SousLotDao {
    @Insert
    suspend fun insert(sousLot: SousLot): Long

    @Update
    suspend fun update(sousLot: SousLot)

    @Delete
    suspend fun delete(sousLot: SousLot)

    @Query("SELECT * FROM sous_lots WHERE lotId = :lotId ORDER BY dateCreation DESC")
    fun getSousLotsByLotId(lotId: Long): Flow<List<SousLot>>

    @Query("SELECT * FROM sous_lots WHERE id = :id")
    suspend fun getSousLotById(id: Long): SousLot?

    @Transaction
    @Query("SELECT * FROM sous_lots WHERE id = :id")
    suspend fun getSousLotWithArticles(id: Long): SousLotWithArticles?

    // new: return all sous-lots with their articles for a given lot
    @Transaction
    @Query("SELECT * FROM sous_lots WHERE lotId = :lotId ORDER BY dateCreation DESC")
    fun getSousLotsWithArticlesByLotId(lotId: Long): Flow<List<SousLotWithArticles>>

    @Query("UPDATE sous_lots SET isValidated = :isValidated WHERE id = :id")
    suspend fun updateValidation(id: Long, isValidated: Boolean)
}
