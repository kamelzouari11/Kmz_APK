package com.mescomptes.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

class DatabaseConverters {
    @TypeConverter
    fun fromOrderType(value: OrderType): String = value.name

    @TypeConverter
    fun toOrderType(value: String): OrderType = OrderType.valueOf(value)
}

@Dao
interface MesComptesDao {
    @Query("SELECT * FROM accounts ORDER BY bank COLLATE NOCASE, holder COLLATE NOCASE")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM movements ORDER BY operationDate DESC, id DESC")
    fun observeMovements(): Flow<List<MovementEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAccounts(): List<AccountEntity>

    @Query("SELECT * FROM movements ORDER BY operationDate DESC, id DESC")
    suspend fun getMovements(): List<MovementEntity>

    @Query("SELECT * FROM movements WHERE id = :id LIMIT 1")
    suspend fun getMovement(id: Long): MovementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: MovementEntity): Long

    @Update
    suspend fun updateMovement(movement: MovementEntity)

    @Delete
    suspend fun deleteMovement(movement: MovementEntity)

    @Query(
        """SELECT COALESCE(SUM(CASE
            WHEN orderType IN ('ACHAT', 'SOUSCRIPTION') THEN quantity
            ELSE -quantity END), 0)
            FROM movements
            WHERE accountId = :accountId
              AND titleKey = :titleKey
              AND id != :excludedMovementId""",
    )
    suspend fun positionBeforeMovement(
        accountId: Long,
        titleKey: String,
        excludedMovementId: Long,
    ): Int

    @Query("DELETE FROM movements")
    suspend fun deleteAllMovements()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Insert
    suspend fun restoreAccounts(accounts: List<AccountEntity>)

    @Insert
    suspend fun restoreMovements(movements: List<MovementEntity>)
}

@Database(
    entities = [AccountEntity::class, MovementEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class MesComptesDatabase : RoomDatabase() {
    abstract fun dao(): MesComptesDao

    companion object {
        @Volatile
        private var instance: MesComptesDatabase? = null

        fun get(context: Context): MesComptesDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MesComptesDatabase::class.java,
                "mes-comptes.db",
            ).build().also { instance = it }
        }
    }
}
