package com.kamel.cccamscrapper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamel.cccamscrapper.parser.CccamServer
import com.kamel.cccamscrapper.parser.ClineParser
import com.kamel.cccamscrapper.parser.ParseIssue
import com.kamel.cccamscrapper.receiver.GeantReceiverClient
import com.kamel.cccamscrapper.receiver.ReceiverProbeResult
import com.kamel.cccamscrapper.receiver.RemoteKeyMode
import com.kamel.cccamscrapper.tester.CccamTester
import com.kamel.cccamscrapper.tester.ServerState
import com.kamel.cccamscrapper.tester.ServerTestResult
import com.kamel.cccamscrapper.ui.theme.Background
import com.kamel.cccamscrapper.ui.theme.Border
import com.kamel.cccamscrapper.ui.theme.CccamScrapperTheme
import com.kamel.cccamscrapper.ui.theme.Error
import com.kamel.cccamscrapper.ui.theme.Primary
import com.kamel.cccamscrapper.ui.theme.Secondary
import com.kamel.cccamscrapper.ui.theme.Success
import com.kamel.cccamscrapper.ui.theme.Surface
import com.kamel.cccamscrapper.ui.theme.SurfaceRaised
import com.kamel.cccamscrapper.ui.theme.TextPrimary
import com.kamel.cccamscrapper.ui.theme.TextSecondary
import com.kamel.cccamscrapper.ui.theme.Warning
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CccamScrapperTheme {
                ParserScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserScreen() {
    var input by remember { mutableStateOf("") }
    val result = remember(input) { ClineParser.parse(input) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tester = remember { CccamTester() }
    val receiverClient = remember { GeantReceiverClient() }
    val testResults = remember { mutableStateMapOf<String, ServerTestResult>() }
    val isTestingAll = result.servers.any { testResults[it.id()]?.state == ServerState.TESTING }
    var receiverIp by remember { mutableStateOf(GeantReceiverClient.DEFAULT_IP) }
    var receiverProbe by remember { mutableStateOf<ReceiverProbeResult?>(null) }
    var isReceiverTesting by remember { mutableStateOf(false) }
    var robotStatus by remember { mutableStateOf("Pret") }
    var keyMode by remember { mutableStateOf(RemoteKeyMode.KEY_VAL) }

    LaunchedEffect(input) {
        testResults.clear()
    }

    Scaffold(containerColor = Background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Primary, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Cccam Scrapper", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Parser de C-lines", color = TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Text(
                        text = "Colle une ou plusieurs lignes C: host port user pass, puis lance le test de connexion.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    placeholder = {
                        Text("C: example.com 12000 user pass\nhost2.net 13000 user2 pass2", color = TextSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Primary,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Border,
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactButton(
                        text = "Vider",
                        icon = Icons.Default.Delete,
                        onClick = { input = "" },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        containerColor = SurfaceRaised
                    )

                    CompactButton(
                        text = "Copier",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            copyText(context, result.servers.joinToString("\n") { it.normalizedLine })
                            Toast.makeText(context, "C-lines copiees", Toast.LENGTH_SHORT).show()
                        },
                        enabled = result.servers.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        containerColor = Primary,
                        contentColor = Background
                    )

                    CompactButton(
                        text = "Tester",
                        icon = Icons.Default.NetworkCheck,
                        onClick = {
                            scope.launch {
                                result.servers.forEach { server ->
                                    testResults[server.id()] = ServerTestResult(ServerState.TESTING, message = "Test en cours")
                                }
                                result.servers.map { server ->
                                    async {
                                        val testResult = tester.test(server)
                                        testResults[server.id()] = testResult
                                    }
                                }.awaitAll()
                            }
                        },
                        enabled = result.servers.isNotEmpty() && !isTestingAll,
                        modifier = Modifier.weight(1f),
                        containerColor = Secondary,
                        contentColor = Background
                    )
                }
            }

            item {
                SummaryCard(
                    serverCount = result.servers.size,
                    issueCount = result.issues.size,
                    activeCount = result.servers.count { testResults[it.id()]?.state == ServerState.ACTIVE },
                    validCount = result.servers.count { testResults[it.id()]?.state == ServerState.VALID },
                    inactiveCount = result.servers.count { testResults[it.id()]?.state == ServerState.INACTIVE }
                )
            }

            item {
                ReceiverCard(
                    ipAddress = receiverIp,
                    onIpChange = { receiverIp = it },
                    probeResult = receiverProbe,
                    isTesting = isReceiverTesting,
                    cccamConfig = result.servers.joinToString("\n") { it.normalizedLine },
                    onProbe = {
                        scope.launch {
                            isReceiverTesting = true
                            receiverProbe = receiverClient.probe(receiverIp.trim())
                            isReceiverTesting = false
                        }
                    },
                    onCopyConfig = {
                        copyText(context, result.servers.joinToString("\n") { it.normalizedLine })
                        Toast.makeText(context, "Config CCcam copiee", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                RobotCard(
                    status = robotStatus,
                    keyMode = keyMode,
                    onModeChange = { mode ->
                        keyMode = mode
                        scope.launch {
                            robotStatus = "Menu ${mode.label}..."
                            val ok = receiverClient.sendKey(receiverIp.trim(), GeantReceiverClient.KEY_MENU, mode)
                            robotStatus = if (ok) "Menu ${mode.label} envoye" else "Menu ${mode.label} echoue"
                        }
                    },
                    onKey = { label, keyCode ->
                        scope.launch {
                            robotStatus = "$label ${keyMode.label}..."
                            val ok = receiverClient.sendKey(receiverIp.trim(), keyCode, keyMode)
                            robotStatus = if (ok) "$label ${keyMode.label} envoye" else "$label ${keyMode.label} echoue"
                        }
                    },
                    onOpenMenu = {
                        scope.launch {
                            robotStatus = "Menu ${keyMode.label}..."
                            val ok = receiverClient.sendKey(receiverIp.trim(), GeantReceiverClient.KEY_MENU, keyMode)
                            robotStatus = if (ok) "Menu ${keyMode.label} envoye" else "Menu ${keyMode.label} echoue"
                        }
                    },
                    onProbePath = {
                        scope.launch {
                            robotStatus = "Sequence..."
                            val steps = listOf(
                                "Menu" to GeantReceiverClient.KEY_MENU,
                                "Bas" to GeantReceiverClient.KEY_DPAD_DOWN,
                                "Bas" to GeantReceiverClient.KEY_DPAD_DOWN,
                                "OK" to GeantReceiverClient.KEY_DPAD_CENTER
                            )
                            var allOk = true
                            for ((label, keyCode) in steps) {
                                robotStatus = "$label ${keyMode.label}"
                                allOk = receiverClient.sendKey(receiverIp.trim(), keyCode, keyMode) && allOk
                                delay(450)
                            }
                            robotStatus = if (allOk) "Sequence envoyee" else "Sequence incomplete"
                        }
                    }
                )
            }

            items(result.servers) { server ->
                ServerCard(
                    server = server,
                    testResult = testResults[server.id()],
                    onTest = {
                        scope.launch {
                            testResults[server.id()] = ServerTestResult(ServerState.TESTING, message = "Test en cours")
                            testResults[server.id()] = tester.test(server)
                        }
                    }
                )
            }

            items(result.issues) { issue ->
                IssueCard(issue)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverCard(
    ipAddress: String,
    onIpChange: (String) -> Unit,
    probeResult: ReceiverProbeResult?,
    isTesting: Boolean,
    cccamConfig: String,
    onProbe: () -> Unit,
    onCopyConfig: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, tint = Secondary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recepteur GEANT", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("GN-OTT 750 4K via TCP 20000", color = TextSecondary, fontSize = 13.sp)
                }
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Secondary)
                }
            }

            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Adresse IP", color = TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                    focusedBorderColor = Secondary,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactButton(
                    text = "Connecter",
                    icon = Icons.Default.SettingsInputAntenna,
                    onClick = onProbe,
                    enabled = ipAddress.isNotBlank() && !isTesting,
                    modifier = Modifier.weight(1f),
                    containerColor = Secondary,
                    contentColor = Background
                )

                CompactButton(
                    text = "Copier cfg",
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopyConfig,
                    enabled = cccamConfig.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    containerColor = SurfaceRaised
                )
            }

            probeResult?.let { result ->
                val color = if (result.connected) Success else Error
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(color)
                    Text(result.message, color = color, fontSize = 13.sp)
                }
                result.info?.let { info ->
                    Text("Serie: ${info.serialNumber} - ${info.channelCount} chaines", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Text(
                "Injection directe fichier non exposee par les ports ouverts; cette connexion prepare l'automatisation/protocole.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun RobotCard(
    status: String,
    keyMode: RemoteKeyMode,
    onModeChange: (RemoteKeyMode) -> Unit,
    onKey: (String, Int) -> Unit,
    onOpenMenu: () -> Unit,
    onProbePath: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Robot GEANT", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Mode ${keyMode.label} - $status", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RemoteKeyMode.values().forEach { mode ->
                    CompactButton(
                        text = "Menu ${mode.label}",
                        icon = Icons.Default.Menu,
                        onClick = { onModeChange(mode) },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        containerColor = if (mode == keyMode) Primary else SurfaceRaised,
                        contentColor = if (mode == keyMode) Background else TextPrimary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactButton(
                    text = "Menu",
                    icon = Icons.Default.Menu,
                    onClick = onOpenMenu,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = Primary,
                    contentColor = Background
                )
                CompactButton(
                    text = "Retour",
                    icon = Icons.Default.Reply,
                    onClick = { onKey("Retour", GeantReceiverClient.KEY_BACK) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = SurfaceRaised
                )
                CompactButton(
                    text = "OK",
                    icon = Icons.Default.PlayArrow,
                    onClick = { onKey("OK", GeantReceiverClient.KEY_DPAD_CENTER) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = Secondary,
                    contentColor = Background
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactButton(
                    text = "Haut",
                    icon = Icons.Default.KeyboardArrowUp,
                    onClick = { onKey("Haut", GeantReceiverClient.KEY_DPAD_UP) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = SurfaceRaised
                )
                CompactButton(
                    text = "Bas",
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = { onKey("Bas", GeantReceiverClient.KEY_DPAD_DOWN) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = SurfaceRaised
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactButton(
                    text = "Gauche",
                    icon = Icons.Default.KeyboardArrowLeft,
                    onClick = { onKey("Gauche", GeantReceiverClient.KEY_DPAD_LEFT) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = SurfaceRaised
                )
                CompactButton(
                    text = "Droite",
                    icon = Icons.Default.KeyboardArrowRight,
                    onClick = { onKey("Droite", GeantReceiverClient.KEY_DPAD_RIGHT) },
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    containerColor = SurfaceRaised
                )
            }

            CompactButton(
                text = "Essai chemin",
                icon = Icons.Default.SettingsInputAntenna,
                onClick = onProbePath,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                containerColor = SurfaceRaised
            )
        }
    }
}

@Composable
fun CompactButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color = TextPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Surface,
            disabledContentColor = TextSecondary
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SummaryCard(serverCount: Int, issueCount: Int, validCount: Int, activeCount: Int, inactiveCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$serverCount serveur(s) detecte(s)", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("$validCount valide(s), $activeCount port ouvert/refuse, $inactiveCount inactif(s), $issueCount ligne(s) a revoir", color = TextSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Secondary)
        }
    }
}

@Composable
fun ServerCard(server: CccamServer, testResult: ServerTestResult?, onTest: () -> Unit) {
    val context = LocalContext.current
    val state = testResult?.state ?: ServerState.UNTESTED
    val statusColor = when (state) {
        ServerState.VALID -> Success
        ServerState.ACTIVE -> Warning
        ServerState.INACTIVE -> Error
        ServerState.TESTING -> Secondary
        ServerState.UNTESTED -> TextSecondary
    }
    val statusText = when (state) {
        ServerState.VALID -> "Compte valide"
        ServerState.ACTIVE -> "Port ouvert"
        ServerState.INACTIVE -> "Inactif"
        ServerState.TESTING -> "Test..."
        ServerState.UNTESTED -> "Non teste"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${server.host}:${server.port}", color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("user: ${server.username}", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("pass: ${server.password}", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(statusColor)
                    Text(
                        text = statusLabel(statusText, testResult),
                        color = statusColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onTest, enabled = state != ServerState.TESTING) {
                if (state == ServerState.TESTING) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Secondary)
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = "Tester", tint = Secondary)
                }
            }
            IconButton(onClick = {
                copyText(context, server.normalizedLine)
                Toast.makeText(context, "Serveur copie", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = Primary)
            }
        }
    }
}

@Composable
fun StatusDot(color: Color) {
    Card(
        modifier = Modifier.size(8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(4.dp)
    ) {}
}

private fun statusLabel(label: String, result: ServerTestResult?): String {
    val latency = result?.latencyMs?.let { " - ${it}ms" }.orEmpty()
    val message = result?.message?.let { " - $it" }.orEmpty()
    return "$label$latency$message"
}

@Composable
fun IssueCard(issue: ParseIssue) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Warning),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Warning)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(issue.reason, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(issue.raw, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun copyText(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("cccam", value))
}

private fun CccamServer.id(): String = "$host:$port:$username:$password"
