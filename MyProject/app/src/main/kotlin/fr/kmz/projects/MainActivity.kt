package fr.kmz.projects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.room.Room
import fr.kmz.projects.data.db.RenovationDatabase
import fr.kmz.projects.data.repository.RenovationRepository
import fr.kmz.projects.ui.screens.ArticlesScreen
import fr.kmz.projects.ui.screens.LotsScreen
import fr.kmz.projects.ui.screens.SousLotsScreen
import fr.kmz.projects.ui.theme.MyProjectTheme
import fr.kmz.projects.ui.viewmodel.ArticleViewModel
import fr.kmz.projects.ui.viewmodel.LotViewModel
import fr.kmz.projects.ui.viewmodel.SousLotViewModel
import fr.kmz.projects.ui.viewmodel.SyncState

class MainActivity : ComponentActivity() {
    private lateinit var db: RenovationDatabase
    private lateinit var repository: RenovationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db =
                Room.databaseBuilder(
                                applicationContext,
                                RenovationDatabase::class.java,
                                "renovation_db"
                        )
                        .addMigrations(RenovationDatabase.MIGRATION_1_2)
                        .build()

        repository = RenovationRepository(db.lotDao(), db.sousLotDao(), db.articleDao(), db)

        setContent {
            MyProjectTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    val configuration = LocalConfiguration.current
                    val isTabletOrPc = configuration.screenWidthDp >= 800

                    val currentScreen = remember { mutableStateOf<Screen>(Screen.Lots) }
                    val selectedLotId = remember { mutableStateOf(0L) }
                    val selectedLotName = remember { mutableStateOf("") }
                    val selectedSousLotId = remember { mutableStateOf(0L) }
                    val selectedSousLotName = remember { mutableStateOf("") }

                    // Always memoize viewmodels so they don't recreate on each recomposition
                    val lotViewModel = remember(repository) { LotViewModel(repository) }
                    val sousLotViewModel =
                            remember(repository, selectedLotId.value) {
                                SousLotViewModel(repository, selectedLotId.value)
                            }
                    val articleViewModel =
                            remember(repository, selectedSousLotId.value) {
                                ArticleViewModel(repository, selectedSousLotId.value)
                            }

                    if (isTabletOrPc) {
                        // MODE TABLEUR (PC / TABLETTE)
                        val syncState by lotViewModel.syncState.collectAsState()
                        Scaffold(
                                topBar = {
                                    @OptIn(ExperimentalMaterial3Api::class)
                                    TopAppBar(
                                            title = { Text("Budget Rénovation") },
                                            actions = {
                                                // Sync status indicator
                                                when (val s = syncState) {
                                                    is SyncState.Loading ->
                                                            Text(
                                                                    "⏳",
                                                                    modifier =
                                                                            androidx.compose.ui
                                                                                    .Modifier
                                                                                    .padding(
                                                                                            horizontal =
                                                                                                    8.dp
                                                                                    )
                                                            )
                                                    is SyncState.Error ->
                                                            Text(
                                                                    "❌ ${s.message.take(20)}",
                                                                    style =
                                                                            MaterialTheme.typography
                                                                                    .bodySmall,
                                                                    color =
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .error,
                                                                    modifier =
                                                                            androidx.compose.ui
                                                                                    .Modifier
                                                                                    .padding(
                                                                                            horizontal =
                                                                                                    8.dp
                                                                                    )
                                                            )
                                                    is SyncState.Success ->
                                                            Text(
                                                                    "✅",
                                                                    modifier =
                                                                            androidx.compose.ui
                                                                                    .Modifier
                                                                                    .padding(
                                                                                            horizontal =
                                                                                                    8.dp
                                                                                    )
                                                            )
                                                    else -> {}
                                                }
                                                IconButton(
                                                        onClick = {
                                                            lotViewModel.downloadFromGitHub()
                                                        }
                                                ) {
                                                    Icon(
                                                            Icons.Filled.CloudDownload,
                                                            contentDescription =
                                                                    "Télécharger depuis GitHub"
                                                    )
                                                }
                                                IconButton(
                                                        onClick = { lotViewModel.uploadToGitHub() }
                                                ) {
                                                    Icon(
                                                            Icons.Filled.CloudUpload,
                                                            contentDescription =
                                                                    "Sauvegarder sur GitHub"
                                                    )
                                                }
                                            }
                                    )
                                }
                        ) { scaffoldPadding ->
                            Row(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
                                // Section 1: Lots
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    LotsScreen(
                                            viewModel = lotViewModel,
                                            isTabletMode = true,
                                            onLotSelected = { lotId, lotName ->
                                                selectedLotId.value = lotId
                                                selectedLotName.value = lotName
                                                selectedSousLotId.value = 0L // reset article view
                                            }
                                    )
                                }

                                // Section 2: Sous-Lots
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    if (selectedLotId.value != 0L) {
                                        SousLotsScreen(
                                                viewModel = sousLotViewModel,
                                                lotName = selectedLotName.value,
                                                onBackClick = { /* No back in tablet mode */},
                                                onSousLotSelected = { sousLotId, sousLotName ->
                                                    selectedSousLotId.value = sousLotId
                                                    selectedSousLotName.value = sousLotName
                                                },
                                                isTabletMode = true
                                        )
                                    } else {
                                        Box(
                                                Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) { Text("Sélectionnez un Lot à gauche") }
                                    }
                                }

                                // Section 3: Articles (Tableur)
                                Box(modifier = Modifier.weight(2.5f).fillMaxHeight()) {
                                    if (selectedSousLotId.value != 0L) {
                                        ArticlesScreen(
                                                viewModel = articleViewModel,
                                                sousLotName = selectedSousLotName.value,
                                                onBackClick = { /* No back in tablet mode */},
                                                isTabletMode = true
                                        )
                                    } else {
                                        Box(
                                                Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                        ) {
                                            Text("Sélectionnez un Sous-Lot pour voir les articles")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // MODE MOBILE STACK
                        when (val screen = currentScreen.value) {
                            Screen.Lots -> {
                                LotsScreen(
                                        viewModel = lotViewModel,
                                        onLotSelected = { lotId, lotName ->
                                            selectedLotId.value = lotId
                                            selectedLotName.value = lotName
                                            currentScreen.value = Screen.SousLots
                                        }
                                )
                            }
                            Screen.SousLots -> {
                                SousLotsScreen(
                                        viewModel = sousLotViewModel,
                                        lotName = selectedLotName.value,
                                        onBackClick = { currentScreen.value = Screen.Lots },
                                        onSousLotSelected = { sousLotId, sousLotName ->
                                            selectedSousLotId.value = sousLotId
                                            selectedSousLotName.value = sousLotName
                                            currentScreen.value = Screen.Articles
                                        }
                                )
                            }
                            Screen.Articles -> {
                                ArticlesScreen(
                                        viewModel = articleViewModel,
                                        sousLotName = selectedSousLotName.value,
                                        onBackClick = { currentScreen.value = Screen.SousLots }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Lots : Screen()
    object SousLots : Screen()
    object Articles : Screen()
}
