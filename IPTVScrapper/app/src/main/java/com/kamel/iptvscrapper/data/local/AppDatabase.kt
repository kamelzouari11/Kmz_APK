package com.kamel.iptvscrapper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kamel.iptvscrapper.data.local.entities.LinkEntity

@Database(entities = [LinkEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao
}
