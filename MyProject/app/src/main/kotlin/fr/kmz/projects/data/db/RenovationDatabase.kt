package fr.kmz.projects.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.kmz.projects.data.model.Article
import fr.kmz.projects.data.model.Lot
import fr.kmz.projects.data.model.SousLot

@Database(
    entities = [Lot::class, SousLot::class, Article::class],
    version = 2,
    exportSchema = false
)
abstract class RenovationDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // lots: create new table without description
                database.execSQL("CREATE TABLE IF NOT EXISTS `lots_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nom` TEXT NOT NULL, `dateCreation` INTEGER NOT NULL, `isValidated` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `lots_new` (`id`,`nom`,`dateCreation`,`isValidated`) SELECT `id`,`nom`,`dateCreation`,`isValidated` FROM `lots`")
                database.execSQL("DROP TABLE `lots`")
                database.execSQL("ALTER TABLE `lots_new` RENAME TO `lots`")

                // sous_lots: same
                database.execSQL("CREATE TABLE IF NOT EXISTS `sous_lots_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lotId` INTEGER NOT NULL, `nom` TEXT NOT NULL, `dateCreation` INTEGER NOT NULL, `isValidated` INTEGER NOT NULL, FOREIGN KEY(`lotId`) REFERENCES `lots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("INSERT INTO `sous_lots_new` (`id`,`lotId`,`nom`,`dateCreation`,`isValidated`) SELECT `id`,`lotId`,`nom`,`dateCreation`,`isValidated` FROM `sous_lots`")
                database.execSQL("DROP TABLE `sous_lots`")
                database.execSQL("ALTER TABLE `sous_lots_new` RENAME TO `sous_lots`")
            }
        }
    }
    abstract fun lotDao(): LotDao
    abstract fun sousLotDao(): SousLotDao
    abstract fun articleDao(): ArticleDao
}
