package fr.kmz.projects.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import fr.kmz.projects.data.model.Lot
import fr.kmz.projects.data.model.LotWithSousLots
import kotlinx.coroutines.flow.Flow

@Dao
interface LotDao {
    @Insert
    suspend fun insert(lot: Lot): Long

    @Update
    suspend fun update(lot: Lot)

    @Delete
    suspend fun delete(lot: Lot)

    @Query("SELECT * FROM lots ORDER BY dateCreation DESC")
    fun getAllLots(): Flow<List<Lot>>

    // new: lots with nested sous-lots (with articles) for totals
    @Transaction
    @Query("SELECT * FROM lots ORDER BY dateCreation DESC")
    fun getAllLotsWithSousLots(): Flow<List<LotWithSousLots>>

    @Transaction
    @Query("SELECT * FROM lots WHERE id = :id")
    suspend fun getLotWithSousLots(id: Long): LotWithSousLots?

    @Query("SELECT * FROM lots WHERE id = :id")
    suspend fun getLotById(id: Long): Lot?

    @Query("UPDATE lots SET isValidated = :isValidated WHERE id = :id")
    suspend fun updateValidation(id: Long, isValidated: Boolean)
}
