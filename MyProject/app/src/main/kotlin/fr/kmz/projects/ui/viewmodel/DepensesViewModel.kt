package fr.kmz.projects.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.data.model.Projet
import fr.kmz.projects.data.repository.DepensesRepository
import fr.kmz.projects.utils.CsvManager
import fr.kmz.projects.utils.GitHubSyncService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DepensesViewModel(private val repository: DepensesRepository) : ViewModel() {

    private val _projetCourant = MutableStateFlow<Projet?>(null)
    val projetCourant: StateFlow<Projet?> = _projetCourant

    val projets: StateFlow<List<Projet>> =
        repository.getAllProjets()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chapitres: StateFlow<List<Chapitre>> =
        _projetCourant
            .flatMapLatest { projet ->
                if (projet != null) repository.getChapitresByProjet(projet.id)
                else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val beneficiaires: StateFlow<List<Beneficiaire>> =
        _projetCourant
            .flatMapLatest { projet ->
                if (projet != null) repository.getBeneficiairesByProjet(projet.id)
                else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val depenses: StateFlow<List<Depense>> =
        _projetCourant
            .flatMapLatest { projet ->
                if (projet != null) repository.getDepensesByProjet(projet.id)
                else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    val depensesParChapitre: StateFlow<Map<Chapitre, List<Depense>>> =
        combine(chapitres, depenses) { chaps, deps ->
            chaps.associateWith { chapitre ->
                deps.filter { it.chapitreId == chapitre.id }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val depensesParBeneficiaire: StateFlow<Map<Beneficiaire, List<Depense>>> =
        combine(beneficiaires, depenses) { bens, deps ->
            bens.associateWith { beneficiaire ->
                deps.filter { it.beneficiaireId == beneficiaire.id }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val total: StateFlow<Long> =
        depenses
            .combine(chapitres) { deps, _ -> deps.sumOf { it.montant } }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    init {
        viewModelScope.launch {
            projets.collect { list ->
                if (_projetCourant.value == null && list.isNotEmpty()) {
                    _projetCourant.value = list.first()
                }
            }
        }
    }

    fun selectProjet(projet: Projet) {
        _projetCourant.value = projet
    }

    fun createProjet(nom: String) {
        viewModelScope.launch {
            val newId = repository.insertProjet(Projet(nom = nom))
            _projetCourant.value = Projet(id = newId, nom = nom)
        }
    }

    fun deleteProjet(projet: Projet) {
        viewModelScope.launch {
            repository.clearProjetData(projet.id)
            repository.deleteProjet(projet)
            if (_projetCourant.value?.id == projet.id) {
                _projetCourant.value = projets.value.firstOrNull { it.id != projet.id }
            }
        }
    }

    fun createChapitre(nom: String) {
        viewModelScope.launch {
            val projetId = _projetCourant.value?.id ?: return@launch
            repository.insertChapitre(Chapitre(nom = nom, projetId = projetId))
        }
    }

    fun createBeneficiaire(nom: String) {
        viewModelScope.launch {
            val projetId = _projetCourant.value?.id ?: return@launch
            repository.insertBeneficiaire(Beneficiaire(nom = nom, projetId = projetId))
        }
    }

    fun createDepense(
        date: Long,
        chapitreId: Long,
        beneficiaireId: Long,
        montant: Long,
        nature: String,
        objet: String
    ) {
        viewModelScope.launch {
            val projetId = _projetCourant.value?.id ?: return@launch
            repository.insertDepense(
                Depense(
                    date = date,
                    chapitreId = chapitreId,
                    beneficiaireId = beneficiaireId,
                    montant = montant,
                    nature = nature,
                    objet = objet,
                    projetId = projetId
                )
            )
        }
    }

    fun updateDepense(depense: Depense) {
        viewModelScope.launch {
            repository.updateDepense(depense)
        }
    }

    fun deleteDepense(depense: Depense) {
        viewModelScope.launch {
            repository.deleteDepense(depense)
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun uploadToGitHub() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Loading

                val csvContent = CsvManager.exportToCsv(repository.exportAllDataForGithub())
                val success = GitHubSyncService.uploadCsvContent(csvContent)

                _syncState.value = if (success)
                    SyncState.Success("Tous les projets ont été sauvegardés sur GitHub")
                else
                    SyncState.Error("Erreur lors de la sauvegarde")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun downloadFromGitHub() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Loading

                val csvContent = GitHubSyncService.downloadCsvContent()
                if (csvContent.isBlank()) {
                    _syncState.value = SyncState.Success("Aucune donnée globale sur GitHub")
                    return@launch
                }

                val parsedData = CsvManager.parseCsv(csvContent)
                _projetCourant.value = repository.replaceAllGithubData(parsedData)

                _syncState.value = SyncState.Success("Tous les projets ont été restaurés depuis GitHub")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
