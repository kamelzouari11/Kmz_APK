package fr.kmz.projects.data.repository

import fr.kmz.projects.data.db.BeneficiaireDao
import fr.kmz.projects.data.db.ChapitreDao
import fr.kmz.projects.data.db.DepenseDao
import fr.kmz.projects.data.db.DepensesDatabase
import fr.kmz.projects.data.db.ProjetDao
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.data.model.Projet
import fr.kmz.projects.utils.BeneficiaireWithProject
import fr.kmz.projects.utils.ChapitreWithProject
import fr.kmz.projects.utils.DepenseWithNames
import fr.kmz.projects.utils.GithubBackupData
import fr.kmz.projects.utils.ParsedData
import fr.kmz.projects.utils.ParsedDepense
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class DepensesRepository(
    private val chapitreDao: ChapitreDao,
    private val beneficiaireDao: BeneficiaireDao,
    private val depenseDao: DepenseDao,
    private val projetDao: ProjetDao,
    private val database: DepensesDatabase
) {
    // Projet operations
    fun getAllProjets(): Flow<List<Projet>> = projetDao.getAllProjets()

    suspend fun insertProjet(projet: Projet): Long = projetDao.insert(projet)

    suspend fun updateProjet(projet: Projet) = projetDao.update(projet)

    suspend fun deleteProjet(projet: Projet) = projetDao.delete(projet)

    suspend fun getProjetById(id: Long): Projet? = projetDao.getProjetById(id)

    suspend fun getAllProjetsSnapshot(): List<Projet> = projetDao.getAllProjetsSnapshot()

    // Chapitre operations (scoped by project)
    fun getChapitresByProjet(projetId: Long): Flow<List<Chapitre>> =
        chapitreDao.getChapitresByProjet(projetId)

    suspend fun insertChapitre(chapitre: Chapitre): Long = chapitreDao.insert(chapitre)

    suspend fun updateChapitre(chapitre: Chapitre) = chapitreDao.update(chapitre)

    suspend fun deleteChapitre(chapitre: Chapitre) = chapitreDao.delete(chapitre)

    suspend fun getChapitreById(id: Long): Chapitre? = chapitreDao.getChapitreById(id)

    // Beneficiaire operations (scoped by project)
    fun getBeneficiairesByProjet(projetId: Long): Flow<List<Beneficiaire>> =
        beneficiaireDao.getBeneficiairesByProjet(projetId)

    suspend fun insertBeneficiaire(beneficiaire: Beneficiaire): Long =
        beneficiaireDao.insert(beneficiaire)

    suspend fun updateBeneficiaire(beneficiaire: Beneficiaire) = beneficiaireDao.update(beneficiaire)

    suspend fun deleteBeneficiaire(beneficiaire: Beneficiaire) = beneficiaireDao.delete(beneficiaire)

    suspend fun getBeneficiaireById(id: Long): Beneficiaire? = beneficiaireDao.getBeneficiaireById(id)

    // Depense operations (scoped by project)
    fun getDepensesByProjet(projetId: Long): Flow<List<Depense>> =
        depenseDao.getDepensesByProjet(projetId)

    suspend fun insertDepense(depense: Depense): Long = depenseDao.insert(depense)

    suspend fun updateDepense(depense: Depense) = depenseDao.update(depense)

    suspend fun deleteDepense(depense: Depense) = depenseDao.delete(depense)

    suspend fun getDepenseById(id: Long): Depense? = depenseDao.getDepenseById(id)

    suspend fun exportAllDataForGithub(): GithubBackupData {
        val projets = projetDao.getAllProjetsSnapshot()
        val chapitres = chapitreDao.getAllChapitresSnapshot()
        val beneficiaires = beneficiaireDao.getAllBeneficiairesSnapshot()
        val depenses = depenseDao.getAllDepensesSnapshot()

        val projetNomById = projets.associate { it.id to it.nom }
        val chapitreNomById = chapitres.associate { it.id to it.nom }
        val beneficiaireNomById = beneficiaires.associate { it.id to it.nom }

        return GithubBackupData(
            projets = projets,
            chapitres = chapitres.map { chapitre ->
                ChapitreWithProject(
                    projetNom = projetNomById[chapitre.projetId] ?: "Projet par défaut",
                    nom = chapitre.nom
                )
            },
            beneficiaires = beneficiaires.map { beneficiaire ->
                BeneficiaireWithProject(
                    projetNom = projetNomById[beneficiaire.projetId] ?: "Projet par défaut",
                    nom = beneficiaire.nom
                )
            },
            depenses = depenses.map { depense ->
                DepenseWithNames(
                    projetNom = projetNomById[depense.projetId] ?: "Projet par défaut",
                    date = depense.date,
                    chapitreNom = chapitreNomById[depense.chapitreId] ?: "",
                    beneficiaireNom = beneficiaireNomById[depense.beneficiaireId] ?: "",
                    montant = depense.montant,
                    nature = depense.nature,
                    objet = depense.objet
                )
            }
        )
    }

    // Clear all data for a given project (used before sync or project deletion)
    suspend fun clearProjetData(projetId: Long) {
        depenseDao.clearAllForProjet(projetId)
        chapitreDao.clearAllForProjet(projetId)
        beneficiaireDao.clearAllForProjet(projetId)
    }

    // Bulk operations for GitHub sync (scoped to one project)
    suspend fun applyGithubData(
        projetId: Long,
        chapitres: List<Chapitre>,
        beneficiaires: List<Beneficiaire>,
        depenses: List<ParsedDepense>
    ) {
        depenseDao.clearAllForProjet(projetId)
        chapitreDao.clearAllForProjet(projetId)
        beneficiaireDao.clearAllForProjet(projetId)

        val chapitreIdByName = mutableMapOf<String, Long>()
        chapitres.forEach { ch ->
            val newId = chapitreDao.insert(ch.copy(id = 0, projetId = projetId))
            chapitreIdByName[ch.nom] = newId
        }

        val beneficiaireIdByName = mutableMapOf<String, Long>()
        beneficiaires.forEach { b ->
            val newId = beneficiaireDao.insert(b.copy(id = 0, projetId = projetId))
            beneficiaireIdByName[b.nom] = newId
        }

        depenses.forEach { pd ->
            val chId = chapitreIdByName[pd.chapitreNom] ?: return@forEach
            val benId = beneficiaireIdByName[pd.beneficiaireNom] ?: return@forEach
            depenseDao.insert(
                Depense(
                    date = pd.date,
                    chapitreId = chId,
                    beneficiaireId = benId,
                    montant = pd.montant,
                    nature = pd.nature,
                    objet = pd.objet,
                    projetId = projetId
                )
            )
        }
    }

    suspend fun replaceAllGithubData(parsedData: ParsedData): Projet? {
        var selectedProjet: Projet? = null

        database.withTransaction {
            depenseDao.clearAll()
            chapitreDao.clearAll()
            beneficiaireDao.clearAll()
            projetDao.clearAll()

            val projetIdByName = mutableMapOf<String, Long>()
            val projectNames = (
                parsedData.projets.map { it.nom } +
                    parsedData.chapitres.map { it.projetNom } +
                    parsedData.beneficiaires.map { it.projetNom } +
                    parsedData.depenses.map { it.projetNom }
                ).distinct().filter { it.isNotBlank() }

            projectNames.forEach { projetNom ->
                val newId = projetDao.insert(Projet(nom = projetNom))
                projetIdByName[projetNom] = newId
                if (selectedProjet == null) {
                    selectedProjet = Projet(id = newId, nom = projetNom)
                }
            }

            val chapitreIdByProjectAndName = mutableMapOf<Pair<Long, String>, Long>()
            val beneficiaireIdByProjectAndName = mutableMapOf<Pair<Long, String>, Long>()

            parsedData.chapitres.forEach { chapitre ->
                val projetId = projetIdByName[chapitre.projetNom] ?: return@forEach
                val key = projetId to chapitre.nom
                chapitreIdByProjectAndName.getOrPut(key) {
                    chapitreDao.insert(Chapitre(nom = chapitre.nom, projetId = projetId))
                }
            }

            parsedData.beneficiaires.forEach { beneficiaire ->
                val projetId = projetIdByName[beneficiaire.projetNom] ?: return@forEach
                val key = projetId to beneficiaire.nom
                beneficiaireIdByProjectAndName.getOrPut(key) {
                    beneficiaireDao.insert(Beneficiaire(nom = beneficiaire.nom, projetId = projetId))
                }
            }

            parsedData.depenses.forEach { pd ->
                val projetId = projetIdByName[pd.projetNom] ?: return@forEach
                val chapitreKey = projetId to pd.chapitreNom
                val beneficiaireKey = projetId to pd.beneficiaireNom

                val chapitreId = chapitreIdByProjectAndName.getOrPut(chapitreKey) {
                    chapitreDao.insert(Chapitre(nom = pd.chapitreNom, projetId = projetId))
                }
                val beneficiaireId = beneficiaireIdByProjectAndName.getOrPut(beneficiaireKey) {
                    beneficiaireDao.insert(Beneficiaire(nom = pd.beneficiaireNom, projetId = projetId))
                }

                depenseDao.insert(
                    Depense(
                        date = pd.date,
                        chapitreId = chapitreId,
                        beneficiaireId = beneficiaireId,
                        montant = pd.montant,
                        nature = pd.nature,
                        objet = pd.objet,
                        projetId = projetId
                    )
                )
            }
        }

        return selectedProjet
    }
}
