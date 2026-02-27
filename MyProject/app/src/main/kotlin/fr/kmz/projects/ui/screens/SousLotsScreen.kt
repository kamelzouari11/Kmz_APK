package fr.kmz.projects.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.kmz.projects.data.model.SousLot
import fr.kmz.projects.ui.components.RecapCard
import fr.kmz.projects.ui.components.SousLotCard
import fr.kmz.projects.ui.viewmodel.SousLotViewModel
import fr.kmz.projects.utils.FormattingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SousLotsScreen(
        viewModel: SousLotViewModel,
        lotName: String,
        onBackClick: () -> Unit,
        onSousLotSelected: (Long, String) -> Unit,
        modifier: Modifier = Modifier
) {
    val sousLots by viewModel.sousLots.collectAsState()
    val sousLotTotals by viewModel.sousLotTotals.collectAsState()

    // compute lot total to show in header
    val lotTotal = sousLotTotals.values.sum()
    var showDialog by remember { mutableStateOf(false) }
    var editingSousLot by remember { mutableStateOf<SousLot?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingSousLot by remember { mutableStateOf<SousLot?>(null) }
    var nomInput by remember { mutableStateOf("") }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(lotName) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Retour"
                                )
                            }
                        }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                        onClick = {
                            editingSousLot = null
                            nomInput = ""
                            showDialog = true
                        }
                ) { Icon(Icons.Filled.Add, contentDescription = "Ajouter un Sous-Lot") }
            },
            modifier = modifier
    ) { padding ->
        if (sousLots.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(
                        "Aucun sous-lot. Appuyez sur + pour ajouter.",
                        style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sousLots) { sousLot ->
                    SousLotCard(
                            sousLotName = sousLot.nom,
                            total =
                                    FormattingUtils.formatCurrencyNoDecimals(
                                            sousLotTotals[sousLot.id] ?: 0L
                                    ),
                            isValidated = sousLot.isValidated,
                            nbArticles = 0, // À obtenir du repository
                            onEdit = {
                                editingSousLot = sousLot
                                nomInput = sousLot.nom
                                showDialog = true
                            },
                            onNavigate = { onSousLotSelected(sousLot.id, sousLot.nom) }
                    )
                }
                item {
                    RecapCard(
                            title = "Total $lotName",
                            total = FormattingUtils.formatCurrencyNoDecimals(lotTotal)
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showDeleteDialog && deletingSousLot != null) {
        AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    deletingSousLot = null
                },
                title = { Text("Confirmer la suppression") },
                text = {
                    Text(
                            "Voulez-vous vraiment supprimer ce sous-lot ? Cette action est irréversible."
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                viewModel.deleteSousLot(deletingSousLot!!)
                                showDeleteDialog = false
                                deletingSousLot = null
                            }
                    ) { Text("Supprimer") }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showDeleteDialog = false
                                deletingSousLot = null
                            }
                    ) { Text("Annuler") }
                }
        )
    }

    if (showDialog) {
        AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                if (editingSousLot != null) "Modifier le Sous-Lot"
                                else "Nouveau Sous-Lot"
                        )
                        if (editingSousLot != null) {
                            IconButton(
                                    onClick = {
                                        deletingSousLot = editingSousLot
                                        showDialog = false
                                        showDeleteDialog = true
                                    }
                            ) {
                                Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Supprimer",
                                        tint = Color.Red
                                )
                            }
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        OutlinedTextField(
                                value = nomInput,
                                onValueChange = { nomInput = it },
                                label = { Text("Nom du Sous-Lot") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (nomInput.isNotBlank()) {
                                    val currentSousLot = editingSousLot
                                    if (currentSousLot != null) {
                                        viewModel.updateSousLot(currentSousLot.copy(nom = nomInput))
                                    } else {
                                        viewModel.createSousLot(nomInput)
                                    }
                                    showDialog = false
                                }
                            }
                    ) { Text("Enregistrer") }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Annuler") } }
        )
    }
}
