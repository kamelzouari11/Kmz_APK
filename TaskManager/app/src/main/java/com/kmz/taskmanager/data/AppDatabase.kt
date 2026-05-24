package com.kmz.taskmanager.data

import android.content.Context
import androidx.room.*

@Database(entities = [Task::class, Folder::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "task_manager_db"
                                        )
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
