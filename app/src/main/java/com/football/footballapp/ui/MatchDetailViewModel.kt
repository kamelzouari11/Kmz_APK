package com.football.footballapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchDetail
import com.football.footballapp.repository.MatchDetailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchDetailUiState(
    val isLoading: Boolean = false,
    val matchDetail: MatchDetail? = null,
    val error: String? = null
)

class MatchDetailViewModel(
    private val repository: MatchDetailRepository,
    val match: Match
) : ViewModel() {

    private val _state = MutableStateFlow(MatchDetailUiState())
    val state: StateFlow<MatchDetailUiState> = _state.asStateFlow()

    fun loadMatchDetail(
        forceRefresh: Boolean = false
    ) {
        if (_state.value.isLoading && !forceRefresh) return

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            repository.getMatchDetail(
                matchId = match.id,
                source = match.source,
                forceRefresh = forceRefresh
            ).onSuccess { detail ->
                _state.update { it.copy(isLoading = false, matchDetail = detail, error = null) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Unknown error occurred") }
            }
        }
    }

    

    class Factory(
        private val repository: MatchDetailRepository,
        private val match: Match
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MatchDetailViewModel(repository, match) as T
    }
}
