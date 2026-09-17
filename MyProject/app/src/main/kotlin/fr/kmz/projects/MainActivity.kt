package fr.kmz.projects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.room.Room
import fr.kmz.projects.data.db.DepensesDatabase
import fr.kmz.projects.data.model.Projet
import fr.kmz.projects.data.repository.DepensesRepository
import fr.kmz.projects.ui.screens.RapportsScreen
import fr.kmz.projects.ui.screens.SaisieScreen
import fr.kmz.projects.ui.theme.MyProjectTheme
import fr.kmz.projects.ui.viewmodel.DepensesViewModel
import fr.kmz.projects.ui.viewmodel.SyncState

val SnackbarHostStateGlobal = SnackbarHostState()

class MainActivity : ComponentActivity() {
    private lateinit var db: DepensesDatabase
    private lateinit var repository: DepensesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            DepensesDatabase::class.java,
            "depenses_db"
        )
            .addMigrations(DepensesDatabase.MIGRATION_1_2)
            .addMigrations(DepensesDatabase.MIGRATION_2_3)
            .build()

        repository = DepensesRepository(
            db.chapitreDao(),
            db.beneficiaireDao(),
            db.depenseDao(),
            db.projetDao(),
            db
        )

        setContent {
            MyProjectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val configuration = LocalConfiguration.current
                    val isTabletOrPc = configuration.screenWidthDp >= 800

                    val currentScreen = remember { mutableStateOf<Screen>(Screen.Saisie) }
                    val viewModel = remember(repository) { DepensesViewModel(repository) }

                    val syncState by viewModel.syncState.collectAsState()
                    val projets by viewModel.projets.collectAsState()
                    val projetCourant by viewModel.projetCourant.collectAsState()

                    var showSyncDialog by remember { mutableStateOf(false) }
                    var showDownloadConfirm by remember { mutableStateOf(false) }
                    var showUploadConfirm by remember { mutableStateOf(false) }
                    var showSaisieDialog by remember { mutableStateOf(false) }
                    var showNouveauProjet by remember { mutableStateOf(false) }
                    var showDeleteProjetConfirm by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                ProjetSelector(
                                    projets = projets,
                                    projetCourant = projetCourant,
                                    onSelectProjet = { viewModel.selectProjet(it) },
                                    onNouveauProjet = { showNouveauProjet = true }
                                )
                            },
                            actions = {
                                if (projetCourant != null) {
                                    IconButton(onClick = { showDeleteProjetConfirm = true }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer projet")
                                    }
                                }
                                IconButton(onClick = { showDownloadConfirm = true }) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = "Télécharger")
                                }
                                IconButton(onClick = { showUploadConfirm = true }) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = "Sauvegarder")
                                }
                            }
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            when (currentScreen.value) {
                                Screen.Saisie -> SaisieScreen(
                                    viewModel = viewModel,
                                    isTabletMode = isTabletOrPc,
                                    showFormDialog = showSaisieDialog,
                                    onShowFormDialog = { showSaisieDialog = it }
                                )
                                Screen.Rapports -> RapportsScreen(viewModel, isTabletOrPc)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(MaterialTheme.colorScheme.surface),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FooterButton(
                                icon = Icons.Filled.MenuBook,
                                label = "Journal",
                                selected = currentScreen.value == Screen.Saisie,
                                onClick = { currentScreen.value = Screen.Saisie }
                            )
                            FooterButton(
                                icon = Icons.Filled.BarChart,
                                label = "Rapports",
                                selected = currentScreen.value == Screen.Rapports,
                                onClick = { currentScreen.value = Screen.Rapports }
                            )
                            FooterButton(
                                icon = Icons.Filled.Add,
                                label = "Ajouter",
                                selected = false,
                                onClick = {
                                    currentScreen.value = Screen.Saisie
                                    showSaisieDialog = true
                                }
                            )
                        }
                    }

                    SnackbarHost(
                        hostState = SnackbarHostStateGlobal,
                        modifier = Modifier
                    ) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    if (showNouveauProjet) {
                        var nomProjet by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showNouveauProjet = false },
                            title = { Text("Nouveau projet") },
                            text = {
                                OutlinedTextField(
                                    value = nomProjet,
                                    onValueChange = { nomProjet = it },
                                    label = { Text("Nom du projet") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (nomProjet.isNotBlank()) {
                                            viewModel.createProjet(nomProjet.trim())
                                            nomProjet = ""
                                            showNouveauProjet = false
                                        }
                                    }
                                ) { Text("Créer") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    nomProjet = ""
                                    showNouveauProjet = false
                                }) { Text("Annuler") }
                            }
                        )
                    }

                    if (showDeleteProjetConfirm && projetCourant != null) {
                        AlertDialog(
                            onDismissRequest = { showDeleteProjetConfirm = false },
                            title = { Text("Supprimer le projet ?") },
                            text = {
                                Text(
                                    "Supprimer \"${projetCourant!!.nom}\" supprimera aussi toutes ses dépenses, chapitres et bénéficiaires."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteProjet(projetCourant!!)
                                    showDeleteProjetConfirm = false
                                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteProjetConfirm = false }) {
                                    Text("Annuler")
                                }
                            }
                        )
                    }

                    if (showDownloadConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDownloadConfirm = false },
                            title = { Text("Télécharger depuis GitHub ?") },
                            text = {
                                Text(
                                    "Cela remplacera tous les projets, dépenses, chapitres et bénéficiaires par les données du serveur."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDownloadConfirm = false
                                    viewModel.downloadFromGitHub()
                                }) { Text("Télécharger") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDownloadConfirm = false }) {
                                    Text("Annuler")
                                }
                            }
                        )
                    }

                    if (showUploadConfirm) {
                        AlertDialog(
                            onDismissRequest = { showUploadConfirm = false },
                            title = { Text("Sauvegarder sur GitHub ?") },
                            text = {
                                Text(
                                    "Cela remplacera la sauvegarde GitHub par tous les projets et toutes les données locales."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showUploadConfirm = false
                                    viewModel.uploadToGitHub()
                                }) { Text("Sauvegarder") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUploadConfirm = false }) {
                                    Text("Annuler")
                                }
                            }
                        )
                    }

                    if (syncState is SyncState.Success || syncState is SyncState.Error) {
                        showSyncDialog = true
                    }

                    if (showSyncDialog && (syncState is SyncState.Success || syncState is SyncState.Error)) {
                        AlertDialog(
                            onDismissRequest = {
                                showSyncDialog = false
                                viewModel.resetSyncState()
                            },
                            title = { Text(if (syncState is SyncState.Success) "Succès" else "Erreur") },
                            text = {
                                Text(
                                    (syncState as? SyncState.Success)?.message
                                        ?: (syncState as? SyncState.Error)?.message ?: ""
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showSyncDialog = false
                                    viewModel.resetSyncState()
                                }) { Text("OK") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjetSelector(
    projets: List<Projet>,
    projetCourant: Projet?,
    onSelectProjet: (Projet) -> Unit,
    onNouveauProjet: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 4.dp)
    ) {
        TextButton(
            onClick = { expanded = true }
        ) {
            Text(
                text = projetCourant?.nom ?: "Aucun projet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            projets.forEach { projet ->
                DropdownMenuItem(
                    text = {
                        Text(
                            projet.nom,
                            style = if (projet.id == projetCourant?.id)
                                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
                            else
                                MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onSelectProjet(projet)
                        expanded = false
                    }
                )
            }

            if (projets.isNotEmpty()) {
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = {
                    Text(
                        "+ Nouveau projet",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    expanded = false
                    onNouveauProjet()
                }
            )
        }
    }
}

@Composable
private fun FooterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick, modifier = Modifier.height(32.dp)) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

sealed class Screen {
    object Saisie : Screen()
    object Rapports : Screen()
}
