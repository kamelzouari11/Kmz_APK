package com.example.myiptv.ui

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.myiptv.data.MyIptvDatabase
import com.example.myiptv.data.MyIptvRepository
import com.example.myiptv.data.VodMovie
import com.example.myiptv.data.VodCategoryRow
import com.example.myiptv.data.EpgArtworkRepository
import com.example.myiptv.data.artworkQuery
import com.example.myiptv.ui.theme.MyIptvPalette
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun filmTitleKey(value: String): String = Normalizer.normalize(
    artworkQuery(value).replace(Regex("\\b(?:19|20)\\d{2}\\b"), " "),
    Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.length > 1 }
    .filterNot { it in setOf("the", "a", "an", "le", "la", "les", "un", "une", "des", "du", "de", "of") }
    .sorted()
    .joinToString(" ")

private const val MAX_ACTOR_RESULTS = 40

private class TvFilmPlayerView(context: Context) : PlayerView(context) {
    var onDpadEvent: ((Int, Int) -> Unit)? = null

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            post {
                requestFocus()
                showController()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dpadKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> false
        }
        if (dpadKey) {
            onDpadEvent?.invoke(event.keyCode, event.action)
            // The Compose overlay owns the TV controls. Do not let PlayerView
            // consume DPAD events and move focus into its hidden controller.
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilmsScreen(
    profile: com.example.myiptv.data.XtreamProfile?,
    player: ExoPlayer,
    onDismiss: () -> Unit,
    onPlayerModeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember { MyIptvRepository(MyIptvDatabase.get(context)) }
    var movies by remember { mutableStateOf(emptyList<VodMovie>()) }
    var categories by remember { mutableStateOf(emptyList<VodCategoryRow>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedMovie by remember { mutableStateOf<VodMovie?>(null) }
    var selectedCategoryId by remember { mutableStateOf("") }
    var filmQueryInput by remember { mutableStateOf("") }
    var appliedFilmQuery by remember { mutableStateOf("") }
    var actorQueryInput by remember { mutableStateOf("") }
    var appliedActorQuery by remember { mutableStateOf("") }
    var actorSearchStatus by remember { mutableStateOf<String?>(null) }
    var showRecentFilms by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showActorIndexer by remember { mutableStateOf(false) }
    val indexCategoryPreferences = remember(profile?.id) {
        context.getSharedPreferences("film_actor_index_${profile?.id ?: 0}", Context.MODE_PRIVATE)
    }
    var selectedIndexCategories by remember(profile?.id) {
        mutableStateOf(indexCategoryPreferences.getStringSet("category_ids", emptySet()).orEmpty().toSet())
    }
    var indexStatus by remember { mutableStateOf<String?>(null) }
    val screenScope = rememberCoroutineScope()
    val saveIndexCategorySelection: (Set<String>) -> Unit = { selection ->
        selectedIndexCategories = selection
        indexCategoryPreferences.edit().putStringSet("category_ids", selection).apply()
    }
    var pageLoading by remember { mutableStateOf(false) }
    var nextPageOffset by remember { mutableIntStateOf(0) }
    var noMorePages by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val resumePreferences = remember(profile?.id) {
        context.getSharedPreferences("film_resume_${profile?.id ?: 0}", Context.MODE_PRIVATE)
    }
    var resumePositions by remember(profile?.id) {
        mutableStateOf(
            resumePreferences.all.mapNotNull { (key, value) ->
                key.removePrefix("position_").toIntOrNull()?.let { streamId ->
                    (value as? Long)?.let { streamId to it }
                }
            }.toMap(),
        )
    }
    val recentFilmPreferences = remember(profile?.id) {
        context.getSharedPreferences("film_recents_${profile?.id ?: 0}", Context.MODE_PRIVATE)
    }
    var recentMovieIds by remember(profile?.id) {
        mutableStateOf(
            recentFilmPreferences.getString("stream_ids", "")
                .orEmpty()
                .split(",")
                .mapNotNull(String::toIntOrNull),
        )
    }
    val rememberRecentMovie: (VodMovie) -> Unit = { movie ->
        recentMovieIds = (listOf(movie.streamId) + recentMovieIds.filterNot { it == movie.streamId }).take(50)
        recentFilmPreferences.edit().putString("stream_ids", recentMovieIds.joinToString(",")).apply()
    }
    val saveResumePosition: () -> Unit = {
        selectedMovie?.let { movie ->
            val position = player.currentPosition
            if (position > 5_000L) {
                resumePreferences.edit().putLong("position_${movie.streamId}", position).apply()
                resumePositions = resumePositions + (movie.streamId to position)
            }
        }
    }

    BackHandler {
        if (showPlayer) {
            saveResumePosition()
            player.stop()
            player.clearMediaItems()
            showPlayer = false
            onPlayerModeChanged(false)
        } else if (showDetails) {
            // Retour depuis la fiche : rester dans le catalogue Films et
            // conserver la recherche ainsi que la position de la grille.
            showDetails = false
        } else {
            onDismiss()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.clearMediaItems()
            onPlayerModeChanged(false)
        }
    }
    DisposableEffect(showPlayer) {
        onDispose {
            if (showPlayer) saveResumePosition()
        }
    }
    LaunchedEffect(profile?.id, refresh) {
        if (profile == null) {
            loading = false
            error = "Configurez d’abord un profil IPTV."
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { repository.loadVodCategories(profile, forceRefresh = refresh > 0) }
            .onSuccess {
                categories = it
                movies = emptyList()
                selectedMovie = null
            }
            .onFailure { error = "Impossible de charger les films du provider." }
        loading = false
    }

    LaunchedEffect(categories) {
        if (selectedCategoryId !in categories.map { it.categoryId }) {
            selectedCategoryId = categories.firstOrNull()?.categoryId.orEmpty()
        }
    }
    LaunchedEffect(selectedCategoryId, profile?.id, refresh, appliedFilmQuery, appliedActorQuery, showRecentFilms) {
        val activeProfile = profile ?: return@LaunchedEffect
        if (loading || showRecentFilms || appliedFilmQuery.isNotBlank() || appliedActorQuery.isNotBlank()) return@LaunchedEffect
        if (selectedCategoryId.isBlank()) return@LaunchedEffect
        pageLoading = true
        val firstPage = runCatching { repository.loadVodPage(activeProfile, selectedCategoryId, limit = 20, offset = 0) }
            .getOrDefault(emptyList())
        movies = firstPage
        nextPageOffset = firstPage.size
        noMorePages = firstPage.size < 20
        pageLoading = false
    }
    LaunchedEffect(appliedFilmQuery, appliedActorQuery, profile?.id, loading) {
        val activeProfile = profile ?: return@LaunchedEffect
        if (loading || appliedFilmQuery.isBlank() || appliedActorQuery.isNotBlank()) return@LaunchedEffect
        pageLoading = true
        movies = runCatching {
            repository.searchVodMoviesByTitle(activeProfile, appliedFilmQuery.trim())
        }.getOrDefault(emptyList())
        pageLoading = false
    }
    val lastVisibleMovieIndex by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    LaunchedEffect(
        lastVisibleMovieIndex,
        movies.size,
        selectedCategoryId,
        loading,
        appliedFilmQuery,
        appliedActorQuery,
        showRecentFilms,
    ) {
        val activeProfile = profile ?: return@LaunchedEffect
        if (showRecentFilms || appliedFilmQuery.isNotBlank() || appliedActorQuery.isNotBlank()) return@LaunchedEffect
        if (movies.isEmpty() || pageLoading || noMorePages || selectedCategoryId.isBlank()) return@LaunchedEffect
        if (lastVisibleMovieIndex < movies.size - 5) return@LaunchedEffect
        pageLoading = true
        val nextPage = runCatching {
            repository.loadVodPage(activeProfile, selectedCategoryId, limit = 20, offset = nextPageOffset)
        }.getOrDefault(emptyList())
        if (nextPage.isEmpty()) {
            // Certains providers ont un total exactement multiple de 20 et
            // renvoient alors une page vide au lieu d'une page courte.
            noMorePages = true
        } else {
            movies = movies + nextPage
            nextPageOffset += nextPage.size
            noMorePages = nextPage.size < 20
        }
        pageLoading = false
    }
    LaunchedEffect(appliedActorQuery, profile?.id, loading) {
        val activeProfile = profile ?: return@LaunchedEffect
        val terms = appliedActorQuery.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) {
            actorSearchStatus = null
            return@LaunchedEffect
        }
        if (loading || terms.any { it.length < 2 }) return@LaunchedEffect
        // Ne pas laisser visibles les résultats de la recherche précédente
        // pendant la récupération de la nouvelle filmographie.
        movies = emptyList()
        actorSearchStatus = "Index acteurs local…"
        val matches = repository.searchVodMoviesByActor(activeProfile, appliedActorQuery)
            .distinctBy { filmTitleKey(it.name) }
            .take(MAX_ACTOR_RESULTS)
        if (matches.isNotEmpty()) {
            val byId = matches.associate { movie ->
                movie.streamId to movie.copy(
                    actors = listOfNotNull(movie.actors, appliedActorQuery.trim())
                        .distinctBy(String::lowercase)
                        .joinToString(", "),
                    detailsLoaded = true,
                )
            }
            val enrichedMatches = matches.map { byId.getValue(it.streamId) }
            // Affichage immédiat : la sauvegarde ne doit pas bloquer le clavier
            // ni attendre 401 écritures Room successives.
            movies = enrichedMatches
            actorSearchStatus = "${enrichedMatches.size} film(s) trouvé(s) pour « ${appliedActorQuery.trim()} ». Sauvegarde du cache…"
            withContext(Dispatchers.IO) {
                repository.cacheVodMovies(enrichedMatches)
            }
        }
        actorSearchStatus = if (matches.isEmpty()) {
            "Aucun film trouvé dans l’index actuellement disponible. Sélectionnez une catégorie puis indexez-la."
        } else {
            "${matches.size} film(s) trouvé(s) dans l’index local pour « ${appliedActorQuery.trim()} »."
        }
    }
    val visibleMovies = remember(movies, selectedCategoryId, appliedFilmQuery, appliedActorQuery) {
        val filmTerms = appliedFilmQuery.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val actorTerms = appliedActorQuery.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val hasSearch = filmTerms.isNotEmpty() || actorTerms.isNotEmpty()
        val sourceMovies = if (!hasSearch) {
            movies.filter { it.categoryId == selectedCategoryId }
        } else {
            movies
        }
        val filteredMovies = sourceMovies.filter { movie ->
            filmTerms.all { term -> movie.name.contains(term, ignoreCase = true) } &&
                actorTerms.all { term -> movie.actors?.contains(term, ignoreCase = true) == true }
        }
        if (hasSearch) {
            filteredMovies.sortedWith(
                compareBy<VodMovie> { it.categoryOrder }
                    .thenBy { it.providerOrder }
                    .thenBy { it.streamId },
            )
        } else {
            filteredMovies.sortedWith(
                compareBy<VodMovie> { it.providerOrder }.thenBy { it.streamId },
            )
        }
    }
    val recentMovies = remember(movies, recentMovieIds) {
        recentMovieIds.mapNotNull { id -> movies.firstOrNull { it.streamId == id } }
    }
    val displayedMovies = if (showRecentFilms) recentMovies else visibleMovies
    LaunchedEffect(displayedMovies) {
        if (selectedMovie?.streamId !in displayedMovies.map { it.streamId }) selectedMovie = displayedMovies.firstOrNull()
    }
    LaunchedEffect(displayedMovies, refresh) {
        displayedMovies.take(12).forEach { movie ->
            movie.posterUrl?.let {
                context.imageLoader.enqueue(buildPosterRequest(context, it, refresh))
            }
        }
    }

    if (showPlayer && selectedMovie != null) {
        FullScreenMoviePlayer(
            movie = selectedMovie!!,
            profile = profile,
            repository = repository,
            player = player,
            startPosition = resumePositions[selectedMovie!!.streamId] ?: 0L,
        )
        return
    }

    if (showDetails && selectedMovie != null) {
        FilmDetailsScreen(
            movie = selectedMovie!!,
            profile = profile,
            repository = repository,
            refreshToken = refresh,
            onBack = { showDetails = false },
            onPlay = {
                selectedMovie = it
                rememberRecentMovie(it)
                showPlayer = true
                onPlayerModeChanged(true)
            },
            resumeAvailable = selectedMovie!!.streamId in resumePositions,
        )
        return
    }

    if (showActorIndexer) {
        val startActorIndexing = {
            profile?.let { activeProfile ->
                showActorIndexer = false
                screenScope.launch {
                    indexStatus = "Téléchargement du catalogue provider…"
                    runCatching {
                        repository.loadVodMovies(activeProfile)
                        indexStatus = "Indexation des catégories sélectionnées…"
                        repository.buildVodActorIndex(activeProfile, selectedIndexCategories) { done, total ->
                            indexStatus = if (total == 0) {
                                "Aucun film dans les catégories sélectionnées."
                            } else {
                                "Index acteurs : $done / $total films"
                            }
                        }
                    }.onFailure {
                        indexStatus = "Erreur pendant l’indexation."
                    }.onSuccess {
                        indexStatus = "Index acteurs terminé."
                    }
                }
            }
            Unit
        }
        AlertDialog(
            onDismissRequest = { showActorIndexer = false },
            title = { Text("Catégories à indexer") },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { showActorIndexer = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(containerColor = MyIptvPalette.ButtonBackground, disabledContainerColor = MyIptvPalette.ButtonBackground)) { Text("Annuler") }
                        TextButton(
                            enabled = selectedIndexCategories.isNotEmpty() && profile != null,
                            onClick = startActorIndexing,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(containerColor = MyIptvPalette.ButtonBackground, disabledContainerColor = MyIptvPalette.ButtonBackground),
                        ) { Text("Indexer") }
                    }
                    LazyColumn(Modifier.height(420.dp)) {
                        lazyItems(categories, key = { it.categoryId }) { category ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val selection = if (category.categoryId in selectedIndexCategories) {
                                        selectedIndexCategories - category.categoryId
                                    } else {
                                        selectedIndexCategories + category.categoryId
                                    }
                                    saveIndexCategorySelection(selection)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = category.categoryId in selectedIndexCategories,
                                    onCheckedChange = { checked ->
                                        val selection = if (checked) {
                                            selectedIndexCategories + category.categoryId
                                        } else {
                                            selectedIndexCategories - category.categoryId
                                        }
                                        saveIndexCategorySelection(selection)
                                    },
                                )
                                Text(category.categoryName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    Surface(Modifier.fillMaxSize(), color = MyIptvPalette.Background) {
        Column(Modifier.fillMaxSize().focusGroup().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FILMS", style = MaterialTheme.typography.headlineMedium, color = MyIptvPalette.Primary)
                    Text(
                        if (showRecentFilms) "Films récents · ${displayedMovies.size}"
                        else "Catalogue VOD du provider · ${displayedMovies.size} films",
                        color = MyIptvPalette.TextSecondary,
                    )
                }
                MenuButton(
                    text = "Récents (${recentMovies.size})",
                    active = showRecentFilms,
                    onClick = { showRecentFilms = true },
                )
                MenuButton(
                    text = "Effacer récents",
                    enabled = recentMovieIds.isNotEmpty(),
                    onClick = {
                        recentMovieIds = emptyList()
                        recentFilmPreferences.edit().remove("stream_ids").apply()
                        showRecentFilms = false
                    },
                )
                MenuButton("Actualiser", enabled = !loading, onClick = { refresh++ })
                MenuButton(
                    text = "Index acteurs",
                    enabled = !loading && categories.isNotEmpty(),
                    onClick = {
                        saveIndexCategorySelection(
                            selectedIndexCategories.intersect(categories.map { it.categoryId }.toSet()),
                        )
                        showActorIndexer = true
                    },
                )
                Spacer(Modifier.width(8.dp))
                MenuButton("Fermer", onClick = onDismiss)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvTextField(
                    value = filmQueryInput,
                    onValueChange = { filmQueryInput = it },
                    label = { Text("Rechercher un film") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { appliedFilmQuery = filmQueryInput.trim() }),
                )
                TvTextField(
                    value = actorQueryInput,
                    onValueChange = { actorQueryInput = it },
                    label = { Text("Rechercher un acteur") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { appliedActorQuery = actorQueryInput.trim() }),
                )
            }
            actorSearchStatus?.let {
                Text(it, color = MyIptvPalette.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            indexStatus?.let {
                Text(it, color = MyIptvPalette.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            if (loading || (pageLoading && displayedMovies.isEmpty())) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MyIptvPalette.Primary) }
            } else if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MyIptvPalette.Negative) }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        Modifier.fillMaxHeight().weight(1f),
                        color = MyIptvPalette.Surface,
                        border = BorderStroke(1.dp, MyIptvPalette.Border),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lazyItems(categories, key = { it.categoryId }) { category ->
                                MenuButton(
                                    text = category.categoryName,
                                    active = category.categoryId == selectedCategoryId,
                                    onClick = {
                                        selectedCategoryId = category.categoryId
                                        showRecentFilms = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(122.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxHeight().weight(2f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(displayedMovies, key = { "${it.profileId}:${it.streamId}" }) { movie ->
                            FilmPosterCard(movie, refreshToken = refresh, selected = selectedMovie?.streamId == movie.streamId) {
                                selectedMovie = movie
                                showDetails = true
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun posterRequest(url: String?, refreshToken: Int): ImageRequest? {
    val context = LocalContext.current
    return remember(url, refreshToken) {
        url?.let { buildPosterRequest(context, it, refreshToken) }
    }
}

private fun buildPosterRequest(context: android.content.Context, url: String, refreshToken: Int): ImageRequest {
    val cacheKey = "$url|refresh=$refreshToken"
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
}

@Composable
private fun FilmPosterCard(movie: VodMovie, refreshToken: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.tvFocusBorder(shape = shape).clickable(onClick = onClick),
        color = MyIptvPalette.ButtonBackground,
        shape = shape,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MyIptvPalette.Primary else MyIptvPalette.Border),
    ) {
        Column {
            AsyncImage(
                model = posterRequest(movie.posterUrl, refreshToken),
                contentDescription = movie.name,
                modifier = Modifier.fillMaxWidth().height(172.dp).background(MyIptvPalette.CardHover),
                contentScale = ContentScale.Crop,
            )
            Text(
                movie.name,
                modifier = Modifier.padding(8.dp),
                color = MyIptvPalette.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilmDetailsScreen(
    movie: VodMovie,
    profile: com.example.myiptv.data.XtreamProfile?,
    repository: MyIptvRepository,
    refreshToken: Int,
    onBack: () -> Unit,
    onPlay: (VodMovie) -> Unit,
    resumeAvailable: Boolean,
) {
    var detailedMovie by remember(movie.streamId) { mutableStateOf(movie) }
    var internetSummary by remember(movie.streamId) { mutableStateOf<String?>(null) }
    var internetActors by remember(movie.streamId) { mutableStateOf<String?>(null) }
    LaunchedEffect(movie.streamId, profile?.id) {
        val loadedMovie = profile?.let {
            runCatching { repository.loadVodDetails(it, movie) }.getOrDefault(movie)
        } ?: movie
        detailedMovie = loadedMovie
        if (loadedMovie.description.length < 160) {
            internetSummary = EpgArtworkRepository.findMovieSummary(
                loadedMovie.name,
                loadedMovie.releaseDate?.take(4),
                loadedMovie.country,
            )
        }
        if (loadedMovie.actors.isNullOrBlank()) {
            internetActors = EpgArtworkRepository.findMovieActors(loadedMovie.name, loadedMovie.releaseDate?.take(4))
        }
    }
    val synopsis = detailedMovie.description.takeIf { it.length >= 160 }
        ?: internetSummary
        ?: detailedMovie.description.takeIf(String::isNotBlank)

    Surface(Modifier.fillMaxSize(), color = MyIptvPalette.Background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MenuButton("← Catégories", onClick = onBack)
                Text("FICHE DU FILM", modifier = Modifier.weight(1f).padding(start = 14.dp), color = MyIptvPalette.Primary, style = MaterialTheme.typography.titleLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AsyncImage(
                    model = posterRequest(detailedMovie.posterUrl, refreshToken),
                    contentDescription = null,
                    modifier = Modifier.width(300.dp).height(432.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(detailedMovie.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${detailedMovie.categoryName} · ${detailedMovie.country}", color = MyIptvPalette.Primary)
                    synopsis?.let { description ->
                        Text(description, color = MyIptvPalette.TextSecondary)
                    } ?: Text("Description non fournie par le provider.", color = MyIptvPalette.TextSecondary)
                    detailedMovie.genre?.let { Text("Genre : $it", color = MyIptvPalette.TextSecondary) }
                    detailedMovie.director?.let { Text("Réalisation : $it", color = MyIptvPalette.TextSecondary) }
                    (detailedMovie.actors ?: internetActors)?.let { Text("Acteurs : $it", color = MyIptvPalette.TextSecondary) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        detailedMovie.releaseDate?.let { Text(it, color = MyIptvPalette.TextSecondary) }
                        detailedMovie.duration?.let { Text(it, color = MyIptvPalette.TextSecondary) }
                        detailedMovie.rating?.let { Text("★ $it", color = MyIptvPalette.Warning) }
                    }
                    MenuButton(
                        text = if (resumeAvailable) "Reprendre" else "Lire",
                        onClick = { onPlay(detailedMovie) },
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullScreenMoviePlayer(
    movie: VodMovie,
    profile: com.example.myiptv.data.XtreamProfile?,
    repository: MyIptvRepository,
    player: ExoPlayer,
    startPosition: Long,
) {
    val url = profile?.let { repository.vodStreamUrl(it, movie) }
    val playerViewHolder = remember { arrayOfNulls<PlayerView>(1) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var position by remember { mutableStateOf(startPosition) }
    var duration by remember { mutableStateOf(0L) }

    LaunchedEffect(url, startPosition) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (startPosition > 0) player.seekTo(startPosition)
        player.play()

        // prepare()/play() peut réinitialiser la visibilité du contrôleur.
        // On le réaffiche après l'initialisation, puis quelques fois pendant
        // les premières secondes pour couvrir les différents états du player.
        repeat(6) {
            kotlinx.coroutines.delay(250)
            playerViewHolder[0]?.showController()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            kotlinx.coroutines.delay(250)
        }
    }
    LaunchedEffect(controlsInteraction) {
        kotlinx.coroutines.delay(4_000)
        controlsVisible = false
    }
    Box(Modifier.fillMaxSize().background(MyIptvPalette.Background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TvFilmPlayerView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    this.player = player
                    isFocusable = true
                    isFocusableInTouchMode = true
                    playerViewHolder[0] = this
                    post { requestFocus() }
                    onDpadEvent = { keyCode, action ->
                        if (action == KeyEvent.ACTION_DOWN) {
                            controlsVisible = true
                            controlsInteraction++
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                KeyEvent.KEYCODE_DPAD_RIGHT -> player.seekTo(
                                    if (player.duration > 0L) (player.currentPosition + 10_000L).coerceAtMost(player.duration)
                                    else player.currentPosition + 10_000L,
                                )
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> if (player.isPlaying) player.pause() else player.play()
                            }
                        }
                    }
                }
            },
            update = { view ->
                view.useController = false
                view.player = player
                playerViewHolder[0] = view
            },
        )
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MyIptvPalette.Anthracite.copy(alpha = 0.92f))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                LinearProgressIndicator(
                    progress = { if (duration > 0L) position.toFloat() / duration else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (player.isPlaying) "Lecture" else "Pause", color = MyIptvPalette.White)
                    Text("←/→  ±10 s   OK  Lecture/Pause", color = MyIptvPalette.TextSecondary)
                    Text("${formatMovieTime(position)} / ${formatMovieTime(duration)}", color = MyIptvPalette.White)
                }
            }
        }
    }
}

private fun formatMovieTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
