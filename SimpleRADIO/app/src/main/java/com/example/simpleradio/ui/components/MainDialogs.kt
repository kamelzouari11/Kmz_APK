package com.example.simpleradio.ui.components

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.data.local.entities.RadioFavoriteListEntity
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.example.simpleradio.ui.components.dialogs.*
import kotlinx.coroutines.launch

/**
 * Orchestrates all secondary dialogs used in the MainScreen. Reduces the boilerplate in
 * MainActivity.kt.
 */
@Composable
fun MainDialogs(
        radioRepository: RadioRepository,
        showAddCustomUrlDialog: Boolean,
        onSetShowAddCustomUrlDialog: (Boolean) -> Unit,
        showAddListDialog: Boolean,
        onSetShowAddListDialog: (Boolean) -> Unit,
        showSearchDialog: Boolean,
        onSetShowSearchDialog: (Boolean) -> Unit,
        radioSearchQuery: String,
        onSetRadioSearchQuery: (String) -> Unit,
        onSetShowRecentRadiosOnly: (Boolean) -> Unit,
        onSetIsViewingRadioResults: (Boolean) -> Unit,
        onIncrementSearchTrigger: () -> Unit,
        radioToFavorite: RadioStationEntity?,
        onSetRadioToFavorite: (RadioStationEntity?) -> Unit,
        radioFavoriteLists: List<RadioFavoriteListEntity>
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showAddCustomUrlDialog) {
        AddCustomUrlDialog(
                onDismiss = { onSetShowAddCustomUrlDialog(false) },
                onConfirm = { name, url ->
                    scope.launch {
                        radioRepository.addCustomRadio(name, url)
                        Toast.makeText(context, "Ajouté à 'mes urls'", Toast.LENGTH_SHORT).show()
                    }
                    onSetShowAddCustomUrlDialog(false)
                }
        )
    }

    if (showAddListDialog) {
        AddFavoriteListDialog(
                onDismiss = { onSetShowAddListDialog(false) },
                onConfirm = { name ->
                    scope.launch { radioRepository.addFavoriteList(name) }
                    onSetShowAddListDialog(false)
                }
        )
    }

    if (showSearchDialog) {
        SearchDialog(
                initialQuery = radioSearchQuery,
                onDismiss = { onSetShowSearchDialog(false) },
                onSearch = { query ->
                    onSetRadioSearchQuery(query)
                    onSetShowRecentRadiosOnly(false)
                    onSetIsViewingRadioResults(true)
                    onIncrementSearchTrigger()
                    onSetShowSearchDialog(false)
                }
        )
    }

    if (radioToFavorite != null) {
        GenericFavoriteDialog(
                title = "Favoris Radio",
                items = radioFavoriteLists,
                getName = { it.name },
                getId = { it.id },
                onDismiss = { onSetRadioToFavorite(null) },
                onToggle = { listId ->
                    scope.launch {
                        radioRepository.toggleRadioFavorite(radioToFavorite.stationuuid, listId)
                    }
                },
                selectedIdsProvider = {
                    radioRepository.getListIdsForRadio(radioToFavorite.stationuuid)
                }
        )
    }
}
