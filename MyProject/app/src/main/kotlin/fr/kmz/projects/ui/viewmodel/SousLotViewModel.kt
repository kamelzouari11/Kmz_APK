package fr.kmz.projects.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kmz.projects.data.model.SousLot
import fr.kmz.projects.data.repository.RenovationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SousLotViewModel(
    private val repository: RenovationRepository,
    private val lotId: Long
) : ViewModel() {
    val sousLots = repository.getSousLotsByLotId(lotId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // include articles to compute subtotals
    val sousLotsWithArticles = repository.getSousLotsWithArticlesByLotId(lotId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // map of sousLotId to total cents (quantities × prix)
    val sousLotTotals = sousLotsWithArticles
        .map { list -> list.associate { it.sousLot.id to it.calculerTotal() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun createSousLot(nom: String) {
        viewModelScope.launch {
            val sousLot = SousLot(lotId = lotId, nom = nom)
            repository.insertSousLot(sousLot)
        }
    }

    fun updateSousLot(sousLot: SousLot) {
        viewModelScope.launch {
            repository.updateSousLot(sousLot)
        }
    }

    fun deleteSousLot(sousLot: SousLot) {
        viewModelScope.launch {
            repository.deleteSousLot(sousLot)
        }
    }

    fun validateSousLot(sousLotId: Long, isValidated: Boolean) {
        viewModelScope.launch {
            repository.validateSousLot(sousLotId, isValidated)
        }
    }
}
