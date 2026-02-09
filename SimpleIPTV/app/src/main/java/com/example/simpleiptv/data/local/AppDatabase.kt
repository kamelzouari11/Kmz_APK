package com.example.simpleiptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.simpleiptv.data.local.entities.*

@Database(
        entities =
                [
                        CategoryEntity::class,
                        ChannelEntity::class,
                        ChannelCategoryCrossRef::class,
                        FavoriteListEntity::class,
                        ChannelFavoriteCrossRef::class,
                        RecentChannelEntity::class,
                        ProfileEntity::class],
        version = 8,
        exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "iptv_database"
                                        )
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
