package com.kmz.shazamplayer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.compose.AsyncImage
import com.kmz.shazamplayer.model.Track
import com.kmz.shazamplayer.network.SoundCloudManager
import com.kmz.shazamplayer.network.SoundCloudResult
import com.kmz.shazamplayer.util.CsvParser
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Navigation Levels
enum class NavLevel {
    HOME,
    PLAYLIST,
    PLAYER
}

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val clientId = "vIiNGKzDDokJvMAQTU0hxe3QGK3OklKu"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).setHandleAudioBecomingNoisy(true).build()

        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()

        setContent { ShazamPlayerTheme { MainScreen(exoPlayer!!, clientId) { shutdown() } } }
    }

    private fun shutdown() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        mediaSession?.release()
        exoPlayer = null
        mediaSession = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(player: ExoPlayer, clientId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("ShazamPrefs", ComponentActivity.MODE_PRIVATE)
    }

    // Navigation State
    var currentLevel by remember { mutableStateOf(NavLevel.HOME) }

    // Data State
    var allTracks by remember { mutableStateOf(emptyList<Track>()) }
    var filteredTracks by remember { mutableStateOf(emptyList<Track>()) }

    // Player State
    var currentTrackIndexInFiltered by remember { mutableStateOf(-1) }
    var alternateStreams by remember { mutableStateOf(emptyList<SoundCloudResult>()) }
    var currentStreamIndex by remember { mutableIntStateOf(0) }
    var isActuallyPlaying by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }

    // Filter State
    var selectedYear by remember { mutableStateOf("Toutes") }
    var selectedMonth by remember { mutableStateOf("Tous") }
    var artistFilter by remember { mutableStateOf("") }
    var titleFilter by remember { mutableStateOf("") }

    // Progress State
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Sleep Timer State
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerRemainingSeconds by remember { mutableIntStateOf(0) }

    val soundCloudManager = remember { SoundCloudManager(clientId) }
    val currentTrack =
            if (currentTrackIndexInFiltered in filteredTracks.indices)
                    filteredTracks[currentTrackIndexInFiltered]
            else null

    // Load persisted CSV on startup
    LaunchedEffect(Unit) {
        val savedCsv = prefs.getString("csv_data", null)
        if (savedCsv != null) {
            allTracks = CsvParser.parse(savedCsv.byteInputStream())
            filteredTracks = allTracks
        }
    }

    // Navigation back handling
    BackHandler {
        when (currentLevel) {
            NavLevel.PLAYER -> currentLevel = NavLevel.PLAYLIST
            NavLevel.PLAYLIST -> currentLevel = NavLevel.HOME
            NavLevel.HOME -> {
                /* Do nothing, default behavior */
            }
        }
    }

    // Progress update loop
    LaunchedEffect(isActuallyPlaying) {
        while (isActuallyPlaying) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(1000)
        }
    }

    // Sleep Timer Logic
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            sleepTimerRemainingSeconds = sleepTimerMinutes * 60
            while (sleepTimerRemainingSeconds > 0) {
                delay(1000)
                sleepTimerRemainingSeconds--
            }
            // Shutting down when timer ends
            onExit()
        }
    }

    fun applyFilters() {
        val monthMap =
                mapOf(
                        "Janvier" to "01",
                        "Février" to "02",
                        "Mars" to "03",
                        "Avril" to "04",
                        "Mai" to "05",
                        "Juin" to "06",
                        "Juillet" to "07",
                        "Août" to "08",
                        "Septembre" to "09",
                        "Octobre" to "10",
                        "Novembre" to "11",
                        "Décembre" to "12"
                )
        val monthSearch = monthMap[selectedMonth] ?: ""

        filteredTracks =
                allTracks.filter { track ->
                    val matchesYear =
                            selectedYear == "Toutes" || track.tagTime.contains(selectedYear)
                    val matchesMonth =
                            selectedMonth == "Tous" || track.tagTime.contains("-$monthSearch-")

                    val matchesArtist =
                            artistFilter.isEmpty() ||
                                    track.artist.contains(artistFilter, ignoreCase = true)
                    val matchesTitle =
                            titleFilter.isEmpty() ||
                                    track.title.contains(titleFilter, ignoreCase = true)

                    matchesYear && matchesMonth && matchesArtist && matchesTitle
                }
        currentLevel = NavLevel.PLAYLIST
    }

    fun playTrack(index: Int, streamIdx: Int = 0) {
        if (filteredTracks.isEmpty() || index !in filteredTracks.indices) return

        currentTrackIndexInFiltered = index
        val track = filteredTracks[index]

        scope.launch {
            // SoundCloudManager interroge automatiquement Deezer/iTunes pour validation
            val results = soundCloudManager.searchTracks(track.artist, track.title)

            if (results.isNotEmpty()) {
                alternateStreams = results
                currentStreamIndex = streamIdx % results.size
                val selected = results[currentStreamIndex]

                // Récupérer les métadonnées officielles pour enrichir le Track
                val officialMeta = soundCloudManager.getOfficialMetadata(track.artist, track.title)
                if (officialMeta != null) {
                    track.officialDurationMs = officialMeta.durationMs
                    track.officialAlbum = officialMeta.album
                    track.officialCoverHD = officialMeta.coverUrlHD
                    track.metadataSource = officialMeta.source
                }

                track.streamUrl = selected.streamUrl
                // Priorité: Cover officielle HD > Cover SoundCloud
                track.artworkUrl = officialMeta?.coverUrlHD ?: selected.artworkUrl

                val mediaMetadata =
                        MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                                .build()

                val mediaItem =
                        MediaItem.Builder()
                                .setUri(Uri.parse(selected.streamUrl))
                                .setMediaMetadata(mediaMetadata)
                                .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            } else {
                Toast.makeText(context, "Non trouvé: ${track.title}", Toast.LENGTH_SHORT).show()
                if (!isRepeat) playTrack(index + 1)
            }
        }
    }

    fun playNext() {
        if (isRepeat) {
            player.seekTo(0)
            player.play()
        } else if (isShuffle && filteredTracks.isNotEmpty()) {
            playTrack(Random.nextInt(filteredTracks.size))
        } else {
            val next = currentTrackIndexInFiltered + 1
            if (next < filteredTracks.size) playTrack(next) else playTrack(0)
        }
    }

    DisposableEffect(player) {
        val listener =
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isActuallyPlaying = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) playNext()
                    }
                }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val filePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            val content = inputStream.bufferedReader().use { it.readText() }
                            prefs.edit().putString("csv_data", content).apply()
                            allTracks = CsvParser.parse(content.byteInputStream())
                            filteredTracks = allTracks
                            Toast.makeText(
                                            context,
                                            "${allTracks.size} morceaux chargés !",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG)
                                .show()
                    }
                }
            }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- LEVEL ARCHITECTURE ---
        when (currentLevel) {
            NavLevel.HOME -> {
                HomeScreen(
                        onLoadCsv = { filePickerLauncher.launch("*/*") },
                        onExit = onExit,
                        years = listOf("Toutes") + (2019..2026).map { it.toString() },
                        months =
                                listOf(
                                        "Tous",
                                        "Janvier",
                                        "Février",
                                        "Mars",
                                        "Avril",
                                        "Mai",
                                        "Juin",
                                        "Juillet",
                                        "Août",
                                        "Septembre",
                                        "Octobre",
                                        "Novembre",
                                        "Décembre"
                                ),
                        selectedYear = selectedYear,
                        selectedMonth = selectedMonth,
                        artistFilter = artistFilter,
                        titleFilter = titleFilter,
                        sleepTimerMinutes = sleepTimerMinutes,
                        onYearChange = { selectedYear = it },
                        onMonthChange = { selectedMonth = it },
                        onArtistChange = { artistFilter = it },
                        onTitleChange = { titleFilter = it },
                        onApply = { applyFilters() },
                        onSetSleepTimer = { sleepTimerMinutes = it }
                )
            }
            NavLevel.PLAYLIST -> {
                PlaylistScreen(
                        tracks = filteredTracks,
                        selectedIndex = currentTrackIndexInFiltered,
                        onTrackClick = { idx -> playTrack(idx) },
                        onBack = { currentLevel = NavLevel.HOME }
                )
            }
            NavLevel.PLAYER -> {
                if (currentTrack != null) {
                    FullScreenPlayer(
                            track = currentTrack,
                            isPlaying = isActuallyPlaying,
                            isShuffle = isShuffle,
                            isRepeat = isRepeat,
                            currentPosition = currentPosition,
                            duration = duration,
                            onClose = { currentLevel = NavLevel.PLAYLIST },
                            onTogglePlay = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            onPrevious = { playTrack(currentTrackIndexInFiltered - 1) },
                            onNext = { playNext() },
                            onShuffleToggle = { isShuffle = !isShuffle },
                            onRepeatToggle = { isRepeat = !isRepeat },
                            onCycleStream = {
                                if (alternateStreams.size > 1) {
                                    val nextIdx = (currentStreamIndex + 1) % alternateStreams.size
                                    playTrack(currentTrackIndexInFiltered, nextIdx)
                                }
                            },
                            onSeek = { player.seekTo(it) }
                    )
                }
            }
        }

        // Mini player (visible only in Playlist level if a track is playing)
        if (currentTrack != null && currentLevel == NavLevel.PLAYLIST) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                PlayerBottomBar(
                        currentTrack,
                        isActuallyPlaying,
                        onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                        onClick = { currentLevel = NavLevel.PLAYER }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        onLoadCsv: () -> Unit,
        onExit: () -> Unit,
        years: List<String>,
        months: List<String>,
        selectedYear: String,
        selectedMonth: String,
        artistFilter: String,
        titleFilter: String,
        sleepTimerMinutes: Int,
        onYearChange: (String) -> Unit,
        onMonthChange: (String) -> Unit,
        onArtistChange: (String) -> Unit,
        onTitleChange: (String) -> Unit,
        onApply: () -> Unit,
        onSetSleepTimer: (Int) -> Unit
) {
    val context = LocalContext.current
    var showTimerDialog by remember { mutableStateOf(false) }

    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    "Shazam Player",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showTimerDialog = true }) {
                    Icon(
                            if (sleepTimerMinutes > 0) Icons.Default.Timer
                            else Icons.Outlined.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerMinutes > 0) Color(0xFF00FF88) else Color.White
                    )
                }

                IconButton(onClick = onExit) {
                    Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Shutdown",
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        if (showTimerDialog) {
            AlertDialog(
                    onDismissRequest = { showTimerDialog = false },
                    title = { Text("Sommeil (minutes)") },
                    text = {
                        Column {
                            listOf(0, 10, 20, 30, 40).forEach { mins ->
                                TextButton(
                                        onClick = {
                                            onSetSleepTimer(mins)
                                            showTimerDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                            if (mins == 0) "Désactiver" else "$mins min",
                                            color = Color.White
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTimerDialog = false }) { Text("Fermer") }
                    },
                    containerColor = Color(0xFF222222),
                    titleContentColor = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
                onClick = onLoadCsv,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF))
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Charger Library CSV")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Filtres", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

        Spacer(modifier = Modifier.height(16.dp))

        // Year Dropdown
        FilterDropdown("Année", selectedYear, years, onYearChange)

        Spacer(modifier = Modifier.height(16.dp))

        // Month Dropdown
        FilterDropdown("Mois", selectedMonth, months, onMonthChange)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
                value = artistFilter,
                onValueChange = onArtistChange,
                label = { Text("Artiste") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                        )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
                value = titleFilter,
                onValueChange = onTitleChange,
                label = { Text("Titre") },
                modifier = Modifier.fillMaxWidth(),
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                        )
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
        ) { Text("Afficher la Playlist", fontSize = 18.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
        label: String,
        selected: String,
        options: List<String>,
        onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White
                        )
                    }
                },
                colors =
                        OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                        )
        )
        DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF222222)).fillMaxWidth(0.8f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                )
            }
        }
    }
}

@Composable
fun PlaylistScreen(
        tracks: List<Track>,
        selectedIndex: Int,
        onTrackClick: (Int) -> Unit,
        onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(
                    "Ma Playlist",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
            )
        }

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun morceau ne correspond aux filtres", color = Color.Gray)
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    TrackRow(track, isSelected = index == selectedIndex) { onTrackClick(index) }
                }
            }
        }
    }
}

@Composable
fun FullScreenPlayer(
        track: Track,
        isPlaying: Boolean,
        isShuffle: Boolean,
        isRepeat: Boolean,
        currentPosition: Long,
        duration: Long,
        onClose: () -> Unit,
        onTogglePlay: () -> Unit,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        onShuffleToggle: () -> Unit,
        onRepeatToggle: () -> Unit,
        onCycleStream: () -> Unit,
        onSeek: (Long) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Pochette (50% de la hauteur, 100% largeur, coins droits)
        Box(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.5f)) {
            AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
            )
            IconButton(
                    onClick = onClose,
                    modifier =
                            Modifier.padding(16.dp)
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Text(
                            text = track.artist,
                            color = Color.Gray,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // Bouton Permutation Flux (HQ)
                IconButton(onClick = onCycleStream) {
                    Icon(
                            Icons.Default.HighQuality,
                            contentDescription = "Alternative Stream",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Barre de progression
            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            Slider(
                    value = progress,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    colors =
                            SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
            )
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), color = Color.Gray, fontSize = 11.sp)
                Text(formatTime(duration), color = Color.Gray, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Contrôles
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (isShuffle) Color(0xFF0088FF) else Color.Gray,
                            modifier = Modifier.size(26.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                        Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                    Surface(
                            onClick = onTogglePlay,
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(68.dp).padding(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                    imageVector =
                                            if (isPlaying) Icons.Default.Pause
                                            else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        Icon(
                                Icons.Default.SkipNext,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(onClick = onRepeatToggle) {
                    Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (isRepeat) Color(0xFF00FF88) else Color.Gray,
                            modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%02d:%02d".format(min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(track: Track, isSelected: Boolean, onClick: () -> Unit) {
    Card(
            onClick = onClick,
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isSelected) Color(0xFF0088FF).copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.05f)
                    ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            border = if (isSelected) BorderStroke(1.dp, Color(0xFF0088FF)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                    modifier =
                            Modifier.size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF0088FF)
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                        track.title,
                        color = if (isSelected) Color(0xFF0088FF) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                Text(track.tagTime, color = Color.DarkGray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PlayerBottomBar(
        track: Track,
        isPlaying: Boolean,
        onTogglePlay: () -> Unit,
        onClick: () -> Unit
) {
    Surface(
            color = Color(0xFF121212),
            modifier = Modifier.fillMaxWidth().height(70.dp).clickable { onClick() }
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier =
                            Modifier.size(50.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        track.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                )
                Text(track.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                        imageVector =
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun ShazamPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme =
                    darkColorScheme(
                            primary = Color(0xFF0088FF),
                            background = Color.Black,
                            surface = Color(0xFF121212)
                    ),
            content = content
    )
}
