package com.football.footballapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.football.footballapp.data.ApiFootballCountryDto
import com.football.footballapp.data.ApiFootballLeagueEntryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCountriesScreen(
    viewModel: MatchViewModel,
    onBack: () -> Unit,
    onOpenCountry: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val countries by viewModel.availableCountries.collectAsState()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadSettingsCountries() }

    val filtered = remember(countries, search) {
        if (search.isBlank()) countries
        else countries.filter { it.name.contains(search, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pays — Settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchField(value = search, onValueChange = { search = it })

            if (countries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.name }) { country ->
                        CountryRow(
                            country = country,
                            isEnabled = country.name in state.settingsCountries,
                            enabledLeaguesCount = state.settingsLeaguesByCountry[country.name]?.size ?: 0,
                            onToggle = { viewModel.toggleSettingsCountry(country.name) },
                            onClick = { onOpenCountry(country.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Rechercher un pays…", fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Effacer")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50)
    )
}

@Composable
private fun CountryRow(
    country: ApiFootballCountryDto,
    isEnabled: Boolean,
    enabledLeaguesCount: Int,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagImage(url = country.flag, fallback = country.code)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(country.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (isEnabled && enabledLeaguesCount > 0) {
                    Text(
                        "$enabledLeaguesCount compétition(s) activée(s)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isEnabled) {
                    Text(
                        "Activé · aucune compétition cochée",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCompetitionsScreen(
    viewModel: MatchViewModel,
    country: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val leaguesByCountry by viewModel.leaguesByCountry.collectAsState()
    val leagues = leaguesByCountry[country] ?: emptyList()
    val enabledNames = state.settingsLeaguesByCountry[country] ?: emptySet()

    LaunchedEffect(country) { viewModel.loadLeaguesForCountry(country) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(country, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (leagues.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(leagues, key = { it.league.id }) { entry ->
                        LeagueRow(
                            entry = entry,
                            isEnabled = entry.league.name.lowercase().trim() in enabledNames,
                            onToggle = { viewModel.toggleSettingsLeague(country, entry.league.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueRow(
    entry: ApiFootballLeagueEntryDto,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!entry.league.logo.isNullOrBlank()) {
                AsyncImage(
                    model = entry.league.logo,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.league.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    entry.league.type ?: "—",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
fun FlagImage(url: String?, fallback: String?) {
    // Rectangle 3:2 façon vrai drapeau, coins légèrement arrondis
    val shape = RoundedCornerShape(3.dp)
    val width = 32.dp
    val height = 22.dp
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallback?.take(2)?.uppercase() ?: "?",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
