package com.example.simpleiptv

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.AppDatabase
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.ui.theme.SimpleIPTVTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val database = AppDatabase.getDatabase(this)
                val iptvApi = XtreamClient.create("http://j.delta2022.xyz:8880/")
                val iptvRepository = IptvRepository(iptvApi, database.iptvDao())
                setContent { SimpleIPTVTheme(darkTheme = true) { MainScreen(iptvRepository) } }
        }
}

@Composable
fun MainScreen(iptvRepository: IptvRepository) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // UI State
        var profiles by remember { mutableStateOf<List<ProfileEntity>>(emptyList()) }
        var categories by remember { mutableStateOf<List<CategoryEntity>>(emptyList()) }
        var favoriteLists by remember { mutableStateOf<List<FavoriteListEntity>>(emptyList()) }
        var channels by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }

        var activeProfileId by remember { mutableIntStateOf(-1) }
        var selectedCategoryId by remember { mutableStateOf<String?>(null) }
        var selectedFavoriteListId by remember { mutableIntStateOf(-1) }
        var showRecentOnly by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        var playingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
        var isFullScreenPlayer by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var isSearchVisibleOnMobile by remember { mutableStateOf(false) }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Dialog States
        var showProfileManager by remember { mutableStateOf(false) }
        var showProfileAddDialog by remember { mutableStateOf(false) }
        var showProfileEditDialog by remember { mutableStateOf<ProfileEntity?>(null) }
        var showAddListDialog by remember { mutableStateOf(false) }
        var channelToFavorite by remember { mutableStateOf<ChannelEntity?>(null) }
        var showRestoreConfirmDialog by remember { mutableStateOf(false) }
        var backupJsonToRestore by remember { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        var isSearchFocused by remember { mutableStateOf(false) }

        // SAF Launchers for File Access
        val createDocumentLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                        uri?.let {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                                val json = iptvRepository.exportDatabaseToJson()
                                                val outputStream =
                                                        context.contentResolver.openOutputStream(it)
                                                if (outputStream != null) {
                                                        outputStream.use { stream ->
                                                                java.io.OutputStreamWriter(stream)
                                                                        .use { writer ->
                                                                                writer.write(json)
                                                                        }
                                                        }
                                                        kotlinx.coroutines.withContext(
                                                                kotlinx.coroutines.Dispatchers.Main
                                                        ) {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Backup saved successfully",
                                                                                Toast.LENGTH_LONG
                                                                        )
                                                                        .show()
                                                        }
                                                } else {
                                                        throw Exception(
                                                                "Unable to open file for writing"
                                                        )
                                                }
                                        } catch (e: Throwable) {
                                                e.printStackTrace()
                                                kotlinx.coroutines.withContext(
                                                        kotlinx.coroutines.Dispatchers.Main
                                                ) {
                                                        Toast.makeText(
                                                                        context,
                                                                        "Export Error: ${e.message}",
                                                                        Toast.LENGTH_LONG
                                                                )
                                                                .show()
                                                }
                                        }
                                }
                        }
                }

        val openDocumentLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                ) { uri ->
                        uri?.let {
                                try {
                                        context.contentResolver.openInputStream(it)?.use {
                                                inputStream ->
                                                backupJsonToRestore =
                                                        inputStream.bufferedReader().use { reader ->
                                                                reader.readText()
                                                        }
                                                showRestoreConfirmDialog = true
                                        }
                                } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        "Read Error: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                }
                        }
                }

        // Media Player
        var exoPlayer by remember { mutableStateOf<Player?>(null) }
        val controllerFuture = remember {
                MediaController.Builder(
                                context,
                                SessionToken(
                                        context,
                                        ComponentName(context, PlaybackService::class.java)
                                )
                        )
                        .buildAsync()
        }

        LaunchedEffect(controllerFuture) {
                controllerFuture.addListener(
                        {
                                try {
                                        exoPlayer = controllerFuture.get()
                                } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        "Erreur Player: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                }
                        },
                        MoreExecutors.directExecutor()
                )
        }

        DisposableEffect(Unit) { onDispose { MediaController.releaseFuture(controllerFuture) } }

        // Data Observers
        LaunchedEffect(iptvRepository) { iptvRepository.allProfiles.collect { profiles = it } }

        LaunchedEffect(activeProfileId) {
                if (activeProfileId != -1) {
                        launch {
                                iptvRepository.getCategories(activeProfileId).collect {
                                        categories = it
                                }
                        }
                        launch {
                                iptvRepository.getFavoriteLists(activeProfileId).collect {
                                        favoriteLists = it
                                }
                        }
                }
        }

        // Channel loading based on filters
        LaunchedEffect(
                activeProfileId,
                selectedCategoryId,
                selectedFavoriteListId,
                showRecentOnly,
                searchQuery
        ) {
                if (activeProfileId != -1) {
                        val flow =
                                when {
                                        searchQuery.isNotBlank() ->
                                                iptvRepository.searchChannels(
                                                        searchQuery,
                                                        activeProfileId
                                                )
                                        showRecentOnly ->
                                                iptvRepository.getRecentChannels(activeProfileId)
                                        selectedFavoriteListId != -1 ->
                                                iptvRepository.getChannelsByFavoriteList(
                                                        selectedFavoriteListId,
                                                        activeProfileId
                                                )
                                        selectedCategoryId != null ->
                                                iptvRepository.getChannelsByCategory(
                                                        selectedCategoryId!!,
                                                        activeProfileId
                                                )
                                        else -> iptvRepository.getRecentChannels(activeProfileId)
                                }
                        flow.collect { channels = it }
                }
        }

        // Profile Initialization
        LaunchedEffect(profiles) {
                if (activeProfileId == -1 && profiles.isNotEmpty()) {
                        profiles.find { it.isSelected }?.let { activeProfileId = it.id }
                        if (activeProfileId == -1) activeProfileId = profiles.first().id
                }
        }

        // Back button handling
        BackHandler(
                enabled =
                        isFullScreenPlayer ||
                                showProfileManager ||
                                selectedCategoryId != null ||
                                selectedFavoriteListId != -1 ||
                                showRecentOnly
        ) {
                when {
                        isFullScreenPlayer -> isFullScreenPlayer = false
                        showProfileManager -> showProfileManager = false
                        selectedCategoryId != null -> selectedCategoryId = null
                        selectedFavoriteListId != -1 -> selectedFavoriteListId = -1
                        showRecentOnly -> showRecentOnly = false
                }
        }

        // Helper for Channel Click
        val onChannelClick: (ChannelEntity) -> Unit = { channel ->
                playingChannel = channel
                isFullScreenPlayer = true
                exoPlayer?.let { player ->
                        val profile = profiles.find { p -> p.id == activeProfileId }
                        if (profile != null) {
                                scope.launch {
                                        try {
                                                val streamUrl =
                                                        iptvRepository.getStreamUrl(
                                                                profile,
                                                                channel.stream_id
                                                        )
                                                if (streamUrl.isNotEmpty()) {
                                                        val meta =
                                                                MediaMetadata.Builder()
                                                                        .setTitle(channel.name)
                                                                        .setArtworkUri(
                                                                                channel.stream_icon
                                                                                        ?.toUri()
                                                                        )
                                                                        .build()
                                                        val mediaItem =
                                                                MediaItem.Builder()
                                                                        .setUri(streamUrl)
                                                                        .setMediaMetadata(meta)
                                                                        .build()
                                                        player.clearMediaItems()
                                                        player.setMediaItem(mediaItem)
                                                        player.prepare()
                                                        player.play()
                                                } else {
                                                        Toast.makeText(
                                                                        context,
                                                                        "Impossible de récupérer le lien",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        } catch (e: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                "Erreur lecture: ${e.message}",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                        }
                                }
                        }
                }
                scope.launch { iptvRepository.addToRecents(channel.stream_id, activeProfileId) }
        }

        // Main Layout
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (isFullScreenPlayer && playingChannel != null && exoPlayer != null) {
                        VideoPlayerView(
                                exoPlayer = exoPlayer!!,
                                channelName = playingChannel!!.name,
                                currentChannels = channels,
                                categories = categories,
                                onChannelSelected = { onChannelClick(it) },
                                onCategorySelected = {
                                        selectedCategoryId = it.category_id
                                        selectedFavoriteListId = -1
                                        showRecentOnly = false
                                        searchQuery = ""
                                },
                                onBack = { isFullScreenPlayer = false }
                        )
                } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                                // Header
                                Card(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                )
                                ) {
                                        if (isLandscape) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(12.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Text(
                                                                "SimpleIPTV",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .headlineSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )

                                                        Spacer(Modifier.weight(1f))

                                                        TvInput(
                                                                value = searchQuery,
                                                                onValueChange = { query ->
                                                                        searchQuery = query
                                                                },
                                                                label = "Rechercher...",
                                                                focusManager = focusManager,
                                                                leadingIcon = Icons.Default.Search,
                                                                modifier =
                                                                        Modifier.width(
                                                                                        250.dp
                                                                                ) // Reduced width
                                                                                .onFocusChanged {
                                                                                        isSearchFocused =
                                                                                                it.isFocused
                                                                                }
                                                        )

                                                        if (searchQuery.isNotBlank()) {
                                                                var isClearFocused by remember {
                                                                        mutableStateOf(false)
                                                                }
                                                                IconButton(
                                                                        onClick = {
                                                                                searchQuery = ""
                                                                                focusManager
                                                                                        .clearFocus()
                                                                        },
                                                                        modifier =
                                                                                Modifier
                                                                                        .onFocusChanged {
                                                                                                isClearFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .scale(
                                                                                                if (isClearFocused
                                                                                                )
                                                                                                        1.1f
                                                                                                else
                                                                                                        1f
                                                                                        )
                                                                                        .background(
                                                                                                if (isClearFocused
                                                                                                )
                                                                                                        Color.White
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.2f
                                                                                                                )
                                                                                                else
                                                                                                        Color.Transparent,
                                                                                                androidx.compose
                                                                                                        .foundation
                                                                                                        .shape
                                                                                                        .CircleShape
                                                                                        )
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default.Close,
                                                                                contentDescription =
                                                                                        "Effacer",
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .error
                                                                        )
                                                                }
                                                        }

                                                        if (playingChannel != null) {
                                                                HeaderIconButton(
                                                                        Icons.Default.PlayArrow,
                                                                        "Retour Player",
                                                                        {
                                                                                isFullScreenPlayer =
                                                                                        true
                                                                        },
                                                                        tintNormal = Color.Green
                                                                )
                                                        }

                                                        HeaderIconButton(
                                                                Icons.Default.Person,
                                                                "Profils",
                                                                { showProfileManager = true }
                                                        )
                                                        HeaderIconButton(
                                                                Icons.Default.Refresh,
                                                                "Actualiser",
                                                                {
                                                                        scope.launch {
                                                                                isLoading = true
                                                                                val profile =
                                                                                        profiles
                                                                                                .find {
                                                                                                        it.id ==
                                                                                                                activeProfileId
                                                                                                }
                                                                                if (profile != null
                                                                                ) {
                                                                                        try {
                                                                                                iptvRepository
                                                                                                        .refreshDatabase(
                                                                                                                profile
                                                                                                        )
                                                                                        } catch (
                                                                                                e:
                                                                                                        Exception) {
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "Erreur: ${e.message}",
                                                                                                                Toast.LENGTH_SHORT
                                                                                                        )
                                                                                                        .show()
                                                                                        }
                                                                                }
                                                                                isLoading = false
                                                                        }
                                                                }
                                                        )
                                                        HeaderIconButton(
                                                                Icons.Default.CloudUpload,
                                                                "Sauvegarder",
                                                                {
                                                                        try {
                                                                                createDocumentLauncher
                                                                                        .launch(
                                                                                                "simple_iptv_backup.json"
                                                                                        )
                                                                        } catch (e: Exception) {
                                                                                if (android.os.Build
                                                                                                .VERSION
                                                                                                .SDK_INT >=
                                                                                                29
                                                                                ) {
                                                                                        scope
                                                                                                .launch(
                                                                                                        kotlinx.coroutines
                                                                                                                .Dispatchers
                                                                                                                .IO
                                                                                                ) {
                                                                                                        try {
                                                                                                                val json =
                                                                                                                        iptvRepository
                                                                                                                                .exportDatabaseToJson()
                                                                                                                val values =
                                                                                                                        android.content
                                                                                                                                .ContentValues()
                                                                                                                                .apply {
                                                                                                                                        put(
                                                                                                                                                android.provider
                                                                                                                                                        .MediaStore
                                                                                                                                                        .MediaColumns
                                                                                                                                                        .DISPLAY_NAME,
                                                                                                                                                "simple_iptv_backup.json"
                                                                                                                                        )
                                                                                                                                        put(
                                                                                                                                                android.provider
                                                                                                                                                        .MediaStore
                                                                                                                                                        .MediaColumns
                                                                                                                                                        .MIME_TYPE,
                                                                                                                                                "application/json"
                                                                                                                                        )
                                                                                                                                        put(
                                                                                                                                                android.provider
                                                                                                                                                        .MediaStore
                                                                                                                                                        .MediaColumns
                                                                                                                                                        .RELATIVE_PATH,
                                                                                                                                                android.os
                                                                                                                                                        .Environment
                                                                                                                                                        .DIRECTORY_DOWNLOADS
                                                                                                                                        )
                                                                                                                                }
                                                                                                                val resolver =
                                                                                                                        context.contentResolver
                                                                                                                val uri =
                                                                                                                        resolver.insert(
                                                                                                                                android.provider
                                                                                                                                        .MediaStore
                                                                                                                                        .Downloads
                                                                                                                                        .EXTERNAL_CONTENT_URI,
                                                                                                                                values
                                                                                                                        )
                                                                                                                if (uri !=
                                                                                                                                null
                                                                                                                ) {
                                                                                                                        resolver.openOutputStream(
                                                                                                                                        uri
                                                                                                                                )
                                                                                                                                ?.use {
                                                                                                                                        outputStream
                                                                                                                                        ->
                                                                                                                                        outputStream
                                                                                                                                                .write(
                                                                                                                                                        json.toByteArray()
                                                                                                                                                )
                                                                                                                                }
                                                                                                                        kotlinx.coroutines
                                                                                                                                .withContext(
                                                                                                                                        kotlinx.coroutines
                                                                                                                                                .Dispatchers
                                                                                                                                                .Main
                                                                                                                                ) {
                                                                                                                                        Toast.makeText(
                                                                                                                                                        context,
                                                                                                                                                        "Sauvegardé: Downloads/simple_iptv_backup.json",
                                                                                                                                                        Toast.LENGTH_LONG
                                                                                                                                                )
                                                                                                                                                .show()
                                                                                                                                }
                                                                                                                } else {
                                                                                                                        throw Exception(
                                                                                                                                "MediaStore a retourné null"
                                                                                                                        )
                                                                                                                }
                                                                                                        } catch (
                                                                                                                e2:
                                                                                                                        Exception) {
                                                                                                                e2.printStackTrace()
                                                                                                                kotlinx.coroutines
                                                                                                                        .withContext(
                                                                                                                                kotlinx.coroutines
                                                                                                                                        .Dispatchers
                                                                                                                                        .Main
                                                                                                                        ) {
                                                                                                                                Toast.makeText(
                                                                                                                                                context,
                                                                                                                                                "Erreur MediaStore: ${e2.message}",
                                                                                                                                                Toast.LENGTH_LONG
                                                                                                                                        )
                                                                                                                                        .show()
                                                                                                                        }
                                                                                                        }
                                                                                                }
                                                                                } else {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Erreur: Activez 'Stockage' dans les paramètres",
                                                                                                        Toast.LENGTH_LONG
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        }
                                                                }
                                                        )
                                                        HeaderIconButton(
                                                                Icons.Default.CloudDownload,
                                                                "Restaurer",
                                                                {
                                                                        try {
                                                                                openDocumentLauncher
                                                                                        .launch(
                                                                                                "application/json"
                                                                                        )
                                                                        } catch (e: Exception) {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "Erreur: Gestionnaire de fichiers introuvable",
                                                                                                Toast.LENGTH_LONG
                                                                                        )
                                                                                        .show()
                                                                        }
                                                                }
                                                        )
                                                        HeaderIconButton(
                                                                Icons.Default.PowerSettingsNew,
                                                                "Quitter",
                                                                {
                                                                        exoPlayer?.stop()
                                                                        (context as?
                                                                                        android.app.Activity)
                                                                                ?.finishAffinity()
                                                                },
                                                                tintNormal = Color.Red
                                                        )
                                                }
                                        } else {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                        // Row 1: Title + Search Toggle
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween
                                                        ) {
                                                                Text(
                                                                        "SimpleIPTV",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .headlineSmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                )
                                                                HeaderIconButton(
                                                                        Icons.Default.Search,
                                                                        "Recherche",
                                                                        {
                                                                                isSearchVisibleOnMobile =
                                                                                        !isSearchVisibleOnMobile
                                                                        }
                                                                )
                                                        }
                                                        Spacer(Modifier.height(8.dp))
                                                        // Row 2: Actions (Scrollable)
                                                        LazyRow(
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(16.dp),
                                                                modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                                item {
                                                                        HeaderIconButton(
                                                                                Icons.Default
                                                                                        .Person,
                                                                                "Profils",
                                                                                {
                                                                                        showProfileManager =
                                                                                                true
                                                                                }
                                                                        )
                                                                }
                                                                item {
                                                                        HeaderIconButton(
                                                                                Icons.Default
                                                                                        .Refresh,
                                                                                "Actualiser",
                                                                                {
                                                                                        scope
                                                                                                .launch {
                                                                                                        isLoading =
                                                                                                                true
                                                                                                        val profile =
                                                                                                                profiles
                                                                                                                        .find {
                                                                                                                                it.id ==
                                                                                                                                        activeProfileId
                                                                                                                        }
                                                                                                        if (profile !=
                                                                                                                        null
                                                                                                        ) {
                                                                                                                try {
                                                                                                                        iptvRepository
                                                                                                                                .refreshDatabase(
                                                                                                                                        profile
                                                                                                                                )
                                                                                                                } catch (
                                                                                                                        e:
                                                                                                                                Exception) {
                                                                                                                        Toast.makeText(
                                                                                                                                        context,
                                                                                                                                        "Erreur: ${e.message}",
                                                                                                                                        Toast.LENGTH_SHORT
                                                                                                                                )
                                                                                                                                .show()
                                                                                                                }
                                                                                                        }
                                                                                                        isLoading =
                                                                                                                false
                                                                                                }
                                                                                }
                                                                        )
                                                                }
                                                                item {
                                                                        HeaderIconButton(
                                                                                Icons.Default
                                                                                        .CloudUpload,
                                                                                "Sauvegarder",
                                                                                {
                                                                                        createDocumentLauncher
                                                                                                .launch(
                                                                                                        "simple_iptv_backup.json"
                                                                                                )
                                                                                }
                                                                        )
                                                                }
                                                                item {
                                                                        HeaderIconButton(
                                                                                Icons.Default
                                                                                        .CloudDownload,
                                                                                "Restaurer",
                                                                                {
                                                                                        openDocumentLauncher
                                                                                                .launch(
                                                                                                        "application/json"
                                                                                                )
                                                                                }
                                                                        )
                                                                }
                                                                item {
                                                                        HeaderIconButton(
                                                                                Icons.Default
                                                                                        .PowerSettingsNew,
                                                                                "Quitter",
                                                                                {
                                                                                        exoPlayer
                                                                                                ?.stop()
                                                                                        (context as?
                                                                                                        android.app.Activity)
                                                                                                ?.finishAffinity()
                                                                                },
                                                                                tintNormal =
                                                                                        Color.Red
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }

                                if (!isLandscape && isSearchVisibleOnMobile) {
                                        Card(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 4.dp
                                                                ),
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.3f)
                                                        )
                                        ) {
                                                TvInput(
                                                        value = searchQuery,
                                                        onValueChange = { query ->
                                                                searchQuery = query
                                                        },
                                                        label = "Filtrer les chaînes...",
                                                        focusManager = LocalFocusManager.current,
                                                        leadingIcon = Icons.Default.Search,
                                                        modifier = Modifier.padding(4.dp)
                                                )
                                        }
                                }

                                if (isLoading)
                                        LinearProgressIndicator(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 8.dp)
                                        )

                                if (isLandscape) {
                                        Row(modifier = Modifier.weight(1f)) {
                                                // Sidebar (Landscape)
                                                LazyColumn(
                                                        modifier =
                                                                Modifier.weight(0.35f)
                                                                        .fillMaxHeight()
                                                                        .padding(8.dp),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(4.dp)
                                                ) {
                                                        item {
                                                                SidebarItem(
                                                                        text = "Récents",
                                                                        icon =
                                                                                Icons.Default
                                                                                        .History,
                                                                        isSelected = showRecentOnly,
                                                                        onClick = {
                                                                                showRecentOnly =
                                                                                        true
                                                                                selectedCategoryId =
                                                                                        null
                                                                                selectedFavoriteListId =
                                                                                        -1
                                                                                searchQuery = ""
                                                                        }
                                                                )
                                                        }
                                                        item { Spacer(Modifier.height(8.dp)) }
                                                        item {
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically,
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                "Favoris",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleSmall,
                                                                                color = Color.Gray,
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        )
                                                                        IconButton(
                                                                                onClick = {
                                                                                        showAddListDialog =
                                                                                                true
                                                                                },
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                24.dp
                                                                                        )
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Add,
                                                                                        null,
                                                                                        tint =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                        items(favoriteLists) { list ->
                                                                SidebarItem(
                                                                        text = list.name,
                                                                        icon = Icons.Default.Star,
                                                                        isSelected =
                                                                                selectedFavoriteListId ==
                                                                                        list.id,
                                                                        onClick = {
                                                                                selectedFavoriteListId =
                                                                                        list.id
                                                                                selectedCategoryId =
                                                                                        null
                                                                                showRecentOnly =
                                                                                        false
                                                                                searchQuery = ""
                                                                        },
                                                                        onDelete = {
                                                                                scope.launch {
                                                                                        iptvRepository
                                                                                                .removeFavoriteList(
                                                                                                        list
                                                                                                )
                                                                                }
                                                                        }
                                                                )
                                                        }
                                                        item { Spacer(Modifier.height(8.dp)) }
                                                        item {
                                                                Text(
                                                                        "Catégories",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleSmall,
                                                                        color = Color.Gray,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        8.dp
                                                                                )
                                                                )
                                                        }
                                                        items(categories) { category ->
                                                                SidebarItem(
                                                                        text =
                                                                                category.category_name,
                                                                        isSelected =
                                                                                selectedCategoryId ==
                                                                                        category.category_id,
                                                                        onClick = {
                                                                                selectedCategoryId =
                                                                                        category.category_id
                                                                                selectedFavoriteListId =
                                                                                        -1
                                                                                showRecentOnly =
                                                                                        false
                                                                                searchQuery = ""
                                                                        }
                                                                )
                                                        }
                                                }

                                                // Channel List (Landscape)
                                                LazyColumn(
                                                        modifier =
                                                                Modifier.weight(0.65f)
                                                                        .fillMaxHeight()
                                                                        .padding(8.dp),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        items(channels) { channel ->
                                                                ChannelItem(
                                                                        channel = channel,
                                                                        isPlaying =
                                                                                playingChannel
                                                                                        ?.stream_id ==
                                                                                        channel.stream_id,
                                                                        onClick = {
                                                                                onChannelClick(
                                                                                        channel
                                                                                )
                                                                        },
                                                                        onFavoriteClick = {
                                                                                channelToFavorite =
                                                                                        channel
                                                                        }
                                                                )
                                                        }
                                                }
                                        }
                                } else {
                                        // PORTRAIT LAYOUT
                                        Column(modifier = Modifier.weight(1f)) {
                                                // Horizontal Categories
                                                LazyRow(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(8.dp),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        item {
                                                                TvFilterChip(
                                                                        selected = showRecentOnly,
                                                                        onClick = {
                                                                                showRecentOnly =
                                                                                        true
                                                                                selectedCategoryId =
                                                                                        null
                                                                                selectedFavoriteListId =
                                                                                        -1
                                                                                searchQuery = ""
                                                                        },
                                                                        label = "Récents",
                                                                        icon = Icons.Default.History
                                                                )
                                                        }
                                                        item {
                                                                TvFilterChip(
                                                                        selected = false,
                                                                        onClick = {
                                                                                showAddListDialog =
                                                                                        true
                                                                        },
                                                                        label = "Ajouter",
                                                                        icon = Icons.Default.Add
                                                                )
                                                        }
                                                        items(favoriteLists) { list ->
                                                                TvFilterChip(
                                                                        selected =
                                                                                selectedFavoriteListId ==
                                                                                        list.id,
                                                                        onClick = {
                                                                                selectedFavoriteListId =
                                                                                        list.id
                                                                                selectedCategoryId =
                                                                                        null
                                                                                showRecentOnly =
                                                                                        false
                                                                                searchQuery = ""
                                                                        },
                                                                        label = list.name,
                                                                        icon = Icons.Default.Star
                                                                )
                                                        }
                                                        items(categories) { category ->
                                                                TvFilterChip(
                                                                        selected =
                                                                                selectedCategoryId ==
                                                                                        category.category_id,
                                                                        onClick = {
                                                                                selectedCategoryId =
                                                                                        category.category_id
                                                                                selectedFavoriteListId =
                                                                                        -1
                                                                                showRecentOnly =
                                                                                        false
                                                                                searchQuery = ""
                                                                        },
                                                                        label =
                                                                                category.category_name
                                                                )
                                                        }
                                                }
                                                // Full Width Channel List (Portrait)
                                                LazyColumn(
                                                        modifier =
                                                                Modifier.weight(1f).padding(8.dp),
                                                        verticalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        items(channels) { channel ->
                                                                ChannelItem(
                                                                        channel = channel,
                                                                        isPlaying =
                                                                                playingChannel
                                                                                        ?.stream_id ==
                                                                                        channel.stream_id,
                                                                        onClick = {
                                                                                onChannelClick(
                                                                                        channel
                                                                                )
                                                                        },
                                                                        onFavoriteClick = {
                                                                                channelToFavorite =
                                                                                        channel
                                                                        },
                                                                        debugInfo =
                                                                                "Profile: $activeProfileId, Lists: ${favoriteLists.size}"
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }

        // Dialogs
        if (showProfileManager) {
                ProfileManagerDialog(
                        profiles = profiles,
                        onDismiss = { showProfileManager = false },
                        onSelectProfile = { profile ->
                                scope.launch {
                                        iptvRepository.selectProfile(profile.id)
                                        activeProfileId = profile.id
                                        showProfileManager = false
                                }
                        },
                        onAdd = { showProfileAddDialog = true },
                        onEdit = { showProfileEditDialog = it },
                        onDeleteProfile = { scope.launch { iptvRepository.deleteProfile(it) } }
                )
        }

        if (showProfileAddDialog) {
                ProfileFormDialog(
                        onDismiss = { showProfileAddDialog = false },
                        onSave = { n, u, us, p, mac, type ->
                                scope.launch {
                                        iptvRepository.addProfile(
                                                ProfileEntity(
                                                        profileName = n,
                                                        url = u,
                                                        username = us,
                                                        password = p,
                                                        macAddress = mac,
                                                        type = type
                                                )
                                        )
                                        showProfileAddDialog = false
                                }
                        }
                )
        }

        if (showProfileEditDialog != null) {
                ProfileFormDialog(
                        profile = showProfileEditDialog,
                        onDismiss = { showProfileEditDialog = null },
                        onSave = { n, u, us, p, mac, type ->
                                scope.launch {
                                        iptvRepository.updateProfile(
                                                showProfileEditDialog!!.copy(
                                                        profileName = n,
                                                        url = u,
                                                        username = us,
                                                        password = p,
                                                        macAddress = mac,
                                                        type = type
                                                )
                                        )
                                        showProfileEditDialog = null
                                }
                        }
                )
        }

        if (showAddListDialog) {
                var listName by remember { mutableStateOf("") }
                AlertDialog(
                        onDismissRequest = { showAddListDialog = false },
                        title = { Text("Nouvelle Liste") },
                        text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Créez une nouvelle liste de favoris.")
                                        TvInput(
                                                value = listName,
                                                onValueChange = { text -> listName = text },
                                                label = "Nom de la liste",
                                                focusManager = LocalFocusManager.current
                                        )
                                }
                        },
                        confirmButton = {
                                var isAddFocused by remember { mutableStateOf(false) }
                                TextButton(
                                        modifier =
                                                Modifier.focusable()
                                                        .onFocusChanged {
                                                                isAddFocused = it.isFocused
                                                        }
                                                        .scale(if (isAddFocused) 1.05f else 1f)
                                                        .background(
                                                                if (isAddFocused)
                                                                        Color.White.copy(
                                                                                alpha = 0.2f
                                                                        )
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.small
                                                        ),
                                        onClick = {
                                                if (listName.isBlank()) {
                                                        Toast.makeText(
                                                                        context,
                                                                        "Nom vide!",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                } else {
                                                        scope.launch {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Ajout: '$listName' (id: $activeProfileId)",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                                iptvRepository.addFavoriteList(
                                                                        listName,
                                                                        activeProfileId
                                                                )
                                                                showAddListDialog = false
                                                        }
                                                }
                                        }
                                ) { Text("Ajouter") }
                        },
                        dismissButton = {
                                TextButton(
                                        modifier = Modifier.focusable(),
                                        onClick = { showAddListDialog = false }
                                ) { Text("Annuler") }
                        }
                )
        }

        if (channelToFavorite != null) {
                GenericFavoriteDialog(
                        title = "Ajouter aux favoris",
                        items = favoriteLists,
                        getName = { profile -> profile.name },
                        getId = { profile -> profile.id },
                        onDismiss = { channelToFavorite = null },
                        onToggle = { listId ->
                                scope.launch {
                                        iptvRepository.toggleChannelFavorite(
                                                channelToFavorite!!.stream_id,
                                                listId,
                                                activeProfileId
                                        )
                                }
                        },
                        selectedIdsProvider = {
                                iptvRepository.getListIdsForChannel(
                                        channelToFavorite!!.stream_id,
                                        activeProfileId
                                )
                        }
                )
        }

        if (showRestoreConfirmDialog) {
                AlertDialog(
                        onDismissRequest = { showRestoreConfirmDialog = false },
                        title = { Text("Confirmation de Restauration") },
                        text = {
                                Text(
                                        "Voulez-vous restaurer les données depuis 'simple_iptv_backup.json' ?\n\nATTENTION : Cette action écrasera vos profils et favoris actuels."
                                )
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                scope.launch {
                                                        try {
                                                                iptvRepository
                                                                        .importDatabaseFromJson(
                                                                                backupJsonToRestore
                                                                        )
                                                                showRestoreConfirmDialog = false
                                                                Toast.makeText(
                                                                                context,
                                                                                "Restauration terminée !",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                        } catch (e: Exception) {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Erreur import: ${e.message}",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                        }
                                                }
                                        }
                                ) { Text("Confirmer") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                                        Text("Annuler")
                                }
                        }
                )
        }
}

@Composable
fun ProfileFormDialog(
        profile: ProfileEntity? = null,
        onDismiss: () -> Unit,
        onSave: (String, String, String, String, String?, String) -> Unit
) {
        val focusManager = LocalFocusManager.current
        var name by remember { mutableStateOf(profile?.profileName ?: "") }
        var url by remember { mutableStateOf(profile?.url ?: "") }
        var user by remember { mutableStateOf(profile?.username ?: "") }
        var pass by remember { mutableStateOf(profile?.password ?: "") }
        var mac by remember { mutableStateOf(profile?.macAddress ?: "") }
        var type by remember { mutableStateOf(profile?.type ?: "xtream") }

        var isM3uMode by remember { mutableStateOf(false) }
        var m3uUrlInput by remember { mutableStateOf("") }
        var isSaveFocused by remember { mutableStateOf(false) }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                        Text(if (profile == null) "Ajouter un profil" else "Modifier le profil")
                },
                text = {
                        Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                                TvInput(
                                        value = name,
                                        onValueChange = { text -> name = text },
                                        label = "Nom du profil",
                                        focusManager = focusManager
                                )

                                // Protocol Selection
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                        Text("Protocole: ", color = Color.Gray)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                        selected = type == "xtream",
                                                        onClick = { type = "xtream" }
                                                )
                                                Text("Xtream")
                                                Spacer(modifier = Modifier.width(16.dp))
                                                RadioButton(
                                                        selected = type == "stalker",
                                                        onClick = { type = "stalker" }
                                                )
                                                Text("Stalker / MAC")
                                        }
                                }

                                if (type == "xtream") {
                                        if (profile == null) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(top = 8.dp)
                                                ) {
                                                        Text(
                                                                "Utiliser URL M3U",
                                                                modifier = Modifier.weight(1f)
                                                        )
                                                        Switch(
                                                                checked = isM3uMode,
                                                                onCheckedChange = { isM3uMode = it }
                                                        )
                                                }
                                        }

                                        if (isM3uMode) {
                                                TvInput(
                                                        value = m3uUrlInput,
                                                        onValueChange = { input ->
                                                                m3uUrlInput = input
                                                                Regex("username=([^&]+)")
                                                                        .find(input)
                                                                        ?.let {
                                                                                user =
                                                                                        it.groupValues[
                                                                                                1]
                                                                        }
                                                                Regex("password=([^&]+)")
                                                                        .find(input)
                                                                        ?.let {
                                                                                pass =
                                                                                        it.groupValues[
                                                                                                1]
                                                                        }
                                                                Regex("^(https?://[^/?]+)")
                                                                        .find(input)
                                                                        ?.let {
                                                                                url =
                                                                                        it.groupValues[
                                                                                                1] +
                                                                                                "/"
                                                                        }
                                                        },
                                                        label = "Lien M3U",
                                                        focusManager = focusManager
                                                )
                                        }
                                }

                                TvInput(
                                        value = url,
                                        onValueChange = { text -> url = text },
                                        label =
                                                if (type == "stalker") "URL Portal (http://...)"
                                                else "URL Serveur",
                                        focusManager = focusManager
                                )

                                if (type == "xtream") {
                                        TvInput(
                                                value = user,
                                                onValueChange = { text -> user = text },
                                                label = "Utilisateur",
                                                focusManager = focusManager
                                        )
                                        TvInput(
                                                value = pass,
                                                onValueChange = { text -> pass = text },
                                                label = "Mot de passe",
                                                isPassword = false,
                                                focusManager = focusManager
                                        )
                                } else {
                                        TvInput(
                                                value = mac,
                                                onValueChange = { text -> mac = text },
                                                label = "MAC Address (00:1A:79:...)",
                                                focusManager = focusManager
                                        )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                        onClick = { onSave(name, url, user, pass, mac, type) },
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .onFocusChanged {
                                                                isSaveFocused = it.isFocused
                                                        }
                                                        .scale(if (isSaveFocused) 1.05f else 1f)
                                                        .background(
                                                                if (isSaveFocused)
                                                                        Color.White.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.medium
                                                        ),
                                        shape = MaterialTheme.shapes.medium
                                ) {
                                        Icon(Icons.Default.Save, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Enregistrer le Profil")
                                }
                        }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
        )
}

@Composable
fun TvInput(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        isPassword: Boolean = false,
        focusManager: androidx.compose.ui.focus.FocusManager,
        leadingIcon: ImageVector? = null
) {
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }

        AndroidView(
                modifier =
                        modifier.fillMaxWidth()
                                .height(60.dp)
                                .onFocusChanged { isFocused = it.isFocused }
                                .scale(if (isFocused) 1.02f else 1f)
                                .background(
                                        color =
                                                if (isFocused) Color.White.copy(alpha = 0.95f)
                                                else
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.medium
                                ),
                factory = { ctx ->
                        EditText(ctx).apply {
                                // 1. Setup Basic Properties
                                setHint(label)
                                setSingleLine(true)
                                setTextColor(android.graphics.Color.WHITE)
                                setHintTextColor(android.graphics.Color.GRAY)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setPadding(32, 16, 32, 16)
                                setGravity(Gravity.CENTER_VERTICAL)
                                textSize = 16f

                                // 2. Input Type Setup
                                inputType =
                                        if (isPassword) {
                                                InputType.TYPE_CLASS_TEXT or
                                                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                                        } else {
                                                InputType.TYPE_CLASS_TEXT
                                        }
                                imeOptions = EditorInfo.IME_ACTION_DONE

                                // 3. ESSENTIAL: Prevent keyboard on simple focus
                                showSoftInputOnFocus = false

                                // CRITICAL: Sync focus state to Compose for visual updates
                                setOnFocusChangeListener { _, hasFocus -> isFocused = hasFocus }

                                // 4. Listen for OK / ENTER to force keyboard
                                setOnKeyListener { v, keyCode, event ->
                                        if (event.action == KeyEvent.ACTION_UP &&
                                                        (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                                                keyCode ==
                                                                        KeyEvent.KEYCODE_NUMPAD_ENTER)
                                        ) {

                                                // Explicitly request focus
                                                v.requestFocus()

                                                // Force Validation
                                                val imm =
                                                        ctx.getSystemService(
                                                                Context.INPUT_METHOD_SERVICE
                                                        ) as
                                                                InputMethodManager
                                                @Suppress("DEPRECATION")
                                                imm.showSoftInput(v, InputMethodManager.SHOW_FORCED)
                                                return@setOnKeyListener true
                                        }
                                        false
                                }

                                // 5. Handle Text Changes
                                addTextChangedListener(
                                        object : android.text.TextWatcher {
                                                override fun beforeTextChanged(
                                                        s: CharSequence?,
                                                        start: Int,
                                                        count: Int,
                                                        after: Int
                                                ) {}
                                                override fun onTextChanged(
                                                        s: CharSequence?,
                                                        start: Int,
                                                        before: Int,
                                                        count: Int
                                                ) {
                                                        // prevent infinite loop
                                                        if (s.toString() != value) {
                                                                onValueChange(s.toString())
                                                        }
                                                }
                                                override fun afterTextChanged(
                                                        s: android.text.Editable?
                                                ) {}
                                        }
                                )
                        }
                },
                update = { editText ->
                        if (editText.text.toString() != value) {
                                editText.setText(value)
                                try {
                                        editText.setSelection(editText.text.length)
                                } catch (e: Exception) {
                                        // Ignore selection error
                                }
                        }
                        // DYNAMIC COLOR UPDATE
                        if (isFocused) {
                                editText.setTextColor(android.graphics.Color.BLACK)
                                editText.setHintTextColor(android.graphics.Color.DKGRAY)
                        } else {
                                editText.setTextColor(android.graphics.Color.WHITE)
                                editText.setHintTextColor(android.graphics.Color.GRAY)
                        }
                }
        )
}

@Composable
fun ProfileManagerDialog(
        profiles: List<ProfileEntity>,
        onDismiss: () -> Unit,
        onSelectProfile: (ProfileEntity) -> Unit,
        onEdit: (ProfileEntity) -> Unit,
        onDeleteProfile: (ProfileEntity) -> Unit,
        onAdd: () -> Unit
) {
        var profileToDelete by remember { mutableStateOf<ProfileEntity?>(null) }

        if (profileToDelete != null) {
                AlertDialog(
                        onDismissRequest = { profileToDelete = null },
                        title = { Text("Supprimer le profil ?") },
                        text = {
                                Text(
                                        "Êtes-vous sûr de vouloir supprimer '${profileToDelete?.profileName}' ?\nCette action est irréversible."
                                )
                        },
                        confirmButton = {
                                var isFocused by remember { mutableStateOf(false) }
                                Button(
                                        onClick = {
                                                profileToDelete?.let { onDeleteProfile(it) }
                                                profileToDelete = null
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Color.Red
                                                ),
                                        modifier =
                                                Modifier.onFocusChanged { isFocused = it.isFocused }
                                                        .scale(if (isFocused) 1.05f else 1f)
                                                        .border(
                                                                if (isFocused) 2.dp else 0.dp,
                                                                Color.White,
                                                                MaterialTheme.shapes.extraSmall
                                                        )
                                ) { Text("Supprimer") }
                        },
                        dismissButton = {
                                var isFocused by remember { mutableStateOf(false) }
                                TextButton(
                                        onClick = { profileToDelete = null },
                                        modifier =
                                                Modifier.onFocusChanged { isFocused = it.isFocused }
                                                        .scale(if (isFocused) 1.05f else 1f)
                                                        .background(
                                                                if (isFocused)
                                                                        Color.White.copy(
                                                                                alpha = 0.2f
                                                                        )
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.extraSmall
                                                        )
                                ) { Text("Annuler") }
                        }
                )
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Gérer les profils") },
                confirmButton = {
                        var isFocused by remember { mutableStateOf(false) }
                        TextButton(
                                onClick = onAdd,
                                modifier =
                                        Modifier.onFocusChanged { isFocused = it.isFocused }
                                                .scale(if (isFocused) 1.05f else 1f)
                                                .background(
                                                        if (isFocused)
                                                                Color.White.copy(alpha = 0.2f)
                                                        else Color.Transparent,
                                                        MaterialTheme.shapes.extraSmall
                                                )
                        ) { Text("Nouveau Profil") }
                },
                dismissButton = {
                        var isFocused by remember { mutableStateOf(false) }
                        TextButton(
                                onClick = onDismiss,
                                modifier =
                                        Modifier.onFocusChanged { isFocused = it.isFocused }
                                                .scale(if (isFocused) 1.05f else 1f)
                                                .background(
                                                        if (isFocused)
                                                                Color.White.copy(alpha = 0.2f)
                                                        else Color.Transparent,
                                                        MaterialTheme.shapes.extraSmall
                                                )
                        ) { Text("Fermer") }
                },
                text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(profiles) { profile ->
                                        var isSelectFocused by remember { mutableStateOf(false) }
                                        var isEditFocused by remember { mutableStateOf(false) }
                                        var isDeleteFocused by remember { mutableStateOf(false) }

                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                // 1. Selection Area (Radio + Name)
                                                Surface(
                                                        modifier =
                                                                Modifier.weight(1f)
                                                                        .onFocusChanged {
                                                                                isSelectFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .scale(
                                                                                if (isSelectFocused)
                                                                                        1.02f
                                                                                else 1f
                                                                        )
                                                                        .clickable {
                                                                                onSelectProfile(
                                                                                        profile
                                                                                )
                                                                        }
                                                                        .focusable(),
                                                        shape = MaterialTheme.shapes.small,
                                                        color =
                                                                if (isSelectFocused)
                                                                        Color.White.copy(
                                                                                alpha = 0.9f
                                                                        )
                                                                else if (profile.isSelected)
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                else Color.Transparent
                                                ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                modifier = Modifier.padding(8.dp)
                                                        ) {
                                                                RadioButton(
                                                                        selected =
                                                                                profile.isSelected,
                                                                        onClick =
                                                                                null, // Handled by
                                                                        // Surface
                                                                        // click
                                                                        colors =
                                                                                RadioButtonDefaults
                                                                                        .colors(
                                                                                                selectedColor =
                                                                                                        if (isSelectFocused
                                                                                                        )
                                                                                                                Color.Black
                                                                                                        else
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .primary,
                                                                                                unselectedColor =
                                                                                                        if (isSelectFocused
                                                                                                        )
                                                                                                                Color.Gray
                                                                                                        else
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .onSurfaceVariant
                                                                                        )
                                                                )
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start = 8.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                profile.profileName,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleMedium,
                                                                                color =
                                                                                        if (isSelectFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.Unspecified
                                                                        )
                                                                        Text(
                                                                                profile.url,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        if (isSelectFocused
                                                                                        )
                                                                                                Color.DarkGray
                                                                                        else
                                                                                                Color.Gray,
                                                                                maxLines = 1
                                                                        )
                                                                }
                                                        }
                                                }

                                                Spacer(Modifier.width(8.dp))

                                                // 2. Edit Button
                                                IconButton(
                                                        onClick = { onEdit(profile) },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isEditFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .scale(
                                                                                if (isEditFocused)
                                                                                        1.1f
                                                                                else 1f
                                                                        )
                                                                        .background(
                                                                                if (isEditFocused)
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.2f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Modifier",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                }

                                                // 3. Delete Button (Triggers Confirmation)
                                                IconButton(
                                                        onClick = { profileToDelete = profile },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isDeleteFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .scale(
                                                                                if (isDeleteFocused)
                                                                                        1.1f
                                                                                else 1f
                                                                        )
                                                                        .background(
                                                                                if (isDeleteFocused)
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.2f
                                                                                                )
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Supprimer",
                                                                tint = Color.Red
                                                        )
                                                }
                                        }
                                }
                        }
                }
        )
}

@Composable
fun HeaderIconButton(
        icon: ImageVector,
        desc: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        tintNormal: Color = MaterialTheme.colorScheme.primary
) {
        var isFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = onClick,
                modifier =
                        modifier.size(40.dp)
                                .onFocusChanged { state -> isFocused = state.isFocused }
                                .scale(if (isFocused) 1.1f else 1f)
                                .background(
                                        if (isFocused) Color.White.copy(alpha = 0.2f)
                                        else Color.Transparent,
                                        MaterialTheme.shapes.small
                                )
        ) {
                Icon(
                        icon,
                        desc,
                        tint = if (isFocused) Color.White else tintNormal,
                        modifier = Modifier.size(24.dp)
                )
        }
}

@Composable
fun SidebarItem(
        text: String,
        icon: ImageVector? = null,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        onDelete: (() -> Unit)? = null
) {
        var isItemFocused by remember { mutableStateOf(false) }
        var isDeleteFocused by remember { mutableStateOf(false) }

        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 1. Main Item Box
                Card(
                        modifier =
                                Modifier.weight(1f)
                                        .onFocusChanged { state -> isItemFocused = state.isFocused }
                                        .scale(if (isItemFocused) 1.02f else 1f)
                                        .clickable { onClick() }
                                        .focusable(),
                        border =
                                if (isSelected && !isItemFocused)
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else null,
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (isItemFocused) Color.White.copy(alpha = 0.95f)
                                                else if (isSelected)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface
                                )
                ) {
                        Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                if (icon != null) {
                                        Icon(
                                                icon,
                                                null,
                                                tint =
                                                        if (isItemFocused) Color.Black
                                                        else if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                        else Color.Gray
                                        )
                                        Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                        text,
                                        maxLines = 1,
                                        color =
                                                if (isItemFocused) Color.Black
                                                else Color.Unspecified
                                )
                        }
                }

                // 2. Delete Button (if exists)
                if (onDelete != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                                modifier =
                                        Modifier.size(48.dp)
                                                .onFocusChanged { isDeleteFocused = it.isFocused }
                                                .scale(if (isDeleteFocused) 1.1f else 1f)
                                                .clickable { onDelete() }
                                                .focusable(),
                                shape = CircleShape,
                                color =
                                        if (isDeleteFocused) Color.White.copy(alpha = 0.2f)
                                        else Color.Transparent
                        ) {
                                Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                                Icons.Default.Delete,
                                                null,
                                                tint =
                                                        if (isDeleteFocused) Color.Red
                                                        else Color.Gray
                                        )
                                }
                        }
                }
        }
}

@Composable
fun ChannelItem(
        channel: ChannelEntity,
        isPlaying: Boolean,
        onClick: () -> Unit,
        onFavoriteClick: () -> Unit,
        modifier: Modifier = Modifier,
        debugInfo: String = "" // Add debug info param
) {
        var isChannelFocused by remember { mutableStateOf(false) }
        var isFavFocused by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Row(
                modifier = modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // 1. Channel Info Block (Clickable Surface)
                Surface(
                        modifier =
                                Modifier.weight(1f)
                                        .height(60.dp) // Fixed height for consistency
                                        .onFocusChanged { state ->
                                                isChannelFocused = state.isFocused
                                        }
                                        .scale(if (isChannelFocused) 1.02f else 1f)
                                        .clickable { onClick() }
                                        .focusable(), // Explicit
                        shape = MaterialTheme.shapes.small,
                        color =
                                if (isChannelFocused) Color.White.copy(alpha = 0.95f)
                                else if (isPlaying)
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.6f
                                        )
                                else MaterialTheme.colorScheme.surface,
                        border =
                                when {
                                        isPlaying -> BorderStroke(2.dp, Color.Green)
                                        else -> BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                                }
                ) {
                        Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                AsyncImage(
                                        model = channel.stream_icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                        channel.name,
                                        color =
                                                if (isChannelFocused) Color.Black
                                                else if (isPlaying) Color.Green
                                                else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                )
                                if (isPlaying) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                                Icons.Default.PlayArrow,
                                                null,
                                                tint = Color.Green,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                        }
                }

                // Spacer
                Spacer(Modifier.width(8.dp))

                // 2. Favorite Star Block (Clickable Surface for better focus control)
                Surface(
                        modifier =
                                Modifier.size(60.dp) // Square button, same height as channel
                                        .onFocusChanged { state -> isFavFocused = state.isFocused }
                                        .scale(if (isFavFocused) 1.1f else 1f)
                                        .clickable {
                                                Toast.makeText(
                                                                context,
                                                                debugInfo,
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                onFavoriteClick()
                                        }
                                        .focusable(), // Explicit
                        shape = CircleShape,
                        color =
                                if (isFavFocused) Color.White.copy(alpha = 0.2f)
                                else Color.Transparent
                ) {
                        Box(contentAlignment = Alignment.Center) {
                                Icon(
                                        Icons.Default.Star,
                                        null,
                                        tint = if (isFavFocused) Color.White else Color.Gray,
                                        modifier = Modifier.size(32.dp) // Larger Icon
                                )
                        }
                }
        }
}

@Composable
fun <T> GenericFavoriteDialog(
        title: String,
        items: List<T>,
        getName: (T) -> String,
        getId: (T) -> Int,
        onDismiss: () -> Unit,
        onToggle: (Int) -> Unit,
        selectedIdsProvider: suspend () -> List<Int>
) {
        val selectedIds = remember { mutableStateListOf<Int>() }
        LaunchedEffect(Unit) {
                selectedIds.clear()
                selectedIds.addAll(selectedIdsProvider())
        }
        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("$title (${items.size})") }, // Debug: Show count
                text = {
                        if (items.isEmpty()) Text("Aucune liste.")
                        else
                                LazyColumn {
                                        items(items) { list ->
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clickable {
                                                                                onToggle(
                                                                                        getId(list)
                                                                                )
                                                                                if (selectedIds
                                                                                                .contains(
                                                                                                        getId(
                                                                                                                list
                                                                                                        )
                                                                                                )
                                                                                )
                                                                                        selectedIds
                                                                                                .remove(
                                                                                                        getId(
                                                                                                                list
                                                                                                        )
                                                                                                )
                                                                                else
                                                                                        selectedIds
                                                                                                .add(
                                                                                                        getId(
                                                                                                                list
                                                                                                        )
                                                                                                )
                                                                        }
                                                                        .focusable() // CRITICAL:
                                                                        // Makes row
                                                                        // focusable on
                                                                        // TV
                                                                        .padding(8.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Checkbox(
                                                                checked =
                                                                        selectedIds.contains(
                                                                                getId(list)
                                                                        ),
                                                                onCheckedChange = null
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(getName(list))
                                                }
                                        }
                                }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
        )
}

@Composable
fun TvFilterChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: String?,
        icon: ImageVector? = null,
        modifier: Modifier = Modifier
) {
        var isFocused by remember { mutableStateOf(false) }
        Surface(
                modifier =
                        modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .scale(if (isFocused) 1.1f else 1f)
                                .clickable { onClick() }
                                .focusable(),
                shape = CircleShape,
                color =
                        if (isFocused) Color.White.copy(alpha = 0.9f)
                        else if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (icon != null) {
                                Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint =
                                                if (isFocused) Color.Black
                                                else MaterialTheme.colorScheme.onSurface
                                )
                                if (label != null) {
                                        Spacer(Modifier.width(8.dp))
                                }
                        }
                        if (label != null) {
                                Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color =
                                                if (isFocused) Color.Black
                                                else MaterialTheme.colorScheme.onSurface
                                )
                        }
                }
        }
}
