package fr.kmz.projects.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.data.repository.DepensesRepository
import fr.kmz.projects.utils.CsvManager
import fr.kmz.projects.utils.DepenseWithNames
import fr.kmz.projects.utils.GitHubSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

class DepensesViewModel(private val repository: DepensesRepository) : ViewModel() {

    val chapitres: StateFlow<List<Chapitre>> =
        repository.getAllChapitres()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    val beneficiaires: StateFlow<List<Beneficiaire>> =
        repository.getAllBeneficiaires()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    val depenses: StateFlow<List<Depense>> =
        repository.getAllDepenses()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    val depensesParChapitre: StateFlow<Map<Chapitre, List<Depense>>> =
        combine(chapitres, depenses) { chaps, deps ->
            chaps.associateWith { chapitre ->
                deps.filter { it.chapitreId == chapitre.id }
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyMap())

    val depensesParBeneficiaire: StateFlow<Map<Beneficiaire, List<Depense>>> =
        combine(beneficiaires, depenses) { bens, deps ->
            bens.associateWith { beneficiaire ->
                deps.filter { it.beneficiaireId == beneficiaire.id }
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyMap())

    val total: StateFlow<Long> =
        depenses
            .combine(chapitres) { _, _ ->
                depenses.value.sumOf { it.montant }
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, 0L)

    fun createChapitre(nom: String) {
        viewModelScope.launch {
            repository.insertChapitre(Chapitre(nom = nom))
        }
    }

    fun createBeneficiaire(nom: String) {
        viewModelScope.launch {
            repository.insertBeneficiaire(Beneficiaire(nom = nom))
        }
    }

    fun createDepense(date: Long, chapitreId: Long, beneficiaireId: Long, montant: Long, nature: String) {
        viewModelScope.launch {
            repository.insertDepense(
                Depense(
                    date = date,
                    chapitreId = chapitreId,
                    beneficiaireId = beneficiaireId,
                    montant = montant,
                    nature = nature
                )
            )
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

                val chaps = chapitres.value
                val bens = beneficiaires.value
                val deps = depenses.value

                val depensesWithNames = deps.map { depense ->
                    val chapitre = chaps.find { it.id == depense.chapitreId }
                    val beneficiaire = bens.find { it.id == depense.beneficiaireId }
                    DepenseWithNames(
                        date = depense.date,
                        chapitreNom = chapitre?.nom ?: "",
                        beneficiaireNom = beneficiaire?.nom ?: "",
                        montant = depense.montant,
                        nature = depense.nature
                    )
                }

                val csvContent = CsvManager.exportToCsv(depensesWithNames)
                val success = GitHubSyncService.uploadCsvContent(csvContent)

                if (success) {
                    _syncState.value = SyncState.Success("Dépenses sauvegardées sur GitHub")
                } else {
                    _syncState.value = SyncState.Error("Erreur lors de la sauvegarde")
                }
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
                    _syncState.value = SyncState.Success("Aucune donnée sur GitHub")
                    return@launch
                }

                val parsedData = CsvManager.parseCsv(csvContent)
                repository.applyGithubData(
                    parsedData.chapitres,
                    parsedData.beneficiaires,
                    parsedData.depenses
                )

                _syncState.value = SyncState.Success("Dépenses restaurées depuis GitHub")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
