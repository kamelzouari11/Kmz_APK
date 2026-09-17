package com.kmz.taskmanager.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmz.taskmanager.R
import com.kmz.taskmanager.data.*
import com.kmz.taskmanager.util.NotificationHelper
import com.kmz.taskmanager.util.UnifiedParser
import com.kmz.taskmanager.viewmodel.TaskViewModel
import com.kmz.taskmanager.viewmodel.ViewType
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(taskViewModel: TaskViewModel = viewModel()) {
        var selectedView by remember { mutableStateOf<ViewType>(ViewType.TODAY) }
        var selectedFolderId by remember { mutableStateOf<Long?>(null) }
        var showAddTaskDialog by remember { mutableStateOf(false) }
        var showAddFolderDialog by remember { mutableStateOf(false) }
        var folderToEdit by remember { mutableStateOf<Folder?>(null) }
        var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
        var showMoveTasksDialog by remember { mutableStateOf(false) }
        var showUploadConfirm by remember { mutableStateOf(false) }
        var showDownloadConfirm by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var showSearchBar by remember { mutableStateOf(false) }

        val allTasks by taskViewModel.tasks.collectAsState(initial = emptyList())
        val filteredTasks =
                remember(allTasks, selectedView, selectedFolderId, searchQuery) {
                        taskViewModel.filterTasks(allTasks, selectedView, selectedFolderId, searchQuery)
                }

        val folders by taskViewModel.folders.collectAsState(initial = emptyList())

        var taskToEdit by remember { mutableStateOf<Task?>(null) }
        var taskToQuickPostpone by remember { mutableStateOf<Task?>(null) }
        val context = androidx.compose.ui.platform.LocalContext.current

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
        LaunchedEffect(Unit) {
                while (true) {
                        kotlinx.coroutines.delay(10000) // Actualisation toutes les 10 secondes
                        currentTime = LocalDateTime.now()
                }
        }
        val snackbarHostState = remember { SnackbarHostState() }

        ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                        ModalDrawerSheet(
                                drawerContainerColor = SelectedTaskBg, // Fond un peu plus clair
                                modifier = Modifier.fillMaxWidth(0.8f),
                                drawerShape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                "Dossiers",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                        )
                                        IconButton(onClick = { showAddFolderDialog = true }) {
                                                Icon(
                                                        Icons.Default.CreateNewFolder,
                                                        contentDescription = "Nouveau Dossier",
                                                        tint = Secondary
                                                )
                                        }
                                }
                                NavigationDrawerItem(
                                        label = { Text("Tous les Dossiers") },
                                        selected = selectedFolderId == null,
                                        onClick = {
                                                selectedFolderId = null
                                                scope.launch { drawerState.close() }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 6.dp,
                                                        vertical = 2.dp
                                                ),
                                        colors =
                                                NavigationDrawerItemDefaults.colors(
                                                        selectedContainerColor = Secondary,
                                                        selectedTextColor = Color.White,
                                                        unselectedTextColor = Color.White,
                                                        unselectedContainerColor = Color.Transparent
                                                )
                                )
                                for (folder in folders) {
                                        var showFolderMenu by remember { mutableStateOf(false) }
                                        Box(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 6.dp,
                                                                vertical = 1.dp // Hauteur réduite
                                                        )
                                        ) {
                                                NavigationDrawerItem(
                                                        label = {
                                                                Text(folder.name, fontSize = 14.sp)
                                                        }, // Police légèrement réduite
                                                        selected = selectedFolderId == folder.id,
                                                        onClick = {
                                                                selectedFolderId = folder.id
                                                                scope.launch { drawerState.close() }
                                                        },
                                                        shape = RoundedCornerShape(6.dp),
                                                        badge = {
                                                                IconButton(
                                                                        onClick = {
                                                                                showFolderMenu =
                                                                                        true
                                                                        },
                                                                        modifier =
                                                                                Modifier.offset(
                                                                                        x = 6.dp
                                                                                ) // Pousse vers la
                                                                        // droite
                                                                        ) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .MoreVert,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint = Color.Gray
                                                                        )
                                                                }
                                                        },
                                                        colors =
                                                                NavigationDrawerItemDefaults.colors(
                                                                        selectedContainerColor =
                                                                                Secondary,
                                                                        selectedTextColor = Color.White,
                                                                        unselectedTextColor =
                                                                                Color.White,
                                                                        unselectedContainerColor =
                                                                                if (showFolderMenu)
                                                                                        SelectedTaskBg
                                                                                else
                                                                                        Color.Transparent
                                                                )
                                                )
                                                DropdownMenu(
                                                        expanded = showFolderMenu,
                                                        onDismissRequest = {
                                                                showFolderMenu = false
                                                        },
                                                        containerColor = SelectedTaskBg
                                                ) {
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Text(
                                                                                "Modifier",
                                                                                color = Color.White
                                                                        )
                                                                },
                                                                onClick = {
                                                                        folderToEdit = folder
                                                                        showFolderMenu = false
                                                                },
                                                                leadingIcon = {
                                                                        Icon(
                                                                                Icons.Default.Edit,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint = Secondary
                                                                        )
                                                                }
                                                        )
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Text(
                                                                                "Supprimer",
                                                                                color = Color.Red
                                                                        )
                                                                },
                                                                onClick = {
                                                                        taskViewModel.deleteFolder(
                                                                                folder
                                                                        )
                                                                        showFolderMenu = false
                                                                },
                                                                leadingIcon = {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .Delete,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint = Color.Red
                                                                        )
                                                                }
                                                        )
                                                }
                                        }
                                }
                                HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                )
                                NavigationDrawerItem(
                                        icon = {
                                                Icon(
                                                        Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = Secondary
                                                )
                                        },
                                        label = { Text("Sonnerie des alertes") },
                                        selected = false,
                                        onClick = {
                                                scope.launch { drawerState.close() }
                                                NotificationHelper.openAlertSoundSettings(context)
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        colors =
                                                NavigationDrawerItemDefaults.colors(
                                                        unselectedTextColor = Color.White,
                                                        unselectedContainerColor = Color.Transparent
                                                )
                                )
                                NavigationDrawerItem(
                                        icon = {
                                                Icon(
                                                        Icons.Default.NotificationsActive,
                                                        contentDescription = null,
                                                        tint = Secondary
                                                )
                                        },
                                        label = { Text("Alertes plein écran") },
                                        selected = false,
                                        onClick = {
                                                scope.launch { drawerState.close() }
                                                NotificationHelper.openFullScreenAlertSettings(context)
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        colors =
                                                NavigationDrawerItemDefaults.colors(
                                                        unselectedTextColor = Color.White,
                                                        unselectedContainerColor = Color.Transparent
                                                )
                                )
                        }
                },
                scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
                Scaffold(
                        topBar = {
                                TopAppBar(
                                        title = {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Image(
                                                                painter =
                                                                        painterResource(
                                                                                id =
                                                                                        R.drawable
                                                                                                .app_logo
                                                                        ),
                                                                contentDescription = null,
                                                                modifier =
                                                                        Modifier.size(48.dp)
                                                                                .padding(end = 6.dp)
                                                        )
                                                        Text(
                                                                text =
                                                                        if (selectedFolderId == null
                                                                        )
                                                                                stringResource(
                                                                                        R.string
                                                                                                .app_name
                                                                                )
                                                                        else
                                                                                folders
                                                                                        .find {
                                                                                                folder
                                                                                                ->
                                                                                                folder.id ==
                                                                                                        selectedFolderId
                                                                                        }
                                                                                        ?.name
                                                                                        ?: stringResource(
                                                                                                R.string
                                                                                                        .app_name
                                                                                        ),
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                }
                                        },
                                        navigationIcon = {
                                                IconButton(
                                                        onClick = {
                                                                scope.launch { drawerState.open() }
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Menu,
                                                                contentDescription = "Menu"
                                                        )
                                                }
                                        },
                                        colors =
                                                TopAppBarDefaults.topAppBarColors(
                                                        containerColor = Black,
                                                        titleContentColor = Color.White,
                                                        navigationIconContentColor = Color.White
                                                ),
                                        actions = {
                                                if (selectedTaskIds.isNotEmpty()) {
                                                        IconButton(
                                                                onClick = {
                                                                        showMoveTasksDialog = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.AutoMirrored.Filled
                                                                                .DriveFileMove,
                                                                        contentDescription =
                                                                                "Déplacer",
                                                                        tint = Secondary
                                                                )
                                                        }
                                                        Spacer(Modifier.width(6.dp))
                                                        IconButton(
                                                                onClick = {
                                                                        val tasksToDelete =
                                                                                filteredTasks
                                                                                        .filter {
                                                                                                it.id in
                                                                                                        selectedTaskIds
                                                                                        }
                                                                        taskViewModel.deleteTasks(
                                                                                tasksToDelete
                                                                        )
                                                                        selectedTaskIds = emptySet()
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Delete,
                                                                        contentDescription =
                                                                                "Supprimer la sélection",
                                                                        tint = Color.Red
                                                                )
                                                        }
                                                } else {
                                                        IconButton(
                                                                onClick = {
                                                                        showUploadConfirm = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.CloudUpload,
                                                                        contentDescription =
                                                                                "Cloud Upload",
                                                                        tint = Secondary
                                                                )
                                                        }
                                                        Spacer(Modifier.width(6.dp))
                                                        IconButton(
                                                                onClick = {
                                                                        showDownloadConfirm = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.CloudDownload,
                                                                        contentDescription =
                                                                                "Cloud Download",
                                                                        tint = Secondary
                                                                )
                                                        }
                                                        Spacer(Modifier.width(6.dp))
                                                        IconButton(
                                                                onClick = {
                                                                        showAddFolderDialog = true
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default
                                                                                .CreateNewFolder,
                                                                        contentDescription =
                                                                                "Nouveau Dossier",
                                                                        tint = Secondary
                                                                )
                                                        }
                                                }
                                        }
                                )
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        floatingActionButton = {
                                FloatingActionButton(
                                        onClick = { showAddTaskDialog = true },
                                        containerColor = Secondary,
                                        contentColor = Color.White
                                ) { Icon(Icons.Default.Add, contentDescription = "Ajouter Tâche") }
                        },
                        containerColor = Black
                ) { padding ->
                        Column(modifier = Modifier.padding(padding)) {
                                // View Tabs
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 6.dp,
                                                                vertical = 4.dp
                                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                        listOf(
                                                        ViewType.TODAY,
                                                        ViewType.WEEK,
                                                        ViewType.MONTH,
                                                        ViewType.YEAR,
                                                        ViewType.LATER,
                                                        ViewType.ALL
                                                )
                                                .forEach { view ->
                                                        val label =
                                                                when (view) {
                                                                        ViewType.ALL -> "ALL"
                                                                        ViewType.TODAY -> "1D"
                                                                        ViewType.WEEK -> "1W"
                                                                        ViewType.MONTH -> "1M"
                                                                        ViewType.YEAR -> "1Y"
                                                                        ViewType.LATER -> "AFT"
                                                                        else -> ""
                                                                }
                                                        val viewColor = viewTypeColor(view)
                                                        val isSelected = selectedView == view
                                                        Box(
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .clip(
                                                                                        RoundedCornerShape(
                                                                                                50
                                                                                        )
                                                                                )
                                                                                .background(
                                                                                        if (isSelected)
                                                                                                viewColor
                                                                                        else
                                                                                                SurfaceVariant
                                                                                )
                                                                                .clickable {
                                                                                        selectedView =
                                                                                                view
                                                                                }
                                                                                .padding(
                                                                                        vertical =
                                                                                                6.dp
                                                                                ),
                                                                contentAlignment =
                                                                        Alignment.Center
                                                        ) {
                                                                Text(
                                                                        label,
                                                                        fontSize = 12.sp,
                                                                        maxLines = 1,
                                                                        color =
                                                                                if (isSelected)
                                                                                        Color.Black
                                                                                else viewColor,
                                                                        fontWeight =
                                                                                if (isSelected)
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Normal
                                                                )
                                                        }
                                                }

                                        Box(
                                                modifier =
                                                        Modifier.width(46.dp)
                                                                .height(38.dp)
                                                                .clip(RoundedCornerShape(50))
                                                                .background(
                                                                        if (showSearchBar)
                                                                                Secondary
                                                                        else
                                                                                SurfaceVariant
                                                                )
                                                                .clickable {
                                                                        showSearchBar = !showSearchBar
                                                                        if (!showSearchBar) {
                                                                                searchQuery = ""
                                                                        }
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = "Recherche",
                                                        tint = if (showSearchBar) Color.Black else Secondary,
                                                        modifier = Modifier.size(22.dp)
                                                )
                                        }
                                }

                                if (showSearchBar) {
                                        OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 8.dp,
                                                                        vertical = 4.dp
                                                                ),
                                                singleLine = true,
                                                placeholder = { Text("Recherche: taxe") },
                                                leadingIcon = {
                                                        Icon(
                                                                Icons.Default.Search,
                                                                contentDescription = null,
                                                                tint = Secondary
                                                        )
                                                },
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor = Secondary,
                                                                unfocusedBorderColor = SurfaceVariant,
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White,
                                                                cursorColor = Secondary,
                                                                focusedPlaceholderColor = Color.Gray,
                                                                unfocusedPlaceholderColor = Color.Gray,
                                                                focusedLeadingIconColor = Secondary,
                                                                unfocusedLeadingIconColor = Secondary
                                                        )
                                        )
                                }

                                if (filteredTasks.isEmpty()) {
                                        Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.Task,
                                                        contentDescription = null,
                                                        tint = DarkGray,
                                                        modifier = Modifier.size(64.dp)
                                                )
                                                Spacer(Modifier.height(16.dp))
                                                Text("Aucune tâche ici", color = Color.Gray)
                                        }
                                } else {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                val groupedTasks =
                                                        filteredTasks.groupBy {
                                                                it.dueDate?.toLocalDate()
                                                        }
                                                groupedTasks.forEach { (date, tasks) ->
                                                        item {
                                                                val dateText =
                                                                        date?.let {
                                                                                val pattern =
                                                                                        if (it.year != java.time.LocalDate.now().year)
                                                                                                "EEEE d MMMM yyyy"
                                                                                        else
                                                                                                "EEEE d MMMM"
                                                                                it.format(
                                                                                        DateTimeFormatter
                                                                                                .ofPattern(
                                                                                                        pattern,
                                                                                                        java.util.Locale.FRENCH
                                                                                                )
                                                                                )
                                                                        } ?: "Sans date"
                                                                val headerColor =
                                                                        bucketColorForDueDate(
                                                                                date?.atStartOfDay(),
                                                                                java.time.LocalDate
                                                                                        .now()
                                                                        )
                                                                Text(
                                                                        text =
                                                                                dateText
                                                                                        .replaceFirstChar {
                                                                                                it.uppercase()
                                                                                        },
                                                                        color = headerColor,
                                                                        fontSize = 11.sp,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                start =
                                                                                                        16.dp,
                                                                                                end =
                                                                                                        16.dp,
                                                                                                top =
                                                                                                        8.dp,
                                                                                                bottom =
                                                                                                        2.dp
                                                                                        ),
                                                                        textAlign = TextAlign.Center
                                                                )
                                                                HorizontalDivider(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        horizontal =
                                                                                                16.dp
                                                                                ),
                                                                        thickness = 0.5.dp,
                                                                        color =
                                                                                headerColor.copy(
                                                                                        alpha = 0.3f
                                                                                )
                                                                )
                                                        }
                                                        items(tasks, key = { task -> task.id }) {
                                                                task ->
                                                                TaskItem(
                                                                        task = task,
                                                                        folders = folders,
                                                                        now = currentTime,
                                                                        isSelected =
                                                                                task.id in
                                                                                        selectedTaskIds,
                                                                        onSelect = {
                                                                                selectedTaskIds =
                                                                                        if (it in
                                                                                                        selectedTaskIds
                                                                                        ) {
                                                                                                selectedTaskIds -
                                                                                                        it
                                                                                        } else {
                                                                                                selectedTaskIds +
                                                                                                        it
                                                                                        }
                                                                        },
                                                                        onToggle = {
                                                                                taskViewModel
                                                                                        .toggleTaskDone(
                                                                                                it
                                                                                        )
                                                                        },
                                                                        onEdit = {
                                                                                taskToEdit = it
                                                                                showAddTaskDialog =
                                                                                        true
                                                                        },
                                                                        onPostpone = { t ->
                                                                                val now =
                                                                                        LocalDateTime
                                                                                                .now()
                                                                                val initialDate =
                                                                                        t.dueDate
                                                                                                ?: now.withHour(
                                                                                                                9
                                                                                                        )
                                                                                                        .withMinute(
                                                                                                                0
                                                                                                        )
                                                                                                        .withSecond(
                                                                                                                0
                                                                                                        )
                                                                                                        .withNano(
                                                                                                                0
                                                                                                        )
                                                                                val datePicker =
                                                                                        android.app
                                                                                                .DatePickerDialog(
                                                                                                        context,
                                                                                                        {
                                                                                                                _,
                                                                                                                y,
                                                                                                                m,
                                                                                                                d
                                                                                                                ->
                                                                                                                val timePicker =
                                                                                                                        android.app
                                                                                                                                .TimePickerDialog(
                                                                                                                                        context,
                                                                                                                                        {
                                                                                                                                                _,
                                                                                                                                                hh,
                                                                                                                                                mm
                                                                                                                                                ->
                                                                                                                                                val newDate =
                                                                                                                                                        LocalDateTime
                                                                                                                                                                .of(
                                                                                                                                                                        y,
                                                                                                                                                                        m +
                                                                                                                                                                                1,
                                                                                                                                                                        d,
                                                                                                                                                                        hh,
                                                                                                                                                                        mm
                                                                                                                                                                )
                                                                                                                                                taskViewModel
                                                                                                                                                        .postponeTask(
                                                                                                                                                                t,
                                                                                                                                                                newDate
                                                                                                                                                        )
                                                                                                                                        },
                                                                                                                                        initialDate
                                                                                                                                                .hour,
                                                                                                                                        initialDate
                                                                                                                                                .minute,
                                                                                                                                        true
                                                                                                                                )
                                                                                                                timePicker
                                                                                                                        .show()
                                                                                                        },
                                                                                                        initialDate
                                                                                                                .year,
                                                                                                        initialDate
                                                                                                                .monthValue -
                                                                                                                1,
                                                                                                        initialDate
                                                                                                                .dayOfMonth
                                                                                                )
                                                                                datePicker.show()
                                                                        },
                                                                        onQuickPostpone = { t ->
                                                                                taskToQuickPostpone =
                                                                                        t
                                                                        }
                                                                )
                                                        }
                                                }
                                                item { Spacer(Modifier.height(80.dp)) }
                                        }
                                }
                        }
                }

                if (showAddTaskDialog || taskToEdit != null) {
                        AddTaskDialog(
                                task = taskToEdit,
                                initialFolderId = selectedFolderId ?: 0L,
                                folders = folders,
                                onDismiss = {
                                        showAddTaskDialog = false
                                        taskToEdit = null
                                },
                                onAdd = {
                                        label,
                                        folderId,
                                        alarm,
                                        type,
                                        n,
                                        unit,
                                        wInt,
                                        wUnit,
                                        wRInt,
                                        wRUnit,
                                        dueDate ->
                                        if (taskToEdit != null) {
                                                taskViewModel.addTask(
                                                        taskToEdit!!.copy(
                                                                label = label,
                                                                folderId = folderId,
                                                                alarmLevel = alarm,
                                                                type = type,
                                                                repeatInterval = n,
                                                                repeatUnit = unit,
                                                                warningInterval = wInt,
                                                                warningUnit = wUnit,
                                                                warningRepeatInterval = wRInt,
                                                                warningRepeatUnit = wRUnit,
                                                                dueDate = dueDate
                                                        )
                                                )
                                        } else {
                                                taskViewModel.addTask(
                                                        Task(
                                                                folderId = folderId,
                                                                label = label,
                                                                dueDate = dueDate,
                                                                alarmLevel = alarm,
                                                                type = type,
                                                                repeatInterval = n,
                                                                repeatUnit = unit,
                                                                warningInterval = wInt,
                                                                warningUnit = wUnit,
                                                                warningRepeatInterval = wRInt,
                                                                warningRepeatUnit = wRUnit
                                                        )
                                                )
                                        }
                                        showAddTaskDialog = false
                                        taskToEdit = null
                                }
                        )
                }

                if (showAddFolderDialog || folderToEdit != null) {
                        AddFolderDialog(
                                folder = folderToEdit,
                                onDismiss = {
                                        showAddFolderDialog = false
                                        folderToEdit = null
                                },
                                onAdd = { name ->
                                        if (folderToEdit != null) {
                                                taskViewModel.updateFolder(
                                                        folderToEdit!!.copy(name = name)
                                                )
                                        } else {
                                                taskViewModel.addFolder(name)
                                        }
                                        showAddFolderDialog = false
                                        folderToEdit = null
                                }
                        )
                }

                if (showMoveTasksDialog) {
                        MoveTasksDialog(
                                folders = folders,
                                onDismiss = { showMoveTasksDialog = false },
                                onMove = { folderId ->
                                        taskViewModel.moveTasksToFolder(
                                                selectedTaskIds.toList(),
                                                folderId
                                        )
                                        selectedTaskIds = emptySet()
                                        showMoveTasksDialog = false
                                }
                        )
                }

                if (showUploadConfirm) {
                        AlertDialog(
                                onDismissRequest = { showUploadConfirm = false },
                                containerColor = SelectedTaskBg,
                                title = {
                                        Text(
                                                "Sauvegarder vers GitHub",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                        )
                                },
                                text = {
                                        Text(
                                                "Les données locales seront envoyées vers GitHub et remplaceront la sauvegarde existante.",
                                                color = Color.Gray
                                        )
                                },
                                confirmButton = {
                                        Button(
                                                onClick = {
                                                        showUploadConfirm = false
                                                        taskViewModel.backupDataCloud { msg ->
                                                                scope.launch {
                                                                        snackbarHostState.showSnackbar(msg)
                                                                }
                                                        }
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor = Secondary,
                                                                contentColor = Color.Black
                                                        )
                                        ) { Text("Sauvegarder", fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                        TextButton(onClick = { showUploadConfirm = false }) {
                                                Text("Annuler", color = Color.Gray)
                                        }
                                }
                        )
                }

                if (showDownloadConfirm) {
                        AlertDialog(
                                onDismissRequest = { showDownloadConfirm = false },
                                containerColor = SelectedTaskBg,
                                title = {
                                        Text(
                                                "Restaurer depuis GitHub",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                        )
                                },
                                text = {
                                        Column {
                                                Text(
                                                        "Toutes les données locales seront remplacées par la sauvegarde GitHub.",
                                                        color = Color.Gray
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                        "Cette action est irréversible.",
                                                        color = Color.Red,
                                                        fontWeight = FontWeight.Medium
                                                )
                                        }
                                },
                                confirmButton = {
                                        Button(
                                                onClick = {
                                                        showDownloadConfirm = false
                                                        taskViewModel.restoreDataCloud { msg ->
                                                                scope.launch {
                                                                        snackbarHostState.showSnackbar(msg)
                                                                }
                                                        }
                                                },
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor = Secondary,
                                                                contentColor = Color.Black
                                                        )
                                        ) { Text("Restaurer", fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                        TextButton(onClick = { showDownloadConfirm = false }) {
                                                Text("Annuler", color = Color.Gray)
                                        }
                                }
                        )
                }

                taskToQuickPostpone?.let { task ->
                        QuickPostponeDialog(
                                currentDueDate = task.dueDate,
                                onDismiss = { taskToQuickPostpone = null },
                                onApply = { input ->
                                        val newDate = UnifiedParser.parse(input, base = task.dueDate)
                                        if (newDate != null) {
                                                taskViewModel.postponeTask(task, newDate)
                                                taskToQuickPostpone = null
                                        } else {
                                                android.widget.Toast.makeText(
                                                                context,
                                                                "Format invalide ou date dans le passé",
                                                                android.widget.Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                        }
                                }
                        )
                }
        }
}

@Composable
fun MoveTasksDialog(folders: List<Folder>, onDismiss: () -> Unit, onMove: (Long) -> Unit) {
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SelectedTaskBg,
                title = { Text("Déplacer vers...", color = Color.White) },
                text = {
                        LazyColumn {
                                items(folders) { folder ->
                                        TextButton(
                                                onClick = { onMove(folder.id) },
                                                modifier = Modifier.fillMaxWidth()
                                        ) { Text(folder.name, color = Secondary) }
                                }
                                item {
                                        TextButton(
                                                onClick = { onMove(0L) },
                                                modifier = Modifier.fillMaxWidth()
                                        ) { Text("Aucun dossier", color = Color.Gray) }
                                }
                        }
                },
                confirmButton = {},
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Annuler", color = Color.Gray) }
                }
        )
}

fun viewTypeColor(view: ViewType): Color =
        when (view) {
                ViewType.ALL -> ViewAllColor
                ViewType.TODAY -> ViewTodayColor
                ViewType.WEEK -> ViewWeekColor
                ViewType.MONTH -> ViewMonthColor
                ViewType.YEAR -> ViewYearColor
                ViewType.LATER -> ViewLaterColor
        }

fun bucketColorForDueDate(dueDate: LocalDateTime?, now: java.time.LocalDate): Color {
        if (dueDate == null) return ViewLaterColor
        val date = dueDate.toLocalDate()
        return when {
                !date.isAfter(now) -> ViewTodayColor
                !date.isAfter(now.plusDays(7)) -> ViewWeekColor
                !date.isAfter(now.plusMonths(1)) -> ViewMonthColor
                !date.isAfter(now.plusYears(1)) -> ViewYearColor
                else -> ViewLaterColor
        }
}

@Composable
fun QuickPostponeDialog(
        currentDueDate: LocalDateTime?,
        onDismiss: () -> Unit,
        onApply: (String) -> Unit
) {
        var shortcut by remember { mutableStateOf("") }
        AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                containerColor = SelectedTaskBg,
                title = { Text("Reporter la tâche", color = Color.White) },
                text = {
                        Column {
                                Text(
                                        "Date libre ou raccourci [p|+][n][h|j|s|m] / [m|-][n][h|j|s|m]",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                        "p2s (+2 sem.)  +3j (+3 jours)  m1j (-1 jour)  -1m (-1 mois)  demain 14h  dans 3 jours",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                TextField(
                                        value = shortcut,
                                        onValueChange = { shortcut = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                                TextFieldDefaults.colors(
                                                        focusedContainerColor = InputFieldBg,
                                                        unfocusedContainerColor = InputFieldBg,
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        cursorColor = Secondary
                                                )
                                )
                                if (currentDueDate != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                                "Échéance actuelle : " +
                                                        currentDueDate.format(
                                                                DateTimeFormatter.ofPattern(
                                                                        "dd/MM/yyyy HH:mm"
                                                                )
                                                        ),
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                        )
                                }
                        }
                },
                confirmButton = {
                        TextButton(onClick = { onApply(shortcut) }) {
                                Text("Valider", color = Secondary)
                        }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Annuler", color = Color.Gray) }
                }
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskItem(
        task: Task,
        folders: List<Folder> = emptyList(),
        now: LocalDateTime,
        isSelected: Boolean = false,
        onSelect: (Long) -> Unit,
        onToggle: (Task) -> Unit,
        onEdit: (Task) -> Unit,
        onPostpone: (Task) -> Unit,
        onQuickPostpone: (Task) -> Unit
) {
        val isExpired = task.dueDate?.isBefore(now) == true && !task.isDone

        val containerColor =
                when {
                        isSelected -> SelectedTaskBg
                        isExpired -> ExpiredTaskBg
                        else -> TaskCardBg
                }

        val dismissState =
                rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                        onSelect(task.id)
                                }
                                false // Important: Snap back to original position
                        }
                )

        SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                        ) {
                                val icon =
                                        if (isSelected) Icons.Default.RemoveCircleOutline
                                        else Icons.Default.CheckCircle
                                val color = if (isSelected) Color.Gray else Secondary
                                Icon(icon, null, tint = color)
                        }
                }
        ) {
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .clickable { onToggle(task) },
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border =
                                if (isSelected) BorderStroke(1.dp, Secondary.copy(alpha = 0.5f))
                                else null
                ) {
                        Box {
                                if (isSelected) {
                                        Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Secondary.copy(alpha = 0.15f),
                                                modifier =
                                                        Modifier.align(Alignment.CenterEnd)
                                                                .padding(end = 12.dp)
                                                                .size(40.dp)
                                        )
                                }
                                Row(
                                        modifier =
                                                Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                text = task.label,
                                                                color =
                                                                        if (task.isDone) Color.Gray
                                                                        else Color.White,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                textDecoration =
                                                                        if (task.isDone)
                                                                                TextDecoration
                                                                                        .LineThrough
                                                                        else null,
                                                                modifier =
                                                                        Modifier.weight(
                                                                                1f,
                                                                                fill = false
                                                                        )
                                                        )
                                                        if (task.alarmLevel != AlarmLevel.MEDIUM) {
                                                                Spacer(Modifier.width(6.dp))
                                                                Icon(
                                                                        imageVector =
                                                                                if (task.alarmLevel ==
                                                                                                AlarmLevel
                                                                                                        .VERY_HIGH
                                                                                )
                                                                                        Icons.Default
                                                                                                .NotificationsActive
                                                                                else
                                                                                        Icons.Default
                                                                                                .Notifications,
                                                                        contentDescription =
                                                                                "Alarm",
                                                                        tint =
                                                                                if (task.alarmLevel ==
                                                                                                AlarmLevel
                                                                                                        .VERY_HIGH
                                                                                )
                                                                                        Color.Red
                                                                                else Color(0xFFFFC107),
                                                                        modifier =
                                                                                Modifier.size(14.dp)
                                                                )
                                                        }
                                                }
                                                if (task.dueDate != null ||
                                                                task.type == TaskType.REPETITIVE ||
                                                                task.folderId != 0L
                                                ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                modifier =
                                                                        Modifier.padding(top = 0.dp)
                                                        ) {
                                                                if (task.dueDate != null) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .AccessTime,
                                                                                contentDescription =
                                                                                        null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                10.dp
                                                                                        ),
                                                                                tint = Color.Gray
                                                                        )
                                                                        Spacer(Modifier.width(4.dp))
                                                                        val dueDateColor =
                                                                                if (task.isDone)
                                                                                        Color.Gray
                                                                                else
                                                                                        bucketColorForDueDate(
                                                                                                task.dueDate,
                                                                                                now.toLocalDate()
                                                                                        )

                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically,
                                                                                modifier =
                                                                                        Modifier
                                                                                                .clickable {
                                                                                                        onPostpone(
                                                                                                                task
                                                                                                        )
                                                                                                }
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .AccessTime,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        12.dp
                                                                                                ),
                                                                                        tint =
                                                                                                dueDateColor
                                                                                )
                                                                                Spacer(
                                                                                        Modifier.width(
                                                                                                4.dp
                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                task.dueDate
                                                                                                        .format(
                                                                                                                DateTimeFormatter
                                                                                                                        .ofPattern(
                                                                                                                                "HH:mm"
                                                                                                                        )
                                                                                                        ),
                                                                                        color =
                                                                                                dueDateColor,
                                                                                        fontSize =
                                                                                                11.sp,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Medium
                                                                                )
                                                                        }
                                                                }

                                                                if (task.type == TaskType.REPETITIVE
                                                                ) {
                                                                        Spacer(Modifier.width(6.dp))
                                                                        Icon(
                                                                                Icons.Default.Sync,
                                                                                contentDescription =
                                                                                        "Répétitive",
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                10.dp
                                                                                        ),
                                                                                tint = Secondary
                                                                        )
                                                                }

                                                                if (task.folderId != 0L) {
                                                                        val folderName =
                                                                                folders
                                                                                        .find {
                                                                                                it.id ==
                                                                                                        task.folderId
                                                                                        }
                                                                                        ?.name
                                                                                        ?: ""
                                                                        if (folderName.isNotBlank()
                                                                        ) {
                                                                                Spacer(
                                                                                        Modifier.width(
                                                                                                6.dp
                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        text =
                                                                                                "• $folderName",
                                                                                        color =
                                                                                                FolderLabelColor,
                                                                                        fontSize =
                                                                                                10.sp,
                                                                                        maxLines =
                                                                                                1,
                                                                                        overflow =
                                                                                                androidx.compose
                                                                                                        .ui
                                                                                                        .text
                                                                                                        .style
                                                                                                        .TextOverflow
                                                                                                        .Ellipsis,
                                                                                        modifier =
                                                                                                Modifier.weight(
                                                                                                        1f,
                                                                                                        fill =
                                                                                                                false
                                                                                                )
                                                                                )
                                                                        }
                                                                }

                                                                Spacer(Modifier.weight(1f))

                                                                IconButton(
                                                                        onClick = { onEdit(task) },
                                                                        modifier =
                                                                                Modifier.size(32.dp)
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .MoreVert,
                                                                                contentDescription =
                                                                                        "Modifier",
                                                                                tint = Color.Gray,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                22.dp
                                                                                        )
                                                                        )
                                                                }

                                                                Spacer(Modifier.width(6.dp))

                                                                IconButton(
                                                                        onClick = {
                                                                                onQuickPostpone(
                                                                                        task
                                                                                )
                                                                        },
                                                                        modifier =
                                                                                Modifier.size(32.dp)
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default.Add,
                                                                                contentDescription =
                                                                                        "Reporter",
                                                                                tint = Secondary,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                22.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        if (task.isDone) {
                                                Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Effectuée",
                                                        tint = Secondary,
                                                        modifier =
                                                                Modifier.size(20.dp)
                                                                        .padding(start = 6.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun AddTaskDialog(
        task: Task? = null,
        initialFolderId: Long,
        folders: List<Folder>,
        onDismiss: () -> Unit,
        onAdd:
                (
                        String,
                        Long,
                        AlarmLevel,
                        TaskType,
                        Int?,
                        RepeatUnit?,
                        Int,
                        RepeatUnit,
                        Int?,
                        RepeatUnit?,
                        LocalDateTime?) -> Unit
) {
        var labelText by remember { mutableStateOf(task?.label ?: "") }
        var dueDateText by remember { mutableStateOf("") }
        var selectedFolderId by remember { mutableStateOf(task?.folderId ?: initialFolderId) }
        var alarmLevel by remember { mutableStateOf(task?.alarmLevel ?: AlarmLevel.HIGH) }
        var taskType by remember { mutableStateOf(task?.type ?: TaskType.ONCE) }
        var nValue by remember { mutableStateOf(task?.repeatInterval?.toString() ?: "1") }
        var repeatUnit by remember { mutableStateOf(task?.repeatUnit ?: RepeatUnit.D) }

        var currentDueDate by remember { mutableStateOf(task?.dueDate) }

        var warningInterval by remember {
                mutableStateOf(task?.warningInterval?.takeIf { it > 0 }?.toString() ?: "")
        }
        var warningUnit by remember { mutableStateOf(task?.warningUnit ?: RepeatUnit.MINUTES) }
        var warningRepeatInterval by remember {
                mutableStateOf(task?.warningRepeatInterval?.toString() ?: "")
        }
        var warningRepeatUnit by remember {
                mutableStateOf(task?.warningRepeatUnit ?: RepeatUnit.MINUTES)
        }

        val context = androidx.compose.ui.platform.LocalContext.current

        LaunchedEffect(dueDateText) {
                if (dueDateText.isNotBlank()) {
                        val defaultTime = task?.dueDate?.toLocalTime() ?: LocalTime.of(9, 0)
                        val base = if (task != null) currentDueDate else null
                        val parsedDate = UnifiedParser.parse(dueDateText, base = base, defaultTime = defaultTime)
                        if (parsedDate != null) {
                                currentDueDate = parsedDate
                        }
                } else if (task == null) {
                        currentDueDate = null
                }
        }

        var showDiscardDialog by remember { mutableStateOf(false) }

        val hasChanges =
                if (task == null) labelText.isNotBlank()
                else
                        labelText != task.label ||
                                currentDueDate != task.dueDate ||
                                alarmLevel != task.alarmLevel ||
                                taskType != task.type

        val doSave: () -> Unit = {
                if (labelText.isNotBlank()) {
                        onAdd(
                                labelText,
                                selectedFolderId,
                                alarmLevel,
                                taskType,
                                nValue.toIntOrNull(),
                                repeatUnit,
                                warningInterval.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                warningUnit,
                                warningRepeatInterval.toIntOrNull(),
                                warningRepeatUnit,
                                currentDueDate
                        )
                }
        }

        AlertDialog(
                onDismissRequest = { if (hasChanges) showDiscardDialog = true else onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                title = {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                        if (task == null) "Nouvelle Tâche" else "Modifier Tâche",
                                        fontWeight = FontWeight.Bold
                                )
                                Box(
                                        modifier =
                                                Modifier.clip(RoundedCornerShape(50))
                                                        .background(Color(0xFF4CAF50))
                                                        .clickable { doSave() }
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                        Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Valider",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                        )
                                }
                        }
                },
                text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                TextField(
                                        value = labelText,
                                        onValueChange = { labelText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                                TextFieldDefaults.colors(
                                                        focusedContainerColor = InputFieldBg,
                                                        unfocusedContainerColor = InputFieldBg,
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        cursorColor = Secondary
                                                )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                TextField(
                                        value = dueDateText,
                                        onValueChange = { dueDateText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                                TextFieldDefaults.colors(
                                                        focusedContainerColor = InputFieldBg,
                                                        unfocusedContainerColor = InputFieldBg,
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        cursorColor = Secondary
                                                )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("Dossier", fontSize = 12.sp, color = Color.Gray)
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        FilterChip(
                                                selected = selectedFolderId == 0L,
                                                onClick = { selectedFolderId = 0L },
                                                label = { Text("Aucun") },
                                                colors =
                                                        FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = Secondary,
                                                                selectedLabelColor = Black
                                                        )
                                        )
                                        folders.forEach { folder ->
                                                Spacer(Modifier.width(6.dp))
                                                FilterChip(
                                                        selected = selectedFolderId == folder.id,
                                                        onClick = { selectedFolderId = folder.id },
                                                        label = { Text(folder.name) },
                                                        colors =
                                                                FilterChipDefaults.filterChipColors(
                                                                        selectedContainerColor =
                                                                                Secondary,
                                                                        selectedLabelColor = Black
                                                                )
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                                Text("Alarme", fontSize = 12.sp, color = Color.Gray)
                                                Row(modifier = Modifier.padding(top = 6.dp)) {
                                                        AlarmLevel.entries.forEach { level ->
                                                                val icon =
                                                                        when (level) {
                                                                                AlarmLevel.MEDIUM ->
                                                                                        Icons.Default
                                                                                                .NotificationsNone
                                                                                AlarmLevel.HIGH ->
                                                                                        Icons.Default
                                                                                                .Notifications
                                                                                AlarmLevel
                                                                                        .VERY_HIGH ->
                                                                                        Icons.Default
                                                                                                .NotificationsActive
                                                                        }
                                                                val size =
                                                                        when (level) {
                                                                                AlarmLevel.MEDIUM ->
                                                                                        20.dp
                                                                                AlarmLevel.HIGH ->
                                                                                        24.dp
                                                                                AlarmLevel
                                                                                        .VERY_HIGH ->
                                                                                        26.dp
                                                                        }
                                                                Box(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                                start =
                                                                                                        6.dp
                                                                                        )
                                                                                        .size(32.dp)
                                                                                        .border(
                                                                                                width =
                                                                                                        if (alarmLevel ==
                                                                                                                        level
                                                                                                        )
                                                                                                                1.dp
                                                                                                        else
                                                                                                                0.dp,
                                                                                                color =
                                                                                                        Color.White,
                                                                                                shape =
                                                                                                        CircleShape
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        IconButton(
                                                                                onClick = {
                                                                                        alarmLevel =
                                                                                                level
                                                                                },
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                size
                                                                                        )
                                                                        ) {
                                                                                Icon(
                                                                                        icon,
                                                                                        contentDescription =
                                                                                                null,
                                                                                        tint =
                                                                                                when (level) {
                                                                                                        AlarmLevel.MEDIUM ->
                                                                                                                if (alarmLevel == level) Color.Gray else Color.Gray.copy(alpha = 0.35f)
                                                                                                        AlarmLevel.HIGH ->
                                                                                                                if (alarmLevel == level) Color(0xFFFFC107) else Color(0xFFFFC107).copy(alpha = 0.35f)
                                                                                                        AlarmLevel.VERY_HIGH ->
                                                                                                                if (alarmLevel == level) Color.Red else Color.Red.copy(alpha = 0.35f)
                                                                                                },
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        size
                                                                                                )
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Date Info Line (non-interactive)
                                currentDueDate?.let { date ->
                                        Text(
                                                text =
                                                        date.format(
                                                                java.time.format.DateTimeFormatter
                                                                        .ofPattern(
                                                                                "EEEE dd MMMM yyyy à HH:mm",
                                                                                java.util.Locale
                                                                                        .FRENCH
                                                                        )
                                                        ),
                                                fontSize = 13.sp,
                                                color = Secondary,
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center
                                        )
                                }
                                        ?: Text(
                                                "Aucune échéance définie",
                                                fontSize = 13.sp,
                                                color = Color.Gray,
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                textAlign = TextAlign.Center
                                        )

                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                        ) {
                                                Checkbox(
                                                        checked = taskType == TaskType.REPETITIVE,
                                                        onCheckedChange = {
                                                                taskType =
                                                                        if (it) TaskType.REPETITIVE
                                                                        else TaskType.ONCE
                                                        },
                                                        colors =
                                                                CheckboxDefaults.colors(
                                                                        checkedColor = Secondary
                                                                )
                                                )
                                                Text("Répétitive")
                                        }

                                        IconButton(
                                                onClick = {
                                                        val now = LocalDateTime.now()
                                                        val initialDate =
                                                                currentDueDate
                                                                        ?: task?.dueDate
                                                                                ?: now.withHour(9)
                                                                                .withMinute(0)
                                                                                .withSecond(0)
                                                                                .withNano(0)
                                                        val datePicker =
                                                                android.app.DatePickerDialog(
                                                                        context,
                                                                        { _, y, m, d ->
                                                                                val timePicker =
                                                                                        android.app
                                                                                                .TimePickerDialog(
                                                                                                        context,
                                                                                                        {
                                                                                                                _,
                                                                                                                hh,
                                                                                                                mm
                                                                                                                ->
                                                                                                                currentDueDate =
                                                                                                                        LocalDateTime
                                                                                                                                .of(
                                                                                                                                        y,
                                                                                                                                        m +
                                                                                                                                                1,
                                                                                                                                        d,
                                                                                                                                        hh,
                                                                                                                                        mm
                                                                                                                                )
                                                                                                        },
                                                                                                        initialDate
                                                                                                                .hour,
                                                                                                        if (currentDueDate ==
                                                                                                                        null &&
                                                                                                                        task?.dueDate ==
                                                                                                                                null
                                                                                                        )
                                                                                                                0
                                                                                                        else
                                                                                                                initialDate
                                                                                                                        .minute,
                                                                                                        true
                                                                                                )
                                                                                timePicker.show()
                                                                        },
                                                                        initialDate.year,
                                                                        initialDate.monthValue - 1,
                                                                        initialDate.dayOfMonth
                                                                )
                                                        datePicker.show()
                                                },
                                                modifier = Modifier.size(48.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.AccessTime,
                                                        contentDescription =
                                                                "Choisir date et heure",
                                                        tint = Secondary,
                                                        modifier = Modifier.size(32.dp)
                                                )
                                        }
                                }

                                if (taskType == TaskType.REPETITIVE) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 12.dp)
                                        ) {
                                                Text("Tous les ")
                                                TextField(
                                                        value = nValue,
                                                        onValueChange = { nValue = it },
                                                        modifier = Modifier.width(60.dp),
                                                        keyboardOptions =
                                                                androidx.compose.foundation.text
                                                                        .KeyboardOptions(
                                                                                keyboardType =
                                                                                        androidx.compose
                                                                                                .ui
                                                                                                .text
                                                                                                .input
                                                                                                .KeyboardType
                                                                                                .Number
                                                                        ),
                                                        colors =
                                                                TextFieldDefaults.colors(
                                                                        focusedContainerColor =
                                                                                InputFieldBg,
                                                                        unfocusedContainerColor =
                                                                                InputFieldBg,
                                                                        focusedTextColor =
                                                                                Color.White,
                                                                        unfocusedTextColor =
                                                                                Color.White,
                                                                        focusedIndicatorColor =
                                                                                Color.Transparent,
                                                                        unfocusedIndicatorColor =
                                                                                Color.Transparent,
                                                                        cursorColor = Secondary
                                                                )
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                repeatUnitDropdown(
                                                        selectedUnit = repeatUnit,
                                                        onUnitSelected = { repeatUnit = it }
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                        "Avertissement avant alarme",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextField(
                                                value = warningInterval,
                                                onValueChange = { warningInterval = it },
                                                modifier = Modifier.width(70.dp),
                                                keyboardOptions =
                                                        androidx.compose.foundation.text
                                                                .KeyboardOptions(
                                                                        keyboardType =
                                                                                androidx.compose.ui
                                                                                        .text.input
                                                                                        .KeyboardType
                                                                                        .Number
                                                                ),
                                                colors =
                                                        TextFieldDefaults.colors(
                                                                focusedContainerColor =
                                                                        InputFieldBg,
                                                                unfocusedContainerColor =
                                                                        InputFieldBg,
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White,
                                                                focusedIndicatorColor =
                                                                        Color.Transparent,
                                                                unfocusedIndicatorColor =
                                                                        Color.Transparent,
                                                                cursorColor = Secondary
                                                        )
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        repeatUnitDropdown(
                                                selectedUnit = warningUnit,
                                                onUnitSelected = { warningUnit = it }
                                        )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                        "Répétition d'avertissement (optionnel)",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextField(
                                                value = warningRepeatInterval,
                                                onValueChange = { warningRepeatInterval = it },
                                                modifier = Modifier.width(70.dp),
                                                keyboardOptions =
                                                        androidx.compose.foundation.text
                                                                .KeyboardOptions(
                                                                        keyboardType =
                                                                                androidx.compose.ui
                                                                                        .text.input
                                                                                        .KeyboardType
                                                                                        .Number
                                                                ),
                                                colors =
                                                        TextFieldDefaults.colors(
                                                                focusedContainerColor =
                                                                        InputFieldBg,
                                                                unfocusedContainerColor =
                                                                        InputFieldBg,
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White,
                                                                focusedIndicatorColor =
                                                                        Color.Transparent,
                                                                unfocusedIndicatorColor =
                                                                        Color.Transparent,
                                                                cursorColor = Secondary
                                                        )
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        repeatUnitDropdown(
                                                selectedUnit = warningRepeatUnit,
                                                onUnitSelected = { warningRepeatUnit = it }
                                        )
                                }
                        }
                },
                confirmButton = {},
                dismissButton = {},
                containerColor = DarkGray,
                titleContentColor = Color.White,
                textContentColor = Color.White
        )

        if (showDiscardDialog) {
                AlertDialog(
                        onDismissRequest = { showDiscardDialog = false },
                        containerColor = SelectedTaskBg,
                        title = { Text("Modifications non sauvegardées", color = Color.White) },
                        text = {
                                Text("Sauvegarder avant de quitter ?", color = Color.Gray)
                        },
                        confirmButton = {
                                Button(
                                        onClick = { doSave() },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor = Secondary,
                                                        contentColor = Color.Black
                                                )
                                ) { Text("Sauvegarder", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                                TextButton(
                                        onClick = {
                                                showDiscardDialog = false
                                                onDismiss()
                                        }
                                ) { Text("Ignorer", color = Color.Gray) }
                        }
                )
        }
}

@Composable
fun repeatUnitDropdown(selectedUnit: RepeatUnit, onUnitSelected: (RepeatUnit) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        val label =
                when (selectedUnit) {
                        RepeatUnit.MINUTES -> "minutes"
                        RepeatUnit.HOURS -> "heures"
                        RepeatUnit.D -> "jours"
                        RepeatUnit.W -> "semaines"
                        RepeatUnit.M -> "mois"
                        RepeatUnit.Y -> "ans"
                }
        Box {
                AssistChip(onClick = { expanded = true }, label = { Text(label) })
                DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = SelectedTaskBg // Gris plus clair
                ) {
                        RepeatUnit.entries.forEach { unit ->
                                val uLabel =
                                        when (unit) {
                                                RepeatUnit.MINUTES -> "minutes"
                                                RepeatUnit.HOURS -> "heures"
                                                RepeatUnit.D -> "jours"
                                                RepeatUnit.W -> "semaines"
                                                RepeatUnit.M -> "mois"
                                                RepeatUnit.Y -> "ans"
                                        }
                                DropdownMenuItem(
                                        text = { Text(uLabel, color = Color.White) },
                                        onClick = {
                                                onUnitSelected(unit)
                                                expanded = false
                                        }
                                )
                        }
                }
        }
}

@Composable
fun AddFolderDialog(folder: Folder? = null, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
        var text by remember { mutableStateOf(folder?.name ?: "") }
        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (folder == null) "Nouveau Dossier" else "Modifier Dossier") },
                text = {
                        TextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = InputFieldBg,
                                                unfocusedContainerColor = InputFieldBg,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                cursorColor = Secondary
                                        )
                        )
                },
                confirmButton = {
                        Button(
                                onClick = { if (text.isNotBlank()) onAdd(text) },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Secondary,
                                                contentColor = Color.White
                                        )
                        ) { Text(if (folder == null) "Créer" else "Sauvegarder") }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Annuler", color = Color.Gray) }
                },
                containerColor = DarkGray,
                titleContentColor = Color.White,
                textContentColor = Color.White
        )
}
