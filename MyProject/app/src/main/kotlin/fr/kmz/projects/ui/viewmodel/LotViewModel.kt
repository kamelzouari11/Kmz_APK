package fr.kmz.projects.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kmz.projects.data.model.Article
import fr.kmz.projects.data.model.Lot
import fr.kmz.projects.data.model.LotWithSousLots
import fr.kmz.projects.data.model.SousLot
import fr.kmz.projects.data.model.SousLotWithArticles
import fr.kmz.projects.data.repository.RenovationRepository
import fr.kmz.projects.utils.CsvManager
import fr.kmz.projects.utils.CsvRow
import fr.kmz.projects.utils.GitHubSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

class LotViewModel(private val repository: RenovationRepository) : ViewModel() {
    val lots = repository.getAllLots().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // lots including their sous-lots+articles for totals
    val lotsWithSousLots =
            repository
                    .getAllLotsWithSousLots()
                    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // map lotId -> total cents
    val lotTotals =
            lotsWithSousLots
                    .map { list -> list.associate { it.lot.id to it.calculerTotal() } }
                    .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // overall project total
    val projectTotal =
            lotTotals
                    .map { map -> map.values.sum() }
                    .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun uploadToGitHub() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            try {
                val allData = lotsWithSousLots.value // Current full state from StateFlow
                val csvContent = CsvManager.exportToCsv(allData)
                val success = GitHubSyncService.uploadCsvContent(csvContent)
                if (success) {
                    _syncState.value = SyncState.Success("Sauvegarde réussie sur GitHub !")
                } else {
                    _syncState.value = SyncState.Error("Échec de la sauvegarde.")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun downloadFromGitHub() {
        viewModelScope.launch {
            _syncState.value = SyncState.Loading
            try {
                val csvContent = GitHubSyncService.downloadCsvContent()
                if (csvContent.isBlank()) {
                    _syncState.value =
                            SyncState.Success("Aucune donnée (fichier vide ou inexistant).")
                    return@launch
                }
                val rows = CsvManager.parseCsv(csvContent)

                // Transformer List<CsvRow> en List<LotWithSousLots> attendue par la base de données
                val parsedLots = transformCsvToHierarchy(rows)

                // Appliquer ces nouvelles lignes dans la base de données Room (Étape finale)
                repository.applyGithubDataToDatabase(parsedLots)

                _syncState.value =
                        SyncState.Success("Base de données mise à jour avec succès depuis GitHub !")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Erreur de téléchargement")
            }
        }
    }

    fun createLot(nom: String) {
        viewModelScope.launch {
            val lot = Lot(nom = nom)
            repository.insertLot(lot)
        }
    }

    fun updateLot(lot: Lot) {
        viewModelScope.launch { repository.updateLot(lot) }
    }

    fun deleteLot(lot: Lot) {
        viewModelScope.launch { repository.deleteLot(lot) }
    }

    fun validateLot(lotId: Long, isValidated: Boolean) {
        viewModelScope.launch { repository.validateLot(lotId, isValidated) }
    }

    private fun transformCsvToHierarchy(csvRows: List<CsvRow>): List<LotWithSousLots> {
        val groupedByLot = csvRows.groupBy { it.lotName }
        val result = mutableListOf<LotWithSousLots>()

        for ((lotName, lotRows) in groupedByLot) {
            val emptyLot = Lot(nom = lotName)
            val subLotsList = mutableListOf<SousLotWithArticles>()

            val groupedBySousLot = lotRows.groupBy { it.sousLotName }.filterKeys { it.isNotBlank() }

            for ((sousLotName, sousLotRows) in groupedBySousLot) {
                val emptySousLot = SousLot(lotId = 0, nom = sousLotName)
                val articlesList = mutableListOf<Article>()

                for (row in sousLotRows) {
                    if (row.articleName.isNotBlank()) {
                        articlesList.add(
                                Article(
                                        sousLotId = 0,
                                        nom = row.articleName,
                                        quantite = row.quantite,
                                        unite = row.unite,
                                        prixUnitaire = row.prixUnitaire
                                )
                        )
                    }
                }
                subLotsList.add(SousLotWithArticles(emptySousLot, articlesList))
            }
            result.add(LotWithSousLots(emptyLot, subLotsList))
        }
        return result
    }
}
