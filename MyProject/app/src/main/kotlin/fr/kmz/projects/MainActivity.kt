package fr.kmz.projects

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

        repository = RenovationRepository(db.lotDao(), db.sousLotDao(), db.articleDao())

        setContent {
            MyProjectTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen = remember { mutableStateOf<Screen>(Screen.Lots) }
                    val selectedLotId = remember { mutableStateOf(0L) }
                    val selectedLotName = remember { mutableStateOf("") }
                    val selectedSousLotId = remember { mutableStateOf(0L) }
                    val selectedSousLotName = remember { mutableStateOf("") }

                    when (val screen = currentScreen.value) {
                        Screen.Lots -> {
                            val lotViewModel = LotViewModel(repository)
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
                            val sousLotViewModel = SousLotViewModel(repository, selectedLotId.value)
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
                            val articleViewModel =
                                    ArticleViewModel(repository, selectedSousLotId.value)
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

sealed class Screen {
    object Lots : Screen()
    object SousLots : Screen()
    object Articles : Screen()
}
