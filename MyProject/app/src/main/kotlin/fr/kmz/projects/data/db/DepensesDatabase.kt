package fr.kmz.projects.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense

@Database(
    entities = [Chapitre::class, Beneficiaire::class, Depense::class],
    version = 1,
    exportSchema = false
)
abstract class DepensesDatabase : RoomDatabase() {
    abstract fun chapitreDao(): ChapitreDao
    abstract fun beneficiaireDao(): BeneficiaireDao
    abstract fun depenseDao(): DepenseDao
}
