package com.football.footballapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.football.footballapp.data.ApiFootballApi
import com.football.footballapp.data.FiltersStore
import com.football.footballapp.data.FootballDataApi
import com.football.footballapp.data.MatchCache
import com.football.footballapp.data.OpenFootballApi
import com.football.footballapp.data.MatchDetailApi
import com.football.footballapp.data.TvChannelsApi
import com.football.footballapp.repository.MatchRepository
import com.football.footballapp.repository.MatchDetailRepository
import com.football.footballapp.ui.MatchListScreen
import com.football.footballapp.ui.MatchDetailScreen
import com.football.footballapp.ui.MatchDetailViewModel
import com.football.footballapp.ui.theme.FootballScheduleAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiFootballKey = BuildConfig.API_FOOTBALL_KEY.takeIf { it.isNotBlank() }
        val footballDataKey = BuildConfig.FOOTBALL_DATA_API_KEY.takeIf { it.isNotBlank() }

        val matchRepository = MatchRepository(
            apiFootballApi = ApiFootballApi.create(
                apiFootballKey,
                useRapidApi = BuildConfig.API_FOOTBALL_USE_RAPID
            ),
            footballDataApi = FootballDataApi.create(footballDataKey),
            openFootballApi = OpenFootballApi.create(),
            matchCache = MatchCache(filesDir)
        )
        val filtersStore = FiltersStore(applicationContext)

        val matchDetailApi = MatchDetailApi.create(
            apiFootballKey,
            useRapidApi = BuildConfig.API_FOOTBALL_USE_RAPID
        )
        val tvChannelsApi = TvChannelsApi.create(BuildConfig.TV_SERVER_URL)
        val matchDetailRepository = MatchDetailRepository(
            matchDetailApi = matchDetailApi,
            tvChannelsApi = tvChannelsApi
        )

        setContent {
            FootballScheduleAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "matchList") {
                        composable("matchList") {
                            MatchListScreen(
                                repository = matchRepository,
                                filtersStore = filtersStore,
                                onMatchClick = { match ->
                                    navController.navigate("matchDetail/${match.id}")
                                }
                            )
                        }
                        composable(
                            route = "matchDetail/{matchId}",
                            arguments = listOf(
                                navArgument("matchId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val matchId = backStackEntry.arguments?.getLong("matchId") ?: 0L
                            val match = matchRepository.getCachedMatch(matchId)

                            if (match != null) {
                                val matchDetailViewModel: MatchDetailViewModel = viewModel(
                                    key = "detail_$matchId",
                                    factory = MatchDetailViewModel.Factory(matchDetailRepository, match)
                                )
                                MatchDetailScreen(
                                    viewModel = matchDetailViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                Surface(modifier = Modifier.fillMaxSize()) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        androidx.compose.material3.Text("Match non trouvé")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
