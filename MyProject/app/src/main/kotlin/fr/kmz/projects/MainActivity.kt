package fr.kmz.projects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.room.Room
import fr.kmz.projects.data.db.DepensesDatabase
import fr.kmz.projects.data.repository.DepensesRepository
import fr.kmz.projects.ui.screens.RapportsScreen
import fr.kmz.projects.ui.screens.SaisieScreen
import fr.kmz.projects.ui.theme.MyProjectTheme
import fr.kmz.projects.ui.viewmodel.DepensesViewModel
import fr.kmz.projects.ui.viewmodel.SyncState

class MainActivity : ComponentActivity() {
    private lateinit var db: DepensesDatabase
    private lateinit var repository: DepensesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            DepensesDatabase::class.java,
            "depenses_db"
        ).build()

        repository = DepensesRepository(db.chapitreDao(), db.beneficiaireDao(), db.depenseDao(), db)

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
                    var showSyncDialog by remember { mutableStateOf(false) }

                    Scaffold(
                        topBar = {
                            @OptIn(ExperimentalMaterial3Api::class)
                            TopAppBar(
                                title = { Text("Dépenses") },
                                actions = {
                                    when (val s = syncState) {
                                        is SyncState.Loading -> Text(
                                            "⏳",
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        is SyncState.Error -> Text(
                                            "❌ ${s.message.take(20)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        is SyncState.Success -> Text(
                                            "✅",
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        else -> {}
                                    }
                                    IconButton(onClick = { viewModel.downloadFromGitHub() }) {
                                        Icon(
                                            Icons.Filled.CloudDownload,
                                            contentDescription = "Télécharger depuis GitHub"
                                        )
                                    }
                                    IconButton(onClick = { viewModel.uploadToGitHub() }) {
                                        Icon(
                                            Icons.Filled.CloudUpload,
                                            contentDescription = "Sauvegarder sur GitHub"
                                        )
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            BottomAppBar {
                                NavigationBarItem(
                                    selected = currentScreen.value == Screen.Saisie,
                                    onClick = { currentScreen.value = Screen.Saisie },
                                    label = { Text("Saisie") },
                                    icon = { Text("✏️") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen.value == Screen.Rapports,
                                    onClick = { currentScreen.value = Screen.Rapports },
                                    label = { Text("Rapports") },
                                    icon = { Text("📊") }
                                )
                            }
                        }
                    ) { paddingValues ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            when (currentScreen.value) {
                                Screen.Saisie -> SaisieScreen(viewModel, isTabletOrPc)
                                Screen.Rapports -> RapportsScreen(viewModel, isTabletOrPc)
                            }
                        }
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
                            text = { Text((syncState as? SyncState.Success)?.message ?: (syncState as? SyncState.Error)?.message ?: "") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showSyncDialog = false
                                        viewModel.resetSyncState()
                                    }
                                ) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Saisie : Screen()
    object Rapports : Screen()
}
