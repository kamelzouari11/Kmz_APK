package com.example.myiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiptv.data.EpgProgram
import com.example.myiptv.data.FavoriteGroup
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.data.XtreamProfile
import com.example.myiptv.ui.theme.MyIptvPalette
import java.text.DateFormat
import java.util.Date

@Composable
private fun RequestInitialFocus(
    focusRequester: FocusRequester,
    enabled: Boolean = true,
    vararg keys: Any?,
) {
    LaunchedEffect(enabled, *keys) {
        if (!enabled) return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
    }
}

@Composable
fun EpgStoryDialog(
    program: EpgProgram,
    onDismiss: () -> Unit,
    programs: List<EpgProgram> = listOf(program),
    channel: SavedChannel? = null,
) {
    val pages = programs.ifEmpty { listOf(program) }
    val initialPage = pages.indexOf(program).coerceAtLeast(0)
    val focusRequester = remember { FocusRequester() }
    RequestInitialFocus(focusRequester, isAndroidTv(), program)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MyIptvPalette.Background) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Guide EPG", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    MenuButton(text = "Fermer", onClick = onDismiss)
                }
                EpgProgramPager(
                    pageKey = "${channel?.profileId}:${channel?.streamId}",
                    count = pages.size,
                    initialPage = initialPage,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { index ->
                    val entry = pages[index]
                    val now = System.currentTimeMillis() / 1000L
                    LargeEpgProgramCard(
                        originalTitle = entry.title,
                        description = entry.description,
                        country = channel?.countryCode.orEmpty(),
                        timeLabel = entry.timeRange,
                        channelName = channel?.name.orEmpty(),
                        channelLogoUrl = channel?.iconUrl,
                        current = entry.startEpochSeconds?.let { start ->
                            entry.stopEpochSeconds?.let { stop -> now >= start && now < stop }
                        } == true,
                        translationButtonModifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileDialog(
    profiles: List<XtreamProfile>,
    activeProfile: XtreamProfile?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onActivate: (XtreamProfile) -> Unit,
    onSave: (Int?, String, String, String, String, Boolean) -> Unit,
    onDelete: (XtreamProfile) -> Unit,
) {
    var showEditor by remember(profiles.isEmpty()) { mutableStateOf(profiles.isEmpty()) }
    var editedProfile by remember { mutableStateOf<XtreamProfile?>(null) }
    var pendingDelete by remember { mutableStateOf<XtreamProfile?>(null) }
    val editorKey = editedProfile?.id ?: 0
    var name by remember(editorKey) { mutableStateOf(editedProfile?.name.orEmpty()) }
    var server by remember(editorKey) { mutableStateOf(editedProfile?.serverUrl.orEmpty()) }
    var username by remember(editorKey) { mutableStateOf(editedProfile?.username.orEmpty()) }
    var password by remember(editorKey) { mutableStateOf(editedProfile?.password.orEmpty()) }
    var countryGroupingEnabled by remember(editorKey) {
        mutableStateOf(editedProfile?.countryGroupingEnabled ?: true)
    }
    val firstFieldFocusRequester = remember { FocusRequester() }
    val formScrollState = rememberScrollState()
    RequestInitialFocus(firstFieldFocusRequester, showEditor, editorKey, showEditor)
    LaunchedEffect(profiles) {
        val editedId = editedProfile?.id
        if (editedId != null && profiles.none { it.id == editedId }) editedProfile = null
        if (profiles.isEmpty()) showEditor = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = {
            Text(
                when {
                    !showEditor -> "Profils Xtream"
                    editedProfile == null -> "Nouveau profil Xtream"
                    else -> "Modifier ${editedProfile?.name}"
                },
            )
        },
        text = {
            if (showEditor) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(formScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "L’enregistrement reste local. Appuyez ensuite sur Sync pour charger les données.",
                        color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TvTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(firstFieldFocusRequester)
                            .tvFocusBorder(),
                        label = { Text("Nom du profil") },
                        singleLine = true,
                    )
                    TvTextField(
                        value = server,
                        onValueChange = { server = it },
                        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
                        label = { Text("Serveur (http://…)") },
                        singleLine = true,
                    )
                    TvTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
                        label = { Text("Utilisateur") },
                        singleLine = true,
                    )
                    TvTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
                        label = { Text("Mot de passe") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Organisation des chaînes",
                            color = MyIptvPalette.EmeraldLight,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        MenuButton(
                            text = if (countryGroupingEnabled) {
                                "Pays → Catégories"
                            } else {
                                "** ALL → Catégories fournisseur"
                            },
                            enabled = !isLoading,
                            onClick = {
                                countryGroupingEnabled = !countryGroupingEnabled
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (countryGroupingEnabled) {
                                "Le pays est déduit du nom de chaque chaîne."
                            } else {
                                "Le pays est ignoré et les catégories restent dans l’ordre du fournisseur."
                            },
                            color = MyIptvPalette.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(profiles, key = XtreamProfile::id) { profile ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MyIptvPalette.Border, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = if (profile.isActive) "● ${profile.name}" else profile.name,
                                color = if (profile.isActive) {
                                    MyIptvPalette.EmeraldAccent
                                } else {
                                    MyIptvPalette.White
                                },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = profile.lastSyncedAt?.let {
                                    "Dernière sync : ${DateFormat.getDateTimeInstance().format(Date(it))}"
                                } ?: "Jamais synchronisé",
                                color = MyIptvPalette.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (profile.countryGroupingEnabled) {
                                    "Navigation : Pays → Catégories"
                                } else {
                                    "Navigation : ** ALL → Catégories fournisseur"
                                },
                                color = MyIptvPalette.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MenuActionButton(
                                    label = if (profile.isActive) "Profil actif" else "Activer",
                                    icon = Icons.Rounded.CheckCircle,
                                    showLabel = false,
                                    active = profile.isActive,
                                    enabled = !profile.isActive && !isLoading,
                                    onClick = {
                                        onActivate(profile)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                MenuActionButton(
                                    label = "Modifier",
                                    icon = Icons.Rounded.Edit,
                                    showLabel = false,
                                    enabled = !isLoading,
                                    onClick = {
                                        editedProfile = profile
                                        showEditor = true
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                MenuActionButton(
                                    label = "Supprimer",
                                    icon = Icons.Rounded.Delete,
                                    showLabel = false,
                                    enabled = !isLoading,
                                    onClick = { pendingDelete = profile },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showEditor) {
                MenuButton(
                    text = if (isLoading) "Enregistrement…" else "Enregistrer",
                    enabled = !isLoading && name.isNotBlank() && server.isNotBlank() &&
                        username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        onSave(
                            editedProfile?.id,
                            name,
                            server,
                            username,
                            password,
                            countryGroupingEnabled,
                        )
                        onDismiss()
                    },
                )
            } else {
                MenuButton(
                    text = "Ajouter",
                    enabled = !isLoading,
                    onClick = {
                        editedProfile = null
                        showEditor = true
                    },
                )
            }
        },
        dismissButton = {
            MenuButton(
                text = if (showEditor && profiles.isNotEmpty()) "Retour" else "Fermer",
                onClick = {
                    if (showEditor && profiles.isNotEmpty()) {
                        showEditor = false
                        editedProfile = activeProfile
                    } else {
                        onDismiss()
                    }
                },
            )
        },
    )
    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MyIptvPalette.Anthracite,
            titleContentColor = MyIptvPalette.EmeraldLight,
            textContentColor = MyIptvPalette.BackgroundLight,
            title = { Text("Supprimer le profil ?") },
            text = {
                Text("Le profil « ${profile.name} » et toutes ses données locales seront supprimés.")
            },
            confirmButton = {
                MenuButton(
                    text = "Supprimer",
                    onClick = {
                        pendingDelete = null
                        onDelete(profile)
                    },
                )
            },
            dismissButton = {
                MenuButton(text = "Annuler", onClick = { pendingDelete = null })
            },
        )
    }
}

@Composable
fun SearchDialog(
    query: String,
    resultCount: Int,
    searchHistory: List<String>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClearHistoryRequest: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(searchHistory.isEmpty()) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val firstHistoryFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val television = isAndroidTv()

    RequestInitialFocus(
        firstHistoryFocusRequester,
        !editing && searchHistory.isNotEmpty(),
        editing,
        searchHistory.firstOrNull(),
    )
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            if (runCatching { searchFieldFocusRequester.requestFocus() }.isSuccess) {
                if (!television) keyboardController?.show()
            }
        } else {
            keyboardController?.hide()
        }
    }

    val startNewSearch = {
        onQueryChange("")
        editing = true
    }
    val returnToHistory = {
        keyboardController?.hide()
        editing = false
    }

    AlertDialog(
        onDismissRequest = {
            if (editing && searchHistory.isNotEmpty()) {
                returnToHistory()
            } else {
                onDismiss()
            }
        },
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = {
            Text(if (editing) "Rechercher une chaîne" else "Chaînes · Recherches récentes")
        },
        text = {
            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldFocusRequester)
                            .tvFocusBorder(),
                        label = { Text("Nom de chaîne") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (query.isNotBlank()) onSubmit(query)
                            },
                        ),
                    )
                    Text(
                        text = if (query.isBlank()) {
                            "Saisissez un ou plusieurs mots."
                        } else {
                            "$resultCount résultat(s)"
                        },
                        color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${searchHistory.size} dernière(s) recherche(s) · OK pour sélectionner",
                        color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MenuButton(
                        text = "+ Nouvelle recherche",
                        onClick = startNewSearch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(searchHistory, key = { it }) { historyQuery ->
                            TvListItem(
                                selected = historyQuery.equals(query, ignoreCase = true),
                                onClick = { onSubmit(historyQuery) },
                                modifier = if (historyQuery == searchHistory.first()) {
                                    Modifier.focusRequester(firstHistoryFocusRequester)
                                } else {
                                    Modifier
                                },
                            ) { _, color ->
                                Text(
                                    text = historyQuery,
                                    color = color,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                MenuButton(
                    text = "Rechercher",
                    enabled = query.isNotBlank(),
                    onClick = { onSubmit(query) },
                )
            } else {
                MenuButton(text = "Fermer", onClick = onDismiss)
            }
        },
        dismissButton = {
            if (editing && searchHistory.isNotEmpty()) {
                MenuButton(text = "Historique", onClick = returnToHistory)
            } else if (!editing && searchHistory.isNotEmpty()) {
                MenuButton(
                    text = "Effacer l’historique",
                    onClick = onClearHistoryRequest,
                )
            } else {
                MenuButton(text = "Effacer", onClick = onClear)
            }
        },
    )
}

@Composable
fun ClearSearchHistoryConfirmationDialog(
    searchCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }
    RequestInitialFocus(confirmFocusRequester)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text("Effacer les recherches récentes ?") },
        text = {
            Text(
                "Les $searchCount terme(s) mémorisé(s) seront supprimés. " +
                    "La recherche active et les chaînes ne seront pas modifiées.",
            )
        },
        confirmButton = {
            MenuButton(
                text = "Tout effacer",
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
            )
        },
        dismissButton = {
            MenuButton(text = "Annuler", onClick = onDismiss)
        },
    )
}

@Composable
fun ClearRecentHistoryConfirmationDialog(
    recentCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }
    RequestInitialFocus(confirmFocusRequester)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text("Effacer toutes les chaînes récentes ?") },
        text = {
            Text(
                "Les $recentCount chaîne(s) de l’historique Récents seront supprimées. " +
                    "La chaîne en cours continuera à jouer et les favoris resteront intacts.",
            )
        },
        confirmButton = {
            MenuButton(
                text = "Tout effacer",
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
            )
        },
        dismissButton = {
            MenuButton(text = "Annuler", onClick = onDismiss)
        },
    )
}

@Composable
fun FavoriteGroupsDialog(
    groups: List<FavoriteGroup>,
    onBrowse: (FavoriteGroup) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (FavoriteGroup, String) -> Unit,
    onDelete: (FavoriteGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    var managedGroup by remember { mutableStateOf<FavoriteGroup?>(null) }
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstGroupId = groups.firstOrNull()?.id
    RequestInitialFocus(
        firstItemFocusRequester,
        !showActions,
        firstGroupId,
        showActions,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text("Groupes de favoris") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "OK : afficher · OK long : gérer le groupe",
                    color = MyIptvPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        TvListItem(
                            selected = false,
                            modifier = if (group.id == firstGroupId) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                            onClick = { onBrowse(group) },
                            onLongClick = {
                                managedGroup = group
                                showActions = true
                            },
                        ) { _, color ->
                            Text(
                                text = group.name,
                                color = color,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item(key = "@new_favorite_group") {
                        TvListItem(
                            selected = false,
                            modifier = if (groups.isEmpty()) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                            onClick = {
                                managedGroup = null
                                showActions = true
                            },
                        ) { _, color ->
                            Text(
                                text = "+ Nouveau groupe",
                                color = color,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { MenuButton(text = "Fermer", onClick = onDismiss) },
    )
    if (showActions) {
        FavoriteGroupActionsDialog(
            group = managedGroup,
            onCreate = {
                onCreate(it)
                showActions = false
            },
            onRename = { group, name ->
                onRename(group, name)
                showActions = false
            },
            onDelete = {
                onDelete(it)
                showActions = false
            },
            onDismiss = { showActions = false },
        )
    }
}

@Composable
private fun FavoriteGroupActionsDialog(
    group: FavoriteGroup?,
    onCreate: (String) -> Unit,
    onRename: (FavoriteGroup, String) -> Unit,
    onDelete: (FavoriteGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var newGroupName by remember { mutableStateOf("") }
    var renamedGroupName by remember(group) { mutableStateOf(group?.name.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val firstActionFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }
    RequestInitialFocus(
        firstActionFocusRequester,
        !confirmDelete,
        group?.id,
        confirmDelete,
    )
    RequestInitialFocus(
        deleteFocusRequester,
        confirmDelete,
        group?.id,
        confirmDelete,
    )
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text(group?.name ?: "Nouveau groupe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                group?.let { existingGroup ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TvTextField(
                            value = renamedGroupName,
                            onValueChange = { renamedGroupName = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(firstActionFocusRequester)
                                .tvFocusBorder(),
                            label = { Text("Nom du groupe") },
                            singleLine = true,
                        )
                        MenuButton(
                            text = "Renommer",
                            enabled = renamedGroupName.isNotBlank(),
                            onClick = { onRename(existingGroup, renamedGroupName) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    MenuButton(
                        text = "Supprimer ${existingGroup.name}",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (group == null) {
                                    Modifier.focusRequester(firstActionFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .tvFocusBorder(),
                        label = { Text("Créer un nouveau groupe") },
                        singleLine = true,
                    )
                    MenuButton(
                        text = "Créer",
                        enabled = newGroupName.isNotBlank(),
                        onClick = { onCreate(newGroupName) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { MenuButton(text = "Fermer", onClick = onDismiss) },
    )
    if (confirmDelete && group != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MyIptvPalette.Anthracite,
            titleContentColor = MyIptvPalette.EmeraldLight,
            textContentColor = MyIptvPalette.BackgroundLight,
            title = { Text("Supprimer le groupe ?") },
            text = {
                Text("Le groupe « ${group.name} » sera supprimé. Les chaînes resteront dans Live.")
            },
            confirmButton = {
                MenuButton(
                    text = "Supprimer",
                    onClick = { onDelete(group) },
                    modifier = Modifier.focusRequester(deleteFocusRequester),
                )
            },
            dismissButton = {
                MenuButton(text = "Annuler", onClick = { confirmDelete = false })
            },
        )
    }
}

@Composable
fun ChannelFavoriteGroupsDialog(
    channel: SavedChannel?,
    groups: List<FavoriteGroup>,
    checkedGroupIds: Set<Long>,
    onToggle: (FavoriteGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstGroupFocusRequester = remember { FocusRequester() }
    val firstGroupId = groups.firstOrNull()?.id
    RequestInitialFocus(
        firstGroupFocusRequester,
        firstGroupId != null,
        channel?.streamId,
        firstGroupId,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text("Ajouter ou retirer des favoris") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = channel?.name ?: "Aucune chaîne sélectionnée",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MyIptvPalette.EmeraldAccent,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        val checked = group.id in checkedGroupIds
                        TvListItem(
                            selected = checked,
                            modifier = if (group.id == firstGroupId) {
                                Modifier.focusRequester(firstGroupFocusRequester)
                            } else {
                                Modifier
                            },
                            onClick = { onToggle(group) },
                        ) { _, color ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(2.dp, color, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) {
                                        Box(
                                            Modifier
                                                .size(12.dp)
                                                .background(MyIptvPalette.EmeraldAccent, CircleShape),
                                        )
                                    }
                                }
                                Text(
                                    text = group.name,
                                    color = color,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (groups.isEmpty()) {
                    Text(
                        "Aucun groupe. Créez-en un depuis le menu Favoris.",
                        color = MyIptvPalette.TextSecondary,
                    )
                }
            }
        },
        confirmButton = { MenuButton(text = "Fermer", onClick = onDismiss) },
    )
}

@Composable
fun GitHubBackupConfirmationDialog(
    upload: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }
    RequestInitialFocus(confirmFocusRequester, true, upload)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text(if (upload) "↑ Upload vers GitHub ?" else "↓ Download depuis GitHub ?") },
        text = {
            Text(
                if (upload) {
                    "Tous les profils Xtream (mots de passe inclus) et leurs favoris remplaceront la sauvegarde distante. Utilisez un dépôt privé."
                } else {
                    "Les profils GitHub seront fusionnés par serveur et utilisateur. Leurs favoris seront restaurés, sans supprimer les profils locaux absents de GitHub et sans lancer de Sync."
                },
            )
        },
        confirmButton = {
            MenuButton(
                text = if (upload) "Upload" else "Download",
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
            )
        },
        dismissButton = { MenuButton(text = "Annuler", onClick = onDismiss) },
    )
}

@Composable
fun EpgGitHubConfirmationDialog(
    upload: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }
    RequestInitialFocus(confirmFocusRequester, true, upload)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = {
            Text(if (upload) "Exporter la base EPG ?" else "Importer la base EPG ?")
        },
        text = {
            Text(
                if (upload) {
                    "La base EPG courte locale sera compressée puis remplacera la base EPG présente sur GitHub."
                } else {
                    "La base EPG GitHub sera vérifiée puis remplacera uniquement le cache EPG local. Les profils et favoris resteront inchangés."
                },
            )
        },
        confirmButton = {
            MenuButton(
                text = if (upload) "Exporter" else "Importer",
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
            )
        },
        dismissButton = { MenuButton(text = "Annuler", onClick = onDismiss) },
    )
}

@Composable
fun QuitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }
    RequestInitialFocus(confirmFocusRequester)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MyIptvPalette.Anthracite,
        titleContentColor = MyIptvPalette.EmeraldLight,
        textContentColor = MyIptvPalette.BackgroundLight,
        title = { Text("Quitter My IPTV ?") },
        text = { Text("La diffusion en cours sera arrêtée.") },
        confirmButton = {
            MenuButton(
                text = "Quitter",
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
            )
        },
        dismissButton = { MenuButton(text = "Annuler", onClick = onDismiss) },
    )
}
