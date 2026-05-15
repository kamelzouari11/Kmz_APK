package fr.kmz.projects.data.repository

import fr.kmz.projects.data.db.BeneficiaireDao
import fr.kmz.projects.data.db.ChapitreDao
import fr.kmz.projects.data.db.DepenseDao
import fr.kmz.projects.data.db.DepensesDatabase
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense
import kotlinx.coroutines.flow.Flow

class DepensesRepository(
    private val chapitreDao: ChapitreDao,
    private val beneficiaireDao: BeneficiaireDao,
    private val depenseDao: DepenseDao,
    private val database: DepensesDatabase
) {
    // Chapitre operations
    fun getAllChapitres(): Flow<List<Chapitre>> = chapitreDao.getAllChapitres()

    suspend fun insertChapitre(chapitre: Chapitre): Long = chapitreDao.insert(chapitre)

    suspend fun updateChapitre(chapitre: Chapitre) = chapitreDao.update(chapitre)

    suspend fun deleteChapitre(chapitre: Chapitre) = chapitreDao.delete(chapitre)

    suspend fun getChapitreById(id: Long): Chapitre? = chapitreDao.getChapitreById(id)

    // Beneficiaire operations
    fun getAllBeneficiaires(): Flow<List<Beneficiaire>> = beneficiaireDao.getAllBeneficiaires()

    suspend fun insertBeneficiaire(beneficiaire: Beneficiaire): Long =
        beneficiaireDao.insert(beneficiaire)

    suspend fun updateBeneficiaire(beneficiaire: Beneficiaire) = beneficiaireDao.update(beneficiaire)

    suspend fun deleteBeneficiaire(beneficiaire: Beneficiaire) = beneficiaireDao.delete(beneficiaire)

    suspend fun getBeneficiaireById(id: Long): Beneficiaire? = beneficiaireDao.getBeneficiaireById(id)

    // Depense operations
    fun getAllDepenses(): Flow<List<Depense>> = depenseDao.getAllDepenses()

    suspend fun insertDepense(depense: Depense): Long = depenseDao.insert(depense)

    suspend fun deleteDepense(depense: Depense) = depenseDao.delete(depense)

    suspend fun getDepenseById(id: Long): Depense? = depenseDao.getDepenseById(id)

    // Bulk operations for GitHub sync
    suspend fun applyGithubData(
        chapitres: List<Chapitre>,
        beneficiaires: List<Beneficiaire>,
        depenses: List<Depense>
    ) {
        chapitreDao.clearAll()
        beneficiaireDao.clearAll()
        depenseDao.clearAll()

        chapitres.forEach { chapitreDao.insert(it.copy(id = 0)) }
        beneficiaires.forEach { beneficiaireDao.insert(it.copy(id = 0)) }
        depenses.forEach { depenseDao.insert(it.copy(id = 0)) }
    }
}
