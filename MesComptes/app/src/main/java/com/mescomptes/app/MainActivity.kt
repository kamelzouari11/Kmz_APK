package com.mescomptes.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mescomptes.app.data.AccountEntity
import com.mescomptes.app.data.MovementEntity
import com.mescomptes.app.data.OrderType
import com.mescomptes.app.data.calculatePositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF172522)
private val Forest = Color(0xFF1A6B5A)
private val Mist = Color(0xFFF7F8F6)
private val Mint = Color(0xFFE2F0E9)
private val Gold = Color(0xFFB88636)
private val Muted = Color(0xFF71807A)
private val Danger = Color(0xFFC35A30)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MesComptesTheme { MesComptesApp() } }
    }
}

@Composable
private fun MesComptesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest, onPrimary = Color.White, background = Mist,
            surface = Color.White, onSurface = Ink, error = Danger,
        ),
        content = content,
    )
}

private enum class BackupAction { EXPORT, IMPORT }

@Composable
private fun MesComptesApp(vm: MesComptesViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var accountEditor by remember { mutableStateOf<AccountEntity?>(null) }
    var creatingAccount by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var movementEditor by remember { mutableStateOf<MovementEntity?>(null) }
    var creatingMovement by remember { mutableStateOf(false) }
    var movementError by remember { mutableStateOf<String?>(null) }
    var presentation by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteMovement by remember { mutableStateOf<MovementEntity?>(null) }
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportPassword by remember { mutableStateOf<CharArray?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                    ?: error("Impossible d'ouvrir le fichier.")
            }.onSuccess { snackbar.showSnackbar("Sauvegarde chiffrée créée.") }
                .onFailure { snackbar.showSnackbar(it.message ?: "Échec de la sauvegarde.") }
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingImportPassword
        pendingImportPassword = null
        if (uri != null && password != null) scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Impossible de lire le fichier.")
                }
                vm.restoreBackup(bytes, password)
            }.onSuccess { snackbar.showSnackbar("Données restaurées avec succès.") }
                .onFailure { snackbar.showSnackbar(it.message ?: "Échec de la restauration.") }
        } else password?.fill('\u0000')
    }

    Scaffold(
        containerColor = Mist,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    "Comptes" to Icons.Default.AccountBalance,
                    "Journal" to Icons.AutoMirrored.Filled.List,
                    "Portefeuille" to Icons.Default.PieChart,
                ).forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index, onClick = { selectedTab = index },
                        icon = { Icon(item.second, null) }, label = { Text(item.first) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> AccountsScreen(
                modifier = Modifier.padding(padding), accounts = accounts, movements = movements,
                onAdd = { creatingAccount = true }, onEdit = { accountEditor = it },
                onDelete = { deleteAccount = it }, onPresent = { presentation = it },
                onExport = { backupAction = BackupAction.EXPORT }, onImport = { backupAction = BackupAction.IMPORT },
            )
            1 -> JournalScreen(
                modifier = Modifier.padding(padding), movements = movements, accounts = accounts,
                onAdd = { creatingMovement = true }, onEdit = { movementEditor = it }, onDelete = { deleteMovement = it },
            )
            else -> PortfolioScreen(Modifier.padding(padding), movements, accounts)
        }
    }

    if (creatingAccount || accountEditor != null) AccountEditorDialog(accountEditor, accountError) { result ->
        if (result == null) { creatingAccount = false; accountEditor = null; accountError = null }
        else scope.launch {
            vm.saveAccount(result)?.let { accountError = it } ?: run {
                creatingAccount = false; accountEditor = null; accountError = null
            }
        }
    }
    if (creatingMovement || movementEditor != null) MovementEditorDialog(movementEditor, accounts, movementError) { result ->
        if (result == null) { creatingMovement = false; movementEditor = null; movementError = null }
        else scope.launch {
            vm.saveMovement(result)?.let { movementError = it } ?: run {
                creatingMovement = false; movementEditor = null; movementError = null
            }
        }
    }
    deleteAccount?.let { account ->
        ConfirmDeleteDialog(
            title = "Supprimer ce compte ?",
            text = "Toutes les opérations et positions liées seront également supprimées.",
            onDismiss = { deleteAccount = null },
        ) { scope.launch { vm.deleteAccount(account); deleteAccount = null; snackbar.showSnackbar("Compte supprimé.") } }
    }
    deleteMovement?.let { movement ->
        ConfirmDeleteDialog(
            title = "Supprimer cette opération ?", text = "Le portefeuille sera recalculé immédiatement.",
            onDismiss = { deleteMovement = null },
        ) { scope.launch {
            val error = vm.deleteMovement(movement)
            deleteMovement = null
            snackbar.showSnackbar(error ?: "Opération supprimée.")
        } }
    }
    backupAction?.let { action ->
        PasswordDialog(action, onDismiss = { backupAction = null }) { password ->
            backupAction = null
            if (action == BackupAction.EXPORT) scope.launch {
                runCatching { vm.createBackup(password.toCharArray()) }
                    .onSuccess { pendingExport = it; createDocument.launch("MesComptes-${LocalDate.now()}.mcp") }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Échec de la sauvegarde.") }
            } else {
                pendingImportPassword = password.toCharArray()
                openDocument.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
            }
        }
    }
    presentation?.let { FullScreenAccount(it) { presentation = null } }
}

@Composable
private fun Header(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
        actions()
    }
}

@Composable
private fun AccountsScreen(
    modifier: Modifier,
    accounts: List<AccountEntity>,
    movements: List<MovementEntity>,
    onAdd: () -> Unit,
    onEdit: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit,
    onPresent: (AccountEntity) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var backupMenu by remember { mutableStateOf(false) }
    val positions = remember(movements) { calculatePositions(movements) }
    Column(modifier.fillMaxSize()) {
        Header("Mes comptes", "Vos coordonnées, à portée de main") {
            Box {
                IconButton(onClick = { backupMenu = true }) { Icon(Icons.Default.MoreVert, "Sauvegarde") }
                DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Sauvegarder (chiffré)") }, leadingIcon = { Icon(Icons.Default.Backup, null) },
                        onClick = { backupMenu = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text("Restaurer") }, leadingIcon = { Icon(Icons.Default.Restore, null) },
                        onClick = { backupMenu = false; onImport() },
                    )
                }
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Ajouter un compte", tint = Forest) }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DashboardCard(accounts.size, movements.firstOrNull()?.operationDate, positions.size) }
            if (accounts.isEmpty()) item { EmptyState(Icons.Default.AccountBalance, "Aucun compte", "Ajoutez votre premier compte avec le bouton +.") }
            items(accounts, key = { it.id }) { account -> AccountCard(account, onEdit, onDelete, onPresent) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun DashboardCard(accountCount: Int, latestMovementDate: Long?, positionCount: Int) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(accountCount.toString(), "Comptes")
            Metric(latestMovementDate?.let(::formatShortDate) ?: "—", "Dernière op.")
            Metric(positionCount.toString(), "Positions")
        }
    }
}

@Composable
private fun RowScope.Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFB6C9C0), fontSize = 12.sp)
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    onEdit: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit,
    onPresent: (AccountEntity) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(Mint, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalance, null, tint = Forest, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.holder, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(account.bank, color = Forest, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onEdit(account) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Edit, "Modifier", tint = Muted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onDelete(account) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Supprimer", tint = Danger, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoLine("CIN", account.cin)
            InfoLine("RIB", account.rib, maxLines = 2)
            InfoLine("Cpte", account.number)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onPresent(account) }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Visibility, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Présenter le compte", maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, maxLines: Int = 1) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color(0xFF84908B), fontSize = 13.sp, modifier = Modifier.width(62.dp))
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun JournalScreen(
    modifier: Modifier,
    movements: List<MovementEntity>,
    accounts: List<AccountEntity>,
    onAdd: () -> Unit,
    onEdit: (MovementEntity) -> Unit,
    onDelete: (MovementEntity) -> Unit,
) {
    var accountFilter by remember { mutableStateOf<Long?>(null) }
    var filterMenu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(accounts, accountFilter) {
        if (accountFilter != null && accounts.none { it.id == accountFilter }) accountFilter = null
    }
    val selectedAccount = accounts.firstOrNull { it.id == accountFilter }
    val filtered = movements.filter {
        (accountFilter == null || it.accountId == accountFilter) &&
            (search.isBlank() || it.title.contains(search.trim(), ignoreCase = true))
    }
    Column(modifier.fillMaxSize()) {
        Header("Journal", "Opérations par chronologie décroissante") {
            IconButton(enabled = accounts.isNotEmpty(), onClick = onAdd) { Icon(Icons.Default.Add, "Ajouter une opération", tint = Forest) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Button(
                    onClick = { filterMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Forest),
                ) {
                    Text(
                        selectedAccount?.movementDisplayName ?: "Tous les comptes",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(
                    expanded = filterMenu,
                    onDismissRequest = { filterMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    DropdownMenuItem(text = { Text("Tous les comptes") }, onClick = { accountFilter = null; filterMenu = false })
                    accounts.forEach { account -> DropdownMenuItem(
                        text = { Text(account.movementDisplayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { accountFilter = account.id; filterMenu = false },
                    ) }
                }
            }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            label = { Text("Rechercher un titre") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Effacer") } },
        )
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) item { EmptyState(Icons.Default.SearchOff, "Aucune opération", "Ajoutez une opération ou changez le filtre.") }
            items(filtered, key = { it.id }) { movement ->
                MovementRow(movement, accounts.firstOrNull { it.id == movement.accountId }, onEdit, onDelete)
            }
        }
    }
}

@Composable
private fun MovementRow(
    movement: MovementEntity,
    account: AccountEntity?,
    onEdit: (MovementEntity) -> Unit,
    onDelete: (MovementEntity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(movement) },
        shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val positive = movement.orderType.addsToPosition
            Column(Modifier.weight(1f)) {
                Text(
                    movement.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(movement.orderType.journalLabel)
                        append(" · ")
                        append(formatDate(movement.operationDate))
                        account?.let { append(" · ").append(it.movementDisplayName) }
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${movement.quantity} titres", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                Text(
                    movement.unitPrice?.let { "PU ${formatPrice(it)}" } ?: "PU —",
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(24.dp).background(if (positive) Mint else Color(0xFFFFE9DC), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (positive) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        if (positive) "Entrée de titres" else "Sortie de titres",
                        tint = if (positive) Forest else Danger,
                        modifier = Modifier.size(17.dp),
                    )
                }
                IconButton(onClick = { onDelete(movement) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Supprimer", tint = Danger, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun PortfolioScreen(modifier: Modifier, movements: List<MovementEntity>, accounts: List<AccountEntity>) {
    val positions = remember(movements) { calculatePositions(movements) }
    Column(modifier.fillMaxSize()) {
        Header("Portefeuille", "Quantités restantes par compte")
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (positions.isEmpty()) item { EmptyState(Icons.Default.PieChart, "Portefeuille vide", "Les achats et ventes déterminent automatiquement vos positions.") }
            accounts.forEach { account ->
                val accountPositions = positions.filter { it.accountId == account.id }
                if (accountPositions.isNotEmpty()) {
                    item(key = "account-${account.id}") {
                        val knownValues = accountPositions.mapNotNull { it.indicativeValue }
                        val accountTotal = knownValues.fold(BigDecimal.ZERO) { total, value -> total.add(value) }
                        val missingPrices = accountPositions.size - knownValues.size
                        Row(
                            Modifier.fillMaxWidth().padding(top = 7.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(account.holder, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(account.displayName, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total indicatif", color = Muted, fontSize = 11.sp)
                                Text(
                                    if (knownValues.isEmpty()) "—" else "≈ ${formatIndicativeAmount(accountTotal)} DT",
                                    color = Forest,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                )
                                if (missingPrices > 0) Text("$missingPrices sans prix", color = Gold, fontSize = 10.sp)
                            }
                        }
                    }
                    items(accountPositions, key = { "${it.accountId}-${it.title.lowercase()}" }) { position ->
                        Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = Gold); Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(position.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        position.lastKnownUnitPrice?.let { "Der PU ${formatPrice(it.toPlainString())}" } ?: "Der PU inconnu",
                                        color = Muted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Row {
                                        Text(
                                            position.quantity.toString(),
                                            color = Forest,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            "titres",
                                            color = Forest,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                    }
                                    Text(
                                        position.indicativeValue?.let { "≈ ${formatIndicativeAmount(it)} DT" } ?: "Valeur —",
                                        color = Muted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color(0xFF9DABA5), modifier = Modifier.size(40.dp)); Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AccountEditorDialog(account: AccountEntity?, error: String?, onResult: (AccountEntity?) -> Unit) {
    var holder by remember(account) { mutableStateOf(account?.holder.orEmpty()) }
    var bank by remember(account) { mutableStateOf(account?.bank.orEmpty()) }
    var cin by remember(account) { mutableStateOf(account?.cin.orEmpty()) }
    var rib by remember(account) { mutableStateOf(account?.rib.orEmpty()) }
    var number by remember(account) { mutableStateOf(account?.number.orEmpty()) }
    AlertDialog(
        onDismissRequest = { onResult(null) }, title = { Text(if (account == null) "Nouveau compte" else "Modifier le compte") },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(holder, { holder = it }, label = { Text("Titulaire") }, singleLine = true)
                OutlinedTextField(cin, { cin = it }, label = { Text("CIN") }, singleLine = true)
                OutlinedTextField(bank, { bank = it }, label = { Text("Banque") }, singleLine = true)
                OutlinedTextField(rib, { rib = it }, label = { Text("RIB") }, singleLine = true)
                OutlinedTextField(number, { number = it }, label = { Text("Numéro de compte") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { onResult(AccountEntity(account?.id ?: 0, holder, cin, bank, rib, number)) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Annuler") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementEditorDialog(
    movement: MovementEntity?, accounts: List<AccountEntity>, error: String?, onResult: (MovementEntity?) -> Unit,
) {
    var accountId by remember(movement, accounts) { mutableLongStateOf(movement?.accountId ?: accounts.firstOrNull()?.id ?: 0) }
    var date by remember(movement) { mutableLongStateOf(movement?.operationDate ?: LocalDate.now().toEpochDay()) }
    var title by remember(movement) { mutableStateOf(movement?.title.orEmpty()) }
    var order by remember(movement) { mutableStateOf(movement?.orderType ?: OrderType.ACHAT) }
    var quantity by remember(movement) { mutableStateOf(movement?.quantity?.toString().orEmpty()) }
    var price by remember(movement) { mutableStateOf(movement?.unitPrice?.replace('.', ',').orEmpty()) }
    var accountMenu by remember { mutableStateOf(false) }
    var orderMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(if (movement == null) "Nouvelle opération" else "Modifier l'opération") },
        text = {
            Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ExposedDropdownMenuBox(expanded = accountMenu, onExpandedChange = { accountMenu = it }) {
                    OutlinedTextField(
                        value = selectedAccount?.movementDisplayName ?: "Sélectionner un compte", onValueChange = {},
                        label = { Text("Compte") }, readOnly = true, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        accounts.forEach { account -> DropdownMenuItem(
                            text = { Text(account.movementDisplayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { accountId = account.id; accountMenu = false },
                        ) }
                    }
                }
                Button(
                    onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Forest),
                ) { Text("Date : ${formatDate(date)}") }
                OutlinedTextField(title, { title = it }, label = { Text("Titre / SICAV") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = orderMenu, onExpandedChange = { orderMenu = it }) {
                    OutlinedTextField(
                        value = order.label, onValueChange = {}, label = { Text("Ordre") }, readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                        OrderType.entries.forEach { type -> DropdownMenuItem(
                            text = { Text(type.label) }, onClick = { order = type; orderMenu = false },
                        ) }
                    }
                }
                OutlinedTextField(
                    quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Quantité") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                OutlinedTextField(
                    price, { price = it }, label = { Text("Prix unitaire (facultatif)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            onResult(MovementEntity(
                id = movement?.id ?: 0, accountId = accountId, operationDate = date, title = title,
                orderType = order, quantity = quantity.toIntOrNull() ?: 0, unitPrice = price.takeIf(String::isNotBlank),
            ))
        }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Annuler") } },
    )
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { date = it / 86_400_000L }; showDatePicker = false
            }) { Text("Valider") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun PasswordDialog(action: BackupAction, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Shield, null, tint = Forest) },
        title = { Text(if (action == BackupAction.EXPORT) "Sauvegarde chiffrée" else "Restaurer une sauvegarde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (action == BackupAction.EXPORT)
                    "Choisissez un mot de passe. Il sera indispensable pour restaurer le fichier."
                else "Attention : la restauration remplacera toutes les données actuelles.")
                OutlinedTextField(
                    password, { password = it }, label = { Text("Mot de passe (6 caractères minimum)") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(enabled = password.length >= 6, onClick = { onConfirm(password) }) {
            Text(if (action == BackupAction.EXPORT) "Créer" else "Choisir le fichier")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun FullScreenAccount(account: AccountEntity, close: () -> Unit) {
    BackHandler(onBack = close)
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previousBrightness = window?.attributes?.screenBrightness
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.attributes = it.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.attributes = it.attributes.apply { screenBrightness = previousBrightness ?: -1f }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = close) { Icon(Icons.Default.Close, "Fermer", tint = Color.White) }
            }
            Text("COORDONNÉES BANCAIRES", color = Color(0xFF8CC8B4), fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(12.dp)); Text(account.holder, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(account.bank, color = Color(0xFFB6C9C0), fontSize = 16.sp); Spacer(Modifier.height(38.dp))
            PresentationLine("CIN", account.cin); PresentationLine("RIB", account.rib); PresentationLine("N° COMPTE", account.number)
            Spacer(Modifier.height(42.dp)); Text("Appareil personnel · MesComptes", color = Color(0xFF71847B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PresentationLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 13.dp)) {
        Text(label, color = Color(0xFF8CC8B4), fontSize = 11.sp, letterSpacing = 1.sp)
        Text(value.ifBlank { "—" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH)
private val shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH)
private fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormatter)
private fun formatShortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)
private fun formatPrice(value: String): String = runCatching { formatNumber(BigDecimal(value), 3) }.getOrDefault(value)
private fun formatIndicativeAmount(value: BigDecimal): String = formatNumber(value, 0)
private fun formatNumber(value: BigDecimal, fractionDigits: Int): String = NumberFormat.getNumberInstance(Locale.FRANCE).run {
    minimumFractionDigits = fractionDigits
    maximumFractionDigits = fractionDigits
    roundingMode = RoundingMode.HALF_UP
    format(value)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
