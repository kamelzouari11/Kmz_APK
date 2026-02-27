package fr.kmz.projects.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kmz.projects.data.model.Lot
import fr.kmz.projects.data.repository.RenovationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LotViewModel(private val repository: RenovationRepository) : ViewModel() {
    val lots = repository.getAllLots()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // lots including their sous-lots+articles for totals
    val lotsWithSousLots = repository.getAllLotsWithSousLots()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // map lotId -> total cents
    val lotTotals = lotsWithSousLots
        .map { list -> list.associate { it.lot.id to it.calculerTotal() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // overall project total
    val projectTotal = lotTotals
        .map { map -> map.values.sum() }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    fun createLot(nom: String) {
        viewModelScope.launch {
            val lot = Lot(nom = nom)
            repository.insertLot(lot)
        }
    }

    fun updateLot(lot: Lot) {
        viewModelScope.launch {
            repository.updateLot(lot)
        }
    }

    fun deleteLot(lot: Lot) {
        viewModelScope.launch {
            repository.deleteLot(lot)
        }
    }

    fun validateLot(lotId: Long, isValidated: Boolean) {
        viewModelScope.launch {
            repository.validateLot(lotId, isValidated)
        }
    }
}
