package com.kamel.iptvscrapper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import com.kamel.iptvscrapper.data.IptvRepository
import com.kamel.iptvscrapper.data.api.IptvCategory
import com.kamel.iptvscrapper.data.api.IptvChannel
import com.kamel.iptvscrapper.data.local.AppDatabase
import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import com.kamel.iptvscrapper.data.scraper.IptvScraper
import com.kamel.iptvscrapper.data.tester.IptvTester
import com.kamel.iptvscrapper.ui.theme.*
import com.kamel.iptvscrapper.ui.viewmodel.MainViewModel
import com.kamel.iptvscrapper.ui.viewmodel.ScreenState

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "iptv-db")
            .fallbackToDestructiveMigration()
            .build()
        
        val repository = IptvRepository(db.linkDao(), IptvScraper(), IptvTester())
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[MainViewModel::class.java]

        setContent {
            IPTVScrapperTheme {
                AppContent(viewModel)
            }
        }
    }
}

@Composable
fun AppContent(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    BackHandler(enabled = uiState.screenState != ScreenState.HOME) {
        viewModel.goBack()
    }

    Crossfade(targetState = uiState.screenState, label = "ScreenTransition") { state ->
        when (state) {
            ScreenState.HOME -> MainScreen(viewModel)
            ScreenState.CATEGORIES -> CategoryScreen(viewModel)
            ScreenState.CHANNELS -> ChannelScreen(viewModel)
            ScreenState.PLAYER -> PlayerScreen(viewModel)
        }
    }
}

@Composable
fun CategoryScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            HeaderWithBack("Categories", viewModel)
        },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                LoadingBox()
            } else if (uiState.categories.isEmpty()) {
                EmptyState("No categories found on this server")
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(uiState.categories) { category ->
                        ItemCard(category.name, Icons.AutoMirrored.Filled.List) {
                            viewModel.selectCategory(category)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            HeaderWithBack(uiState.selectedCategory?.name ?: "Channels", viewModel)
        },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                LoadingBox()
            } else if (uiState.channels.isEmpty()) {
                EmptyState("No channels found in this category")
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(uiState.channels) { channel ->
                        ChannelItemCard(channel) {
                            viewModel.playChannel(channel)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItemCard(channel: IptvChannel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(channel.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (!channel.nowPlaying.isNullOrBlank()) {
                    Text(
                        channel.nowPlaying, 
                        color = TextSecondary, 
                        fontSize = 11.sp, 
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = TextSecondary, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun PlayerScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val userAgent = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        // Optimized LoadControl for IPTV (from SimpleIPTV)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000, // minBufferMs: Faster start
                50_000, // maxBufferMs: Stable cache
                1_500,  // bufferForPlaybackMs
                3_000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val cause = error.cause
                        val message = if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                            "HTTP Error: ${cause.responseCode}"
                        } else if (cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException) {
                            "HTTP Source Error: ${cause.javaClass.simpleName}"
                        } else {
                            error.localizedMessage ?: "Source Error"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                })
            }
    }

    LaunchedEffect(uiState.currentStreamUrl) {
        uiState.currentStreamUrl?.let { url ->
            if (url.isNotBlank()) {
                val link = uiState.selectedLink
                val headers = mutableMapOf<String, String>()
                
                if (link?.type == "STALKER") {
                    headers["Cookie"] = "mac=${link.mac}; stb_lang=en; timezone=Europe/Paris;"
                }
                
                headers["Connection"] = "keep-alive"
                headers["Accept"] = "*/*"

                // IMPORTANT: User-Agent MUST match the one used during scraping/handshake
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent) 
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                
                val mediaSourceFactory = DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(httpDataSourceFactory)
                
                exoPlayer.stop()
                exoPlayer.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(url)))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { 
            exoPlayer.stop()
            exoPlayer.release() 
        }
    }

    Scaffold(
        topBar = {
            HeaderWithBack(uiState.selectedChannel?.name ?: "Player", viewModel)
        },
        containerColor = Background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Player View (Fixed Aspect Ratio for Portrait)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black)) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Primary)
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // Copy Parameters Section
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "STREAM PARAMETERS",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val link = uiState.selectedLink
            if (link != null) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CopyableItem("URL", link.url, context)
                        if (link.type == "XTREAM") {
                            CopyableItem("User", link.username ?: "", context)
                            CopyableItem("Pass", link.password ?: "", context)
                        } else if (link.type == "STALKER") {
                            CopyableItem("MAC", link.mac ?: "", context)
                        }
                        uiState.currentStreamUrl?.let {
                            CopyableItem("Direct Stream", it, context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CopyableItem(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun HeaderWithBack(title: String, viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Primary,
            modifier = Modifier.clickable { viewModel.goBack() }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ItemCard(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(name, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Primary)
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manual Import", color = TextPrimary)
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        placeholder = { Text("Paste IPTV data here (Portal URL, Username, Password...)\n\nExample:\nHost: http://server.com\nUser: myuser\nPass: mypass", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Primary,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { importText = "" }) {
                            Text("Clear", color = Error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val item = clipboard.primaryClip?.getItemAt(0)
                            item?.text?.let { importText = it.toString() }
                        }) {
                            Text("Paste from Clipboard", color = Primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.isBlank()) return@Button
                        viewModel.importManualText(importText) { count, error ->
                            if (error != null) {
                                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Imported $count links", Toast.LENGTH_SHORT).show()
                                showImportDialog = false
                                importText = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Import Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Surface
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(16.dp)
            ) {
                Text(
                    "IPTV SCRAPPER PRO",
                    color = Primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActionButton(
                        text = "Scrape Blog",
                        icon = Icons.Default.Search,
                        isLoading = uiState.isScraping,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.scrapeLinks { count ->
                            Toast.makeText(context, "Found $count new links", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ActionButton(
                        text = "Paste Text",
                        icon = Icons.Default.Add,
                        modifier = Modifier.weight(1f)
                    ) {
                        showImportDialog = true
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActionButton(
                        text = "Test All",
                        icon = Icons.Default.PlayArrow,
                        isLoading = uiState.isTesting,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.testAllLinks()
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ActionButton(
                        text = "Clear",
                        icon = Icons.Default.Delete,
                        modifier = Modifier.weight(1f),
                        containerColor = Error.copy(alpha = 0.2f),
                        contentColor = Error
                    ) {
                        viewModel.clearHistory()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ActionButton(
                        text = if (uiState.isFilterWorking) "Show All" else "Filter Working",
                        icon = if (uiState.isFilterWorking) Icons.Default.List else Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f),
                        containerColor = if (uiState.isFilterWorking) Success.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                        contentColor = if (uiState.isFilterWorking) Success else TextPrimary
                    ) {
                        viewModel.toggleFilterWorking()
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ActionButton(
                        text = "Copy Working",
                        icon = Icons.Default.ContentCopy,
                        modifier = Modifier.weight(1f)
                    ) {
                        val text = viewModel.getWorkingLinksText()
                        if (text.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("IPTV Links", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied all working parameters!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No working links to copy", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            StatsHeader(uiState.links)
            
            val displayLinks = if (uiState.isFilterWorking) {
                uiState.links.filter { it.status == "WORKING" }
            } else {
                uiState.links
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayLinks) { link ->
                    LinkCard(link) {
                        if (link.status == "WORKING") {
                            viewModel.browseLink(link)
                        } else {
                            Toast.makeText(context, "Please test this link first", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean = false,
    containerColor: Color = Color.White.copy(alpha = 0.05f),
    contentColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun StatsHeader(links: List<LinkEntity>) {
    val working = links.count { it.status == "WORKING" }
    val pending = links.count { it.status == "PENDING" }
    val dead = links.count { it.status == "DEAD" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatItem("Working", working.toString(), Success)
        StatItem("Pending", pending.toString(), Primary)
        StatItem("Dead", dead.toString(), Error)
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
fun LinkCard(link: LinkEntity, onClick: () -> Unit) {
    val statusColor = when (link.status) {
        "WORKING" -> Success
        "DEAD" -> Error
        else -> Primary
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(GradientPrimary),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(link.type.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        link.url,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        link.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Spacer(modifier = Modifier.height(8.dp))

            // Show credentials based on type
            when (link.type) {
                "XTREAM" -> {
                    CredentialItem(Icons.Default.Person, "User: ${link.username}")
                    CredentialItem(Icons.Default.Lock, "Pass: ${link.password}")
                }
                "STALKER" -> {
                    CredentialItem(Icons.Default.Settings, "MAC: ${link.mac}")
                }
                "M3U" -> {
                    CredentialItem(Icons.AutoMirrored.Filled.List, "M3U Playlist")
                }
            }

            if (link.latency > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Latency: ${link.latency}ms", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun CredentialItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp)
    }
}
