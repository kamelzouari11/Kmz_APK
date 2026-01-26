package com.example.simpleiptv

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.AppDatabase
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.ui.theme.SimpleIPTVTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.ui.input.key.*
import android.widget.Toast
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.app.Activity
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val database = AppDatabase.getDatabase(this)
        val iptvApi = XtreamClient.create("http://j.delta2022.xyz:8880/")
        val iptvRepository = IptvRepository(iptvApi, database.iptvDao())
        setContent {
            SimpleIPTVTheme(darkTheme = true) {
                MainScreen(iptvRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(iptvRepository: IptvRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var playingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var isFullScreenPlayer by remember { mutableStateOf(false) }
    var playerIsPlaying by remember { mutableStateOf(false) }

    val controllerFuture = remember {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).buildAsync()
    }
    var exoPlayer by remember { mutableStateOf<androidx.media3.common.Player?>(null) }

    LaunchedEffect(controllerFuture) {
        controllerFuture.addListener({
            try {
                exoPlayer = controllerFuture.get()
                exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playerIsPlaying = isPlaying
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Toast.makeText(context, "Erreur de lecture : URL corrompue ou indisponible", Toast.LENGTH_SHORT).show()
                        playingChannel = null
                        isFullScreenPlayer = false
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur d'initialisation du service", Toast.LENGTH_LONG).show()
            }
        }, MoreExecutors.directExecutor())
    }

    val listFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isFullScreenPlayer) {
        if (!isFullScreenPlayer) {
            delay(500)
            try { listFocusRequester.requestFocus() } catch(e:Exception){}
        }
    }

    DisposableEffect(Unit) {
        onDispose { 
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // --- IPTV STATE ---
    val categories by iptvRepository.allCategories.collectAsState(initial = emptyList())
    val favoriteLists by iptvRepository.allFavoriteLists.collectAsState(initial = emptyList())
    val recentChannels by iptvRepository.recentChannels.collectAsState(initial = emptyList())
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedFavoriteListId by remember { mutableStateOf<Int?>(null) }
    var showRecentOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val channelsFlow = remember(selectedCategoryId, selectedFavoriteListId, showRecentOnly, searchQuery) {
        when {
            searchQuery.isNotBlank() -> iptvRepository.searchChannels(searchQuery)
            showRecentOnly -> null
            selectedFavoriteListId != null -> iptvRepository.getChannelsByFavoriteList(selectedFavoriteListId!!)
            selectedCategoryId != null -> iptvRepository.getChannelsByCategory(selectedCategoryId!!)
            else -> null
        }
    }
    val dbChannels by (channelsFlow?.collectAsState(initial = emptyList()) ?: mutableStateOf(emptyList()))
    val channels = if (showRecentOnly && searchQuery.isBlank()) recentChannels else dbChannels
    
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
   
    var navChannelList by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }

    var channelToFavorite by remember { mutableStateOf<ChannelEntity?>(null) }
    var showAddListDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val user = "98342256-b1"; val pass = "90626936"; val baseUrl = "http://j.delta2022.xyz:8880"

    LaunchedEffect(categories) {
        if (categories.isEmpty() && !isLoading) {
            isLoading = true; try { iptvRepository.refreshDatabase(user, pass) } catch (e: Exception) {}; isLoading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SimpleIPTV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.7f))
                            IconButton(onClick = { 
                                scope.launch { 
                                    isLoading = true
                                    try { iptvRepository.refreshDatabase(user, pass) } catch (e: Exception) {}
                                    isLoading = false
                                }
                            }, modifier = Modifier.weight(0.15f).size(48.dp)) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                            IconButton(onClick = { 
                                exoPlayer?.stop()
                                context.stopService(Intent(context, PlaybackService::class.java))
                                (context as? Activity)?.finish() 
                            }, modifier = Modifier.weight(0.15f).size(48.dp)) {
                                Icon(Icons.Default.PowerSettingsNew, "Quitter", tint = Color.Red, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it; if (it.isNotBlank()) { selectedCategoryId = null; selectedFavoriteListId = null; showRecentOnly = false } },
                                modifier = Modifier.weight(0.85f).focusRequester(searchFocusRequester)
                                    .onFocusChanged { if(!it.isFocused) isSearchActive = false }
                                    .onKeyEvent { if(it.key == Key.DirectionCenter || it.key == Key.Enter || it.nativeKeyEvent.keyCode == 66) { isSearchActive = true; false } else false },
                                placeholder = { Text("Rechercher des chaînes...") },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                                singleLine = true, shape = MaterialTheme.shapes.medium,
                                readOnly = !isSearchActive,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { interactionSource ->
                                    LaunchedEffect(interactionSource) {
                                        interactionSource.interactions.collect { interaction ->
                                            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) isSearchActive = true
                                        }
                                    }
                                }
                            )
                            if (playingChannel != null) {
                                IconButton(onClick = { isFullScreenPlayer = true }, modifier = Modifier.weight(0.15f)) {
                                    Icon(Icons.Default.PlayCircleOutline, "Player", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                }
                            } else {
                                Spacer(Modifier.weight(0.15f))
                            }
                        }
                    }
                }

                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))

                val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
                
                if (isPortrait) {
                    val isCategorySelected = selectedCategoryId != null || showRecentOnly || selectedFavoriteListId != null || searchQuery.isNotBlank()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (!isCategorySelected) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { SidebarItem(text = "Récents", icon = Icons.Default.History, isSelected = false, onClick = { showRecentOnly = true }) }
                                item { Text("Favoris", style = MaterialTheme.typography.titleSmall, color = Color.Gray); IconButton(onClick = { showAddListDialog = true }) { Icon(Icons.Default.Add, null) } }
                                items(favoriteLists) { list -> SidebarItem(text = list.name, isSelected = false, onClick = { selectedFavoriteListId = list.id }) }
                                item { Text("Catégories", style = MaterialTheme.typography.titleSmall, color = Color.Gray) }
                                items(categories) { category -> SidebarItem(text = category.category_name, isSelected = false, onClick = { selectedCategoryId = category.category_id }) }
                            }
                        } else {
                            Column {
                                IconButton(onClick = { selectedCategoryId = null; showRecentOnly = false; selectedFavoriteListId = null; searchQuery = "" }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Retour") }
                                }
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(channels) { channel -> MainItem(title = channel.name, iconUrl = channel.stream_icon, isPlaying = playingChannel?.stream_id == channel.stream_id, onClick = { 
                                        playingChannel = channel
                                        navChannelList = channels.toList()
                                        exoPlayer?.let { 
                                            val meta = androidx.media3.common.MediaMetadata.Builder().setTitle(channel.name).setArtworkUri(channel.stream_icon?.let { Uri.parse(it) }).build()
                                            val mi = androidx.media3.common.MediaItem.Builder().setUri("$baseUrl/live/$user/$pass/${channel.stream_id}.ts").setMediaMetadata(meta).build()
                                            it.setMediaItem(mi)
                                            it.prepare()
                                            it.play() 
                                        }
                                        isFullScreenPlayer = true
                                        scope.launch { iptvRepository.addToRecents(channel.stream_id) } 
                                    }, onAddFavorite = { channelToFavorite = channel }) }
                                }
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { SidebarItem(text = "Récents", icon = Icons.Default.History, isSelected = showRecentOnly && searchQuery.isBlank(), onClick = { showRecentOnly = true; selectedCategoryId = null; selectedFavoriteListId = null; searchQuery = "" }) }
                                item { Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Favoris", style = MaterialTheme.typography.titleSmall, color = Color.Gray); IconButton(onClick = { showAddListDialog = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) } } }
                                items(favoriteLists) { list -> SidebarItem(text = list.name, isSelected = selectedFavoriteListId == list.id, onClick = { selectedFavoriteListId = list.id; selectedCategoryId = null; showRecentOnly = false; searchQuery = "" }, onDelete = { scope.launch { iptvRepository.removeFavoriteList(list) } }) }
                                item { Text("Catégories", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                                items(categories) { category -> SidebarItem(text = category.category_name, isSelected = selectedCategoryId == category.category_id, onClick = { selectedCategoryId = category.category_id; selectedFavoriteListId = null; showRecentOnly = false; searchQuery = "" }) }
                            }
                        }
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                item { Text("Télévision en direct", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp)) }
                                items(channels) { channel -> 
                                    val isCurrent = playingChannel?.stream_id == channel.stream_id
                                    MainItem(
                                        title = channel.name, 
                                        iconUrl = channel.stream_icon, 
                                        isPlaying = isCurrent, 
                                        modifier = if(isCurrent) Modifier.focusRequester(listFocusRequester) else Modifier,
                                        onClick = { 
                                            if (exoPlayer != null) {
                                                playingChannel = channel
                                                navChannelList = channels.toList()
                                                exoPlayer?.let { 
                                                    val mediaItems = channels.map { ch ->
                                                        val meta = androidx.media3.common.MediaMetadata.Builder().setTitle(ch.name).setArtworkUri(ch.stream_icon?.let { Uri.parse(it) }).build()
                                                        androidx.media3.common.MediaItem.Builder().setUri("$baseUrl/live/$user/$pass/${ch.stream_id}.ts").setMediaMetadata(meta).setMediaId(ch.stream_id.toString()).build()
                                                    }
                                                    it.setMediaItems(mediaItems, channels.indexOfFirst { it.stream_id == channel.stream_id }, 0L)
                                                    it.prepare()
                                                    it.play() 
                                                }
                                                isFullScreenPlayer = true
                                                scope.launch { iptvRepository.addToRecents(channel.stream_id) } 
                                            }
                                        }, onAddFavorite = { channelToFavorite = channel }
                                    ) 
                                }
                            }
                        }
                    }
                }
            }
            if (isFullScreenPlayer && playingChannel != null && exoPlayer != null) {
                VideoPlayerView(
                    exoPlayer = exoPlayer!!,
                    onBack = { isFullScreenPlayer = false },
                    channel = playingChannel
                )
            }

            if (showAddListDialog) {
                var name by remember { mutableStateOf("") }
                AlertDialog(onDismissRequest = { showAddListDialog = false }, title = { Text("Nouvelle Liste") }, text = { TextField(value = name, onValueChange = { name = it }, placeholder = { Text("Nom de la liste") }) }, confirmButton = { TextButton(onClick = { if(name.isNotBlank()) scope.launch { iptvRepository.addFavoriteList(name) }; showAddListDialog = false }) { Text("Ajouter") } }, dismissButton = { TextButton(onClick = { showAddListDialog = false }) { Text("Annuler") } })
            }
            if (channelToFavorite != null) GenericFavoriteDialog(title = "Ajouter aux favoris", items = favoriteLists, getName = { it.name }, getId = { it.id }, onDismiss = { channelToFavorite = null }, onToggle = { listId -> scope.launch { iptvRepository.toggleChannelFavorite(channelToFavorite!!.stream_id, listId) } }, selectedIdsProvider = { iptvRepository.getListIdsForChannel(channelToFavorite!!.stream_id) })
        }
    }
}

@Composable
fun SidebarItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.clickable { onClick() },
        border = if (isFocused) BorderStroke(3.dp, Color.Yellow) else if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if(icon != null) { Icon(icon, null, tint = if(isSelected || isFocused) MaterialTheme.colorScheme.primary else Color.Gray); Spacer(Modifier.width(12.dp)) }
                Text(text, color = if(isSelected || isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            if(onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
fun MainItem(title: String, iconUrl: String?, isPlaying: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onAddFavorite: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    var isFavFocused by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(
            modifier = Modifier.weight(0.833f).fillMaxHeight().onFocusChanged { isFocused = it.isFocused }.clickable { onClick() },
            border = if (isFocused) BorderStroke(4.dp, Color.Yellow) else if (isPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            colors = CardDefaults.cardColors(containerColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else if (isPlaying) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(56.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, color = if(isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onAddFavorite, modifier = Modifier.weight(0.083f).fillMaxHeight().onFocusChanged { isFavFocused = it.isFocused }.background(if(isFavFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)) { Icon(Icons.Default.Add, null, tint = if(isFavFocused) Color.Black else Color.White) }
        Spacer(Modifier.weight(0.084f))
    }
}

@Composable
fun <T> GenericFavoriteDialog(title: String, items: List<T>, getName: (T) -> String, getId: (T) -> Int, onDismiss: () -> Unit, onToggle: (Int) -> Unit, selectedIdsProvider: suspend () -> List<Int>) {
    val selectedIds = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) { selectedIds.clear(); selectedIds.addAll(selectedIdsProvider()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { 
        if (items.isEmpty()) Text("Aucune liste.") 
        else LazyColumn { items(items) { list -> Row(modifier = Modifier.fillMaxWidth().clickable { onToggle(getId(list)); if(selectedIds.contains(getId(list))) selectedIds.remove(getId(list)) else selectedIds.add(getId(list)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selectedIds.contains(getId(list)), onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text(getName(list)) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

@Composable
fun VideoPlayerView(exoPlayer: androidx.media3.common.Player, onBack: () -> Unit, channel: ChannelEntity? = null) {
    val context = LocalContext.current
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(800); try { backFocusRequester.requestFocus() } catch(e:Exception){} }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { player = exoPlayer; useController = true; keepScreenOn = true } },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).focusRequester(backFocusRequester)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
        }
    }
}
