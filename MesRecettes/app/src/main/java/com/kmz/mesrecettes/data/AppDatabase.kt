package com.kmz.mesrecettes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
        entities =
                [
                        Base::class,
                        IngredientGroup::class,
                        Ingredient::class,
                        Recipe::class,
                        RecipeIngredient::class],
        version = 1,
        exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "recettes_database"
                                        )
                                        .addCallback(DatabaseCallback())
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // No automated pre-population, bases and groups are created by the user
        }
    }
}
