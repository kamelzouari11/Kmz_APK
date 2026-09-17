package fr.kmz.projects.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.data.model.Projet

@Database(
    entities = [Chapitre::class, Beneficiaire::class, Depense::class, Projet::class],
    version = 3,
    exportSchema = false
)
abstract class DepensesDatabase : RoomDatabase() {
    abstract fun chapitreDao(): ChapitreDao
    abstract fun beneficiaireDao(): BeneficiaireDao
    abstract fun depenseDao(): DepenseDao
    abstract fun projetDao(): ProjetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `projets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nom` TEXT NOT NULL)"
                )
                database.execSQL("INSERT INTO `projets` (`nom`) VALUES ('Projet par défaut')")
                database.execSQL("ALTER TABLE `chapitres` ADD COLUMN `projetId` INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE `beneficiaires` ADD COLUMN `projetId` INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE `depenses` ADD COLUMN `projetId` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `depenses` ADD COLUMN `objet` TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
