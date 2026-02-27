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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
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
import fr.kmz.projects.data.model.Lot
import fr.kmz.projects.ui.components.LotCard
import fr.kmz.projects.ui.components.RecapCard
import fr.kmz.projects.ui.viewmodel.LotViewModel
import fr.kmz.projects.utils.FormattingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotsScreen(
        viewModel: LotViewModel,
        onLotSelected: (Long, String) -> Unit,
        modifier: Modifier = Modifier
) {
    val lots by viewModel.lots.collectAsState()
    val lotTotals by viewModel.lotTotals.collectAsState()
    val projectTotal by viewModel.projectTotal.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingLot by remember { mutableStateOf<Lot?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingLot by remember { mutableStateOf<Lot?>(null) }
    var nomInput by remember { mutableStateOf("") }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Budget Rénovation") },
                        actions = {
                            IconButton(onClick = { /* TODO: Download from GitHub */}) {
                                Icon(
                                        Icons.Filled.CloudDownload,
                                        contentDescription = "Télécharger depuis GitHub"
                                )
                            }
                            IconButton(onClick = { /* TODO: Upload to GitHub */}) {
                                Icon(
                                        Icons.Filled.CloudUpload,
                                        contentDescription = "Sauvegarder sur GitHub"
                                )
                            }
                        }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                        onClick = {
                            editingLot = null
                            nomInput = ""
                            showDialog = true
                        }
                ) { Icon(Icons.Filled.Add, contentDescription = "Ajouter un Lot") }
            },
            modifier = modifier
    ) { padding ->
        if (lots.isEmpty()) {
            Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.Center
            ) {
                Text(
                        "Aucun lot créé. Appuyez sur + pour commencer.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(lots) { lot ->
                    val total = lotTotals[lot.id] ?: 0L
                    LotCard(
                            lotName = lot.nom,
                            total = FormattingUtils.formatCurrencyNoDecimals(total),
                            isValidated = lot.isValidated,
                            onEdit = {
                                editingLot = lot
                                nomInput = lot.nom
                                showDialog = true
                            },
                            onNavigate = { onLotSelected(lot.id, lot.nom) }
                    )
                }
                item {
                    RecapCard(
                            title = "Budget Rénovation",
                            total = FormattingUtils.formatCurrencyNoDecimals(projectTotal)
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showDeleteDialog && deletingLot != null) {
        AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    deletingLot = null
                },
                title = { Text("Confirmer la suppression") },
                text = {
                    Text("Voulez-vous vraiment supprimer ce lot ? Cette action est irréversible.")
                },
                confirmButton = {
                    Button(
                            onClick = {
                                viewModel.deleteLot(deletingLot!!)
                                showDeleteDialog = false
                                deletingLot = null
                            }
                    ) { Text("Supprimer") }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showDeleteDialog = false
                                deletingLot = null
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
                        Text(if (editingLot != null) "Modifier le Lot" else "Nouveau Lot")
                        if (editingLot != null) {
                            IconButton(
                                    onClick = {
                                        deletingLot = editingLot
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
                                label = { Text("Nom du Lot") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (nomInput.isNotBlank()) {
                                    val currentLot = editingLot
                                    if (currentLot != null) {
                                        viewModel.updateLot(currentLot.copy(nom = nomInput))
                                    } else {
                                        viewModel.createLot(nomInput)
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

fun calculateLotTotal(lot: Lot): Long {
    // À adapter selon votre logique de calcul
    return 0L
}
