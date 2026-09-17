package com.example.myiptv.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiptv.data.*
import com.example.myiptv.ui.theme.MyIptvPalette
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Independent cinema catalogue; kept composed during playback to preserve the current card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CinemaScreen(visible: Boolean, onPlay: (SavedChannel) -> Unit, onDismiss: () -> Unit) {
    ProgrammeCatalogueScreen(sports = false, visible = visible, onPlay = onPlay, onDismiss = onDismiss)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SportsScreen(visible: Boolean, onPlay: (SavedChannel) -> Unit, onDismiss: () -> Unit) {
    ProgrammeCatalogueScreen(sports = true, visible = visible, onPlay = onPlay, onDismiss = onDismiss)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgrammeCatalogueScreen(
    sports: Boolean,
    visible: Boolean,
    onPlay: (SavedChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val tvLandscape = isTvLandscape()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val repository = remember { CinemaRepository(context) }
    val filterPreferences = remember {
        context.getSharedPreferences(
            if (sports) SPORTS_FILTER_PREFERENCES else CINEMA_FILTER_PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        )
    }
    val initialCountryCodes = remember {
        if (filterPreferences.contains(CINEMA_FILTER_COUNTRIES)) {
            val saved = filterPreferences.getStringSet(CINEMA_FILTER_COUNTRIES, emptySet()).orEmpty()
            cinemaCountries.map(CinemaCountry::code).filter(saved::contains)
        } else {
            cinemaCountries.map(CinemaCountry::code)
        }
    }
    val initialPeriod = remember {
        filterPreferences.getString(CINEMA_FILTER_PERIOD, null)
            ?.takeIf { saved -> CinemaPeriod.entries.any { it.name == saved } }
            ?: CinemaPeriod.NOW.name
    }
    var selectedCountryCodes by rememberSaveable {
        mutableStateOf(initialCountryCodes)
    }
    var periodName by rememberSaveable { mutableStateOf(initialPeriod) }
    var catalogueQuery by rememberSaveable { mutableStateOf("") }
    var appliedCatalogueQuery by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var consumedRefresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(CinemaPage(emptyList(), emptyList())) }
    var channels by remember { mutableStateOf(emptyList<SavedChannel>()) }
    var channelsLoaded by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    var association by remember { mutableStateOf<CinemaProgram?>(null) }
    var variantProgram by remember { mutableStateOf<CinemaProgram?>(null) }
    var selectedProgram by remember { mutableStateOf<CinemaProgram?>(null) }
    var focusedProgram by remember { mutableStateOf<CinemaProgram?>(null) }
    var linksVersion by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val headerFocusRequester = remember { FocusRequester() }
    val strongSearchIndex by produceState<List<IndexedCinemaChannel>?>(null, channels) {
        value = withContext(Dispatchers.Default) {
            channels.map { IndexedCinemaChannel(it, cinemaName(it.name)) }
        }
    }
    val day = now.atZone(EpgSearch.tunis).toLocalDate()
    LaunchedEffect(Unit) {
        while (true) { now = Instant.now(); delay(30_000) }
    }
    LaunchedEffect(selectedCountryCodes) {
        filterPreferences.edit()
            .putStringSet(CINEMA_FILTER_COUNTRIES, selectedCountryCodes.toSet())
            .apply()
    }
    LaunchedEffect(periodName) {
        filterPreferences.edit().putString(CINEMA_FILTER_PERIOD, periodName).apply()
    }
    LaunchedEffect(catalogueQuery) {
        if (catalogueQuery.isBlank()) {
            appliedCatalogueQuery = ""
        } else {
            delay(450)
            appliedCatalogueQuery = catalogueQuery
        }
    }
    LaunchedEffect(Unit) {
        channels = runCatching { repository.strongChannels() }.getOrDefault(emptyList())
        channelsLoaded = true
    }
    LaunchedEffect(selectedCountryCodes, refresh, day) {
        val force = refresh != consumedRefresh
        consumedRefresh = refresh
        loading = true
        page = CinemaPage(emptyList(), emptyList())
        try {
            page = repository.load(selectedCountryCodes.toSet(), force, sports) {
                // Repository runs on IO; snapshot state is safe to update across threads.
                status = it
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            page = CinemaPage(
                emptyList(),
                listOf("Impossible de charger les programmes ${if (sports) "sportifs" else "cinéma"}."),
            )
        }
        finally { loading = false }
    }
    var programs by remember { mutableStateOf(emptyList<CinemaProgram>()) }
    LaunchedEffect(page, periodName, now, appliedCatalogueQuery) {
        programs = withContext(Dispatchers.Default) {
            if (appliedCatalogueQuery.isBlank()) {
                cinemaProgramsInPeriod(page.programs, CinemaPeriod.valueOf(periodName), now)
            } else {
                page.programs.filter { program -> catalogueMatches(program, appliedCatalogueQuery) }
                    .sortedWith(compareBy<CinemaProgram> { it.start }.thenBy { it.title })
            }
        }
    }
    LaunchedEffect(programs) {
        focusedProgram = focusedProgram?.takeIf { focused -> programs.any { it.key == focused.key } }
            ?: programs.firstOrNull()
    }
    val play: (CinemaProgram, SavedChannel) -> Unit = { p, c ->
        repository.rememberVariant(p, c)
        linksVersion++
        variantProgram = null
        onPlay(c)
    }
    if (visible) Dialog(
        onDismissRequest = {
            if (selectedProgram != null) selectedProgram = null else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MyIptvPalette.Background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = if (portrait) 112.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (sports) "SPORTS" else "CINÉMA", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    MenuButton("Actualiser", enabled = !loading, onClick = { refresh++ })
                    MenuButton("Fermer", onClick = onDismiss, modifier = Modifier.focusRequester(headerFocusRequester))
                }
                if (selectedProgram == null) {
                    TvTextField(
                        value = catalogueQuery,
                        onValueChange = { catalogueQuery = it },
                        label = { Text("Rechercher · toutes périodes, pays actifs") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CinemaPeriod.entries.forEach { period ->
                            MenuButton(period.label, active = periodName == period.name, onClick = { periodName = period.name })
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val allSelected = selectedCountryCodes.size == cinemaCountries.size
                        CinemaCountryToggle("All", allSelected) {
                            selectedCountryCodes = if (allSelected) emptyList() else cinemaCountries.map(CinemaCountry::code)
                        }
                        cinemaCountries.forEach { country ->
                            CinemaCountryToggle(country.name, country.code in selectedCountryCodes) {
                                selectedCountryCodes = if (country.code in selectedCountryCodes) {
                                    selectedCountryCodes - country.code
                                } else {
                                    selectedCountryCodes + country.code
                                }
                            }
                        }
                    }
                    val coverageFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(EpgSearch.tunis)
                    val first = page.programs.minOfOrNull(CinemaProgram::start)
                    val last = page.programs.maxOfOrNull(CinemaProgram::stop)
                    Text(
                        if (first == null || last == null) "Aucun pays sélectionné"
                        else "Guide : ${coverageFormat.format(Instant.ofEpochSecond(first))} → ${coverageFormat.format(Instant.ofEpochSecond(last))}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (catalogueQuery.isNotBlank()) {
                        Text(
                            if (catalogueQuery != appliedCatalogueQuery) "Recherche…"
                            else "${programs.size} résultat(s) · recherche dans toutes les périodes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MyIptvPalette.Positive,
                        )
                    }
                }
                if (channelsLoaded && channels.isEmpty()) {
                    Text("Aucune chaîne locale trouvée dans le profil STRONG IPTV. Synchronisez ce profil pour activer les associations.")
                }
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status)
                }
                if (page.notices.isNotEmpty()) Text(page.notices.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                if (!loading && programs.isEmpty() && selectedProgram == null) {
                    Text("Aucun programme ${if (sports) "sportif" else "cinéma"} pour cette sélection. Essayez une autre période ou un autre pays.")
                }
                val detail = selectedProgram
                if (detail == null) {
                    if (tvLandscape) {
                        CinemaTvBrowser(
                            programs = programs,
                            focusedProgram = focusedProgram,
                            now = now,
                            sports = sports,
                            headerFocusRequester = headerFocusRequester,
                            channelLogo = { program ->
                                repository.variants(program, channels).firstOrNull()?.iconUrl
                            },
                            onFocused = { focusedProgram = it },
                            onClick = { selectedProgram = it },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (portrait) 1 else 2),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = if (portrait) 56.dp else 20.dp),
                        ) {
                            gridItems(programs, key = CinemaProgram::key) { program ->
                                CinemaPosterCard(
                                    program = program,
                                    sports = sports,
                                    channelLogoUrl = repository.variants(program, channels).firstOrNull()?.iconUrl,
                                    onClick = { selectedProgram = program },
                                )
                            }
                        }
                    }
                } else {
                    val p = detail
                    val variants = remember(p.channelKey, channels, linksVersion) { repository.variants(p, channels) }
                    val format = DateTimeFormatter.ofPattern("HH:mm").withZone(EpgSearch.tunis)
                    val current = p.start <= now.epochSecond && p.stop > now.epochSecond
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        LargeEpgProgramCard(
                            originalTitle = p.title,
                            description = listOf(p.year, p.genres, p.description).filter { it.isNotBlank() }.joinToString("\n"),
                            country = p.country,
                            timeLabel = "${format.format(Instant.ofEpochSecond(p.start))} → ${format.format(Instant.ofEpochSecond(p.stop))}",
                            channelName = "${p.channelName} · ${cinemaCountries.firstOrNull { it.code == p.country }?.name ?: p.country}",
                            channelLogoUrl = variants.firstOrNull()?.iconUrl, current = current,
                            programImageUrl = p.image, movieOnly = !sports,
                        ) {
                            Text(if (variants.isEmpty()) "Aucune chaîne Strong associée" else "${variants.size} variante(s) Strong disponibles")
                            MenuButton(
                                if (current) "▶ Regarder" else "▶ Ouvrir la chaîne en direct",
                                enabled = variants.isNotEmpty(),
                                onClick = { if (variants.size == 1) play(p, variants.first()) else variantProgram = p },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (variants.isNotEmpty()) MenuButton("Changer de variante", onClick = { variantProgram = p }, modifier = Modifier.fillMaxWidth())
                            MenuButton("Associer / modifier les chaînes", enabled = channels.isNotEmpty(), onClick = { association = p }, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { runCatching { uriHandler.openUri(if (p.source.contains("xmltvfr.fr")) "https://xmltvfr.fr/" else "https://epgshare01.online/") } }) {
                                Text(if (p.source.contains("xmltvfr.fr")) "Programmes : XMLTV France" else "Programmes : EPGShare01")
                            }
                            if (!current) Text("Ce bouton ouvre le direct actuel, pas cette diffusion en différé.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MenuButton(
                        if (tvLandscape) "← Retour à la liste" else "← Retour à la mosaïque",
                        onClick = { selectedProgram = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (visible) variantProgram?.let { p ->
        val variants = repository.variants(p, channels)
        Dialog(onDismissRequest = { variantProgram = null }) {
            Surface(shape = MaterialTheme.shapes.large, color = MyIptvPalette.Background) {
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${p.channelName} · Choisir une variante")
                    Text("Toutes les qualités sont conservées. Dernière variante utilisée en tête.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(
                        Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        items(variants, key = { "${it.profileId}:${it.streamId}" }) { c ->
                            MenuButton("${c.name} · #${c.streamId}", onClick = { play(p, c) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), maxLines = 3)
                        }
                    }
                    MenuButton("Retour", onClick = { variantProgram = null })
                }
            }
        }
    }
    if (visible) association?.let { p ->
        CinemaAssociationDialog(p, strongSearchIndex, repository.variants(p, channels), onDismiss = { association = null }) { ids ->
            repository.associate(p, channels, ids)
            linksVersion++
            association = null
        }
    }
}

@Composable
private fun CinemaPosterCard(
    program: CinemaProgram,
    sports: Boolean,
    channelLogoUrl: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().tvFocusBorder().clickable(onClick = onClick),
        color = MyIptvPalette.Card,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MyIptvPalette.Border),
    ) {
        Column {
            CinemaArtwork(program, sports, channelLogoUrl, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                program.title,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CinemaArtwork(
    program: CinemaProgram,
    sports: Boolean,
    channelLogoUrl: String?,
    modifier: Modifier = Modifier,
) {
    var directImageFailed by remember(program.image) { mutableStateOf(false) }
    var searchedImageFailed by remember(program.title) { mutableStateOf(false) }
    val searchedArtwork by produceState<EpgArtwork?>(null, program.title, directImageFailed) {
        if (program.image.isBlank() || directImageFailed) {
            value = runCatching {
                if (sports) EpgArtworkRepository.find(program.title, program.country)
                else EpgArtworkRepository.findMovie(program.title)
            }.getOrNull()
        }
    }
    val directUrl = program.image.takeIf { it.startsWith("https://") && !directImageFailed }
    val searchedUrl = searchedArtwork?.images?.firstOrNull()?.url?.takeUnless { searchedImageFailed }
    val imageUrl = directUrl ?: searchedUrl ?: channelLogoUrl
    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (imageUrl == null) {
            Text(
                if (sports) "Illustration indisponible" else "Pochette indisponible",
                modifier = Modifier.padding(8.dp),
                color = MyIptvPalette.TextSecondary,
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = "${if (sports) "Illustration" else "Pochette"} de ${program.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = {
                    when (imageUrl) {
                        directUrl -> directImageFailed = true
                        searchedUrl -> searchedImageFailed = true
                    }
                },
            )
        }
    }
}

@Composable
private fun CinemaTvBrowser(
    programs: List<CinemaProgram>,
    focusedProgram: CinemaProgram?,
    now: Instant,
    sports: Boolean,
    headerFocusRequester: FocusRequester,
    channelLogo: (CinemaProgram) -> String?,
    onFocused: (CinemaProgram) -> Unit,
    onClick: (CinemaProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = remember { DateTimeFormatter.ofPattern("dd/MM · HH:mm").withZone(EpgSearch.tunis) }
    val listState = rememberLazyListState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val lastItemFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(programs.size, key = { programs[it].key }) { index ->
                val program = programs[index]
                val edgeFocusModifier = when (index) {
                    0 -> Modifier.focusRequester(firstItemFocusRequester)
                    programs.lastIndex -> Modifier.focusRequester(lastItemFocusRequester)
                    else -> Modifier
                }
                TvListItem(
                    selected = focusedProgram?.key == program.key,
                    onClick = { onClick(program) },
                    onFocused = { onFocused(program) },
                    modifier = edgeFocusModifier.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (index == 0) {
                                    headerFocusRequester.requestFocus()
                                } else {
                                    scope.launch {
                                        listState.scrollToItem(0)
                                        withFrameNanos { }
                                        firstItemFocusRequester.requestFocus()
                                    }
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                if (index == programs.lastIndex) {
                                    headerFocusRequester.requestFocus()
                                } else {
                                    scope.launch {
                                        listState.scrollToItem(programs.lastIndex)
                                        withFrameNanos { }
                                        lastItemFocusRequester.requestFocus()
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    },
                ) { _, contentColor ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(program.title, color = contentColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${format.format(Instant.ofEpochSecond(program.start))} · ${program.channelName}",
                            color = MyIptvPalette.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (program.start <= now.epochSecond && program.stop > now.epochSecond) {
                            val progress = ((now.epochSecond - program.start).toFloat() /
                                (program.stop - program.start).toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                color = MyIptvPalette.Positive,
                            )
                        }
                    }
                }
            }
        }
        Surface(
            Modifier.weight(1f).fillMaxHeight(),
            color = MyIptvPalette.Card,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MyIptvPalette.Border),
        ) {
            focusedProgram?.let { program ->
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CinemaArtwork(
                        program,
                        sports,
                        channelLogo(program),
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                    Text(program.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${format.format(Instant.ofEpochSecond(program.start))} · ${program.channelName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyIptvPalette.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CinemaCountryToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MyIptvPalette.Positive else MyIptvPalette.White
    Surface(
        modifier = Modifier.tvFocusBorder().clickable(onClick = onClick),
        color = MyIptvPalette.Card,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(if (selected) 2.dp else 1.dp, color),
    ) {
        Text(text, color = color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun CinemaAssociationDialog(
    program: CinemaProgram, indexedChannels: List<IndexedCinemaChannel>?, initial: List<SavedChannel>,
    onDismiss: () -> Unit, onSave: (Set<Int>) -> Unit,
) {
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    var query by remember(program.channelKey) { mutableStateOf(program.channelName) }
    var selected by remember(program.channelKey) { mutableStateOf(initial.map { it.streamId }.toSet()) }
    var candidates by remember(program.channelKey) { mutableStateOf(initial) }
    var searching by remember(program.channelKey) { mutableStateOf(true) }
    LaunchedEffect(query, indexedChannels, program.country) {
        val index = indexedChannels
        if (index == null) {
            searching = true
            return@LaunchedEffect
        }
        searching = true
        delay(180)
        val normalizedQuery = withContext(Dispatchers.Default) { cinemaName(query) }
        val words = normalizedQuery.split(' ').filter(String::isNotBlank)
        candidates = if (words.isEmpty()) {
            initial
        } else {
            withContext(Dispatchers.Default) {
                val buckets = List(4) { mutableListOf<SavedChannel>() }
                for (entry in index) {
                    if (!words.all { word -> entry.normalizedName.contains(word) }) continue
                    val sameCountry = entry.channel.countryCode.equals(program.country, ignoreCase = true)
                    val exact = entry.normalizedName == normalizedQuery
                    val bucket = when {
                        sameCountry && exact -> 0
                        exact -> 1
                        sameCountry -> 2
                        else -> 3
                    }
                    if (buckets[bucket].size < MAX_ASSOCIATION_RESULTS) buckets[bucket] += entry.channel
                }
                buckets.flatten().take(MAX_ASSOCIATION_RESULTS)
            }
        }
        searching = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MyIptvPalette.Background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (portrait) 96.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Associer ${program.channelName} (${program.country})",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    MenuButton("Enregistrer", onClick = { onSave(selected) })
                }
                Text("Cochez toutes les variantes utiles. Le pays, les numéros, +1 et East/West doivent correspondre.")
                TvTextField(query, { query = it }, label = { Text("Rechercher dans Strong IPTV") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuButton("Nom EPG", onClick = { query = program.channelName })
                    MenuButton("Cocher les résultats", enabled = candidates.isNotEmpty() && !searching, onClick = { selected = selected + candidates.map { it.streamId } })
                    MenuButton("Vider", onClick = { selected = emptySet() })
                }
                Text(
                    when {
                        indexedChannels == null -> "Indexation des chaînes Strong IPTV…"
                        searching -> "Recherche…"
                        else -> "${selected.size} sélectionnée(s) · ${candidates.size} résultat(s) affiché(s)"
                    },
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = if (portrait) 48.dp else 16.dp),
                ) {
                    items(candidates, key = { "${it.profileId}:${it.streamId}" }) { c ->
                        MenuButton(
                            "${if (c.streamId in selected) "☑" else "☐"} ${c.name} · ${c.countryCode} · #${c.streamId}",
                            active = c.streamId in selected,
                            onClick = { selected = if (c.streamId in selected) selected - c.streamId else selected + c.streamId },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), maxLines = 3,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuButton("Enregistrer", onClick = { onSave(selected) })
                    MenuButton("Annuler", onClick = onDismiss)
                }
            }
        }
    }
}

private data class IndexedCinemaChannel(
    val channel: SavedChannel,
    val normalizedName: String,
)

private const val MAX_ASSOCIATION_RESULTS = 200
private const val CINEMA_FILTER_PREFERENCES = "cinema_filters"
private const val SPORTS_FILTER_PREFERENCES = "sports_filters"
private const val CINEMA_FILTER_COUNTRIES = "selected_countries"
private const val CINEMA_FILTER_PERIOD = "selected_period"
