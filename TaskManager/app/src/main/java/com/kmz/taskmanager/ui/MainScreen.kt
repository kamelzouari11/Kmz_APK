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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmz.taskmanager.data.*
import com.kmz.taskmanager.util.SmartParser
import com.kmz.taskmanager.viewmodel.TaskViewModel
import com.kmz.taskmanager.viewmodel.ViewType
import java.time.LocalDateTime
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

        val allTasks by taskViewModel.tasks.collectAsState(initial = emptyList())
        val filteredTasks =
                remember(allTasks, selectedView, selectedFolderId) {
                        taskViewModel.filterTasks(allTasks, selectedView, selectedFolderId)
                }

        val folders by taskViewModel.folders.collectAsState(initial = emptyList())

        var taskToEdit by remember { mutableStateOf<Task?>(null) }
        val context = androidx.compose.ui.platform.LocalContext.current

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                        ModalDrawerSheet(
                                drawerContainerColor = Color(0xFF1A1A1A), // Fond un peu plus clair
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
                                        shape = RoundedCornerShape(8.dp),
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 2.dp
                                                ),
                                        colors =
                                                NavigationDrawerItemDefaults.colors(
                                                        selectedContainerColor = Secondary,
                                                        selectedTextColor = Black,
                                                        unselectedTextColor = Color.White,
                                                        unselectedContainerColor = Color.Transparent
                                                )
                                )
                                for (folder in folders) {
                                        var showFolderMenu by remember { mutableStateOf(false) }
                                        Box(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 12.dp,
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
                                                        shape = RoundedCornerShape(8.dp),
                                                        badge = {
                                                                IconButton(
                                                                        onClick = {
                                                                                showFolderMenu =
                                                                                        true
                                                                        },
                                                                        modifier =
                                                                                Modifier.offset(
                                                                                        x = 8.dp
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
                                                                        selectedTextColor = Black,
                                                                        unselectedTextColor =
                                                                                Color.White,
                                                                        unselectedContainerColor =
                                                                                if (showFolderMenu)
                                                                                        MidnightGreen
                                                                                else
                                                                                        Color.Transparent
                                                                )
                                                )
                                                DropdownMenu(
                                                        expanded = showFolderMenu,
                                                        onDismissRequest = {
                                                                showFolderMenu = false
                                                        },
                                                        containerColor = MidnightGreen
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
                        }
                },
                scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
                Scaffold(
                        topBar = {
                                TopAppBar(
                                        title = {
                                                Text(
                                                        if (selectedFolderId == null) "TaskManager"
                                                        else
                                                                folders
                                                                        .find { folder ->
                                                                                folder.id ==
                                                                                        selectedFolderId
                                                                        }
                                                                        ?.name
                                                                        ?: "TaskManager"
                                                )
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
                                                                        taskViewModel.backupData {
                                                                                msg ->
                                                                                scope.launch {
                                                                                        snackbarHostState
                                                                                                .showSnackbar(
                                                                                                        msg
                                                                                                )
                                                                                }
                                                                        }
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Upload,
                                                                        contentDescription =
                                                                                "Sauvegarder",
                                                                        tint = Secondary
                                                                )
                                                        }
                                                        IconButton(
                                                                onClick = {
                                                                        taskViewModel.restoreData {
                                                                                msg ->
                                                                                scope.launch {
                                                                                        snackbarHostState
                                                                                                .showSnackbar(
                                                                                                        msg
                                                                                                )
                                                                                }
                                                                        }
                                                                }
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Download,
                                                                        contentDescription =
                                                                                "Restaurer",
                                                                        tint = Secondary
                                                                )
                                                        }
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
                                        contentColor = Black
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
                                                                horizontal = 12.dp,
                                                                vertical = 4.dp
                                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        listOf(
                                                        ViewType.ALL,
                                                        ViewType.TODAY,
                                                        ViewType.THIS_WEEK,
                                                        ViewType.LATER
                                                )
                                                .forEach { view ->
                                                        val label =
                                                                when (view) {
                                                                        ViewType.ALL -> "ALL"
                                                                        ViewType.TODAY -> "TODAY"
                                                                        ViewType.THIS_WEEK -> "WEEK"
                                                                        ViewType.LATER -> "LATER"
                                                                }
                                                        FilterChip(
                                                                modifier = Modifier.weight(1f),
                                                                selected = selectedView == view,
                                                                onClick = { selectedView = view },
                                                                label = {
                                                                        Text(
                                                                                label,
                                                                                fontSize = 11.sp,
                                                                                maxLines = 1
                                                                        )
                                                                },
                                                                colors =
                                                                        FilterChipDefaults
                                                                                .filterChipColors(
                                                                                        selectedContainerColor =
                                                                                                Secondary,
                                                                                        selectedLabelColor =
                                                                                                Black,
                                                                                        containerColor =
                                                                                                SurfaceVariant,
                                                                                        labelColor =
                                                                                                Color.Gray
                                                                                ),
                                                                border = null
                                                        )
                                                }
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
                                                items(filteredTasks, key = { task -> task.id }) {
                                                        task ->
                                                        TaskItem(
                                                                task = task,
                                                                folders = folders,
                                                                isSelected =
                                                                        task.id in selectedTaskIds,
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
                                                                                .toggleTaskDone(it)
                                                                },
                                                                onDelete = {
                                                                        taskViewModel.deleteTask(it)
                                                                },
                                                                onEdit = { taskToEdit = it },
                                                                onPostpone = { t ->
                                                                        val now =
                                                                                LocalDateTime.now()
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
                                                                                                                                now.hour,
                                                                                                                                now.minute,
                                                                                                                                true
                                                                                                                        )
                                                                                                        timePicker
                                                                                                                .show()
                                                                                                },
                                                                                                now.year,
                                                                                                now.monthValue -
                                                                                                        1,
                                                                                                now.dayOfMonth
                                                                                        )
                                                                        datePicker.show()
                                                                }
                                                        )
                                                }
                                                item { Spacer(Modifier.height(80.dp)) }
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
                                                priority,
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
                                                                        priority = priority,
                                                                        alarmLevel = alarm,
                                                                        type = type,
                                                                        repeatInterval = n,
                                                                        repeatUnit = unit,
                                                                        warningInterval = wInt,
                                                                        warningUnit = wUnit,
                                                                        warningRepeatInterval =
                                                                                wRInt,
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
                                                                        priority = priority,
                                                                        alarmLevel = alarm,
                                                                        type = type,
                                                                        repeatInterval = n,
                                                                        repeatUnit = unit,
                                                                        warningInterval = wInt,
                                                                        warningUnit = wUnit,
                                                                        warningRepeatInterval =
                                                                                wRInt,
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
                }
        }
}

@Composable
fun MoveTasksDialog(folders: List<Folder>, onDismiss: () -> Unit, onMove: (Long) -> Unit) {
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = Color(0xFF1A1A1A),
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

@Composable
fun TaskItem(
        task: Task,
        folders: List<Folder> = emptyList(),
        isSelected: Boolean = false,
        onSelect: (Long) -> Unit,
        onToggle: (Task) -> Unit,
        onDelete: (Task) -> Unit,
        onEdit: (Task) -> Unit,
        onPostpone: (Task) -> Unit
) {
        val priorityColor =
                when (task.priority) {
                        Priority.LOW -> PriorityLow
                        Priority.MEDIUM -> PriorityMedium
                        Priority.HIGH -> PriorityHigh
                }

        var showMenu by remember { mutableStateOf(false) }

        Box {
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .clickable { onToggle(task) },
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (isSelected || showMenu) MidnightGreen
                                                else SurfaceVariant
                                ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                        Row(
                                modifier =
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                        text = task.label,
                                                        color =
                                                                if (task.isDone) Color.Gray
                                                                else priorityColor,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        textDecoration =
                                                                if (task.isDone)
                                                                        TextDecoration.LineThrough
                                                                else null,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (task.alarmLevel != AlarmLevel.MEDIUM) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Icon(
                                                                if (task.alarmLevel ==
                                                                                AlarmLevel.VERY_HIGH
                                                                )
                                                                        Icons.Default
                                                                                .NotificationsActive
                                                                else Icons.Default.Notifications,
                                                                contentDescription = "Alarm",
                                                                tint =
                                                                        if (task.alarmLevel ==
                                                                                        AlarmLevel
                                                                                                .VERY_HIGH
                                                                        )
                                                                                Color.Red
                                                                        else Color.White,
                                                                modifier = Modifier.size(14.dp)
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
                                                        modifier = Modifier.padding(top = 0.dp)
                                                ) {
                                                        if (task.dueDate != null) {
                                                                Icon(
                                                                        Icons.Default.AccessTime,
                                                                        contentDescription = null,
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        10.dp
                                                                                ),
                                                                        tint = Color.Gray
                                                                )
                                                                Spacer(Modifier.width(4.dp))
                                                                val now = LocalDateTime.now()
                                                                val isToday =
                                                                        task.dueDate
                                                                                .toLocalDate() ==
                                                                                now.toLocalDate()
                                                                val isPast =
                                                                        task.dueDate.isBefore(now)
                                                                val isThisWeek =
                                                                        task.dueDate.isBefore(
                                                                                now.plusDays(7)
                                                                        ) && !isToday

                                                                val dueDateColor =
                                                                        when {
                                                                                task.isDone ->
                                                                                        Color.Gray
                                                                                isPast -> Color.Red
                                                                                isToday ->
                                                                                        Color(
                                                                                                0xFF4CAF50
                                                                                        ) // Green
                                                                                isThisWeek ->
                                                                                        Color.White
                                                                                else -> Color.Gray
                                                                        }

                                                                Text(
                                                                        text =
                                                                                task.dueDate.format(
                                                                                        java.time
                                                                                                .format
                                                                                                .DateTimeFormatter
                                                                                                .ofPattern(
                                                                                                        "EEEE dd MMM, HH:mm",
                                                                                                        java.util
                                                                                                                .Locale
                                                                                                                .FRENCH
                                                                                                )
                                                                                ),
                                                                        color = dueDateColor,
                                                                        fontSize = 11.sp
                                                                )
                                                        }

                                                        if (task.type == TaskType.REPETITIVE) {
                                                                Spacer(Modifier.width(6.dp))
                                                                Icon(
                                                                        Icons.Default.Sync,
                                                                        contentDescription =
                                                                                "Répétitive",
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        10.dp
                                                                                ),
                                                                        tint =
                                                                                Color(
                                                                                        0xFF4CAF50
                                                                                ) // Vert
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
                                                                if (folderName.isNotBlank()) {
                                                                        Spacer(Modifier.width(6.dp))
                                                                        Text(
                                                                                text =
                                                                                        "• $folderName",
                                                                                color = Secondary,
                                                                                fontSize = 10.sp,
                                                                                maxLines = 1,
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
                                                }
                                        }
                                }

                                Column(
                                        modifier =
                                                Modifier.fillMaxHeight().padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(32.dp).clickable {
                                                                onSelect(task.id)
                                                        },
                                                contentAlignment = Alignment.TopCenter
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(18.dp)
                                                                        .border(
                                                                                1.dp,
                                                                                if (isSelected)
                                                                                        Secondary
                                                                                else
                                                                                        Color.Gray
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.5f
                                                                                                ),
                                                                                CircleShape
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        if (isSelected) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(10.dp)
                                                                                        .background(
                                                                                                Secondary,
                                                                                                CircleShape
                                                                                        )
                                                                )
                                                        }
                                                }
                                        }

                                        IconButton(
                                                onClick = { showMenu = true },
                                                modifier = Modifier.size(24.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                        }
                                }

                                if (task.isDone) {
                                        Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Effectuée",
                                                tint = Secondary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                        }
                }

                DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MidnightGreen, // Vert nuit moderne
                        offset =
                                androidx.compose.ui.unit.DpOffset(
                                        x = (-8).dp,
                                        y = 0.dp
                                ) // Rapproché des 3 pts
                ) {
                        DropdownMenuItem(
                                text = { Text("Reporter", color = Color.White) },
                                onClick = {
                                        onPostpone(task)
                                        showMenu = false
                                },
                                leadingIcon = {
                                        Icon(
                                                Icons.Default.Update,
                                                contentDescription = null,
                                                tint = Secondary
                                        )
                                }
                        )
                        DropdownMenuItem(
                                text = { Text("Modifier", color = Color.White) },
                                onClick = {
                                        onEdit(task)
                                        showMenu = false
                                },
                                leadingIcon = {
                                        Icon(
                                                Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = Secondary
                                        )
                                }
                        )
                        DropdownMenuItem(
                                text = { Text("Supprimer", color = Color.Red) },
                                onClick = {
                                        onDelete(task)
                                        showMenu = false
                                },
                                leadingIcon = {
                                        Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = Color.Red
                                        )
                                }
                        )
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
                        Priority,
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
        var text by remember { mutableStateOf(task?.label ?: "") }
        var selectedFolderId by remember { mutableStateOf(task?.folderId ?: initialFolderId) }
        var priority by remember { mutableStateOf(task?.priority ?: Priority.MEDIUM) }
        var alarmLevel by remember { mutableStateOf(task?.alarmLevel ?: AlarmLevel.MEDIUM) }
        var taskType by remember { mutableStateOf(task?.type ?: TaskType.ONCE) }
        var nValue by remember { mutableStateOf(task?.repeatInterval?.toString() ?: "1") }
        var repeatUnit by remember { mutableStateOf(task?.repeatUnit ?: RepeatUnit.D) }

        var currentDueDate by remember { mutableStateOf(task?.dueDate) }

        var warningInterval by remember {
                mutableStateOf(task?.warningInterval?.toString() ?: "15")
        }
        var warningUnit by remember { mutableStateOf(task?.warningUnit ?: RepeatUnit.MINUTES) }
        var warningRepeatInterval by remember {
                mutableStateOf(task?.warningRepeatInterval?.toString() ?: "")
        }
        var warningRepeatUnit by remember {
                mutableStateOf(task?.warningRepeatUnit ?: RepeatUnit.MINUTES)
        }

        val context = androidx.compose.ui.platform.LocalContext.current

        // Update currentDueDate based on text parsing in real-time
        LaunchedEffect(text) {
                if (text.isNotBlank()) {
                        val (_, parsedDate) = SmartParser.parse(text)
                        currentDueDate = parsedDate
                } else if (task == null) {
                        currentDueDate = null
                }
        }

        AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                        Text(
                                if (task == null) "Nouvelle Tâche" else "Modifier Tâche",
                                fontWeight = FontWeight.Bold
                        )
                },
                text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                TextField(
                                        value = text,
                                        onValueChange = { text = it },
                                        placeholder = { Text("Ex: RDV à 10h30") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                TextFieldDefaults.colors(
                                                        focusedContainerColor = SurfaceVariant,
                                                        unfocusedContainerColor = SurfaceVariant,
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White
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
                                                Spacer(Modifier.width(8.dp))
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
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column {
                                                Text(
                                                        "Priorité",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                )
                                                Row(modifier = Modifier.padding(top = 8.dp)) {
                                                        Priority.entries.forEach { p ->
                                                                val pColor =
                                                                        when (p) {
                                                                                Priority.LOW ->
                                                                                        PriorityLow
                                                                                Priority.MEDIUM ->
                                                                                        PriorityMedium
                                                                                Priority.HIGH ->
                                                                                        PriorityHigh
                                                                        }
                                                                Box(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                                end =
                                                                                                        12.dp
                                                                                        )
                                                                                        .size(
                                                                                                if (priority ==
                                                                                                                p
                                                                                                )
                                                                                                        28.dp
                                                                                                else
                                                                                                        24.dp
                                                                                        )
                                                                                        .background(
                                                                                                pColor,
                                                                                                CircleShape
                                                                                        )
                                                                                        .border(
                                                                                                width =
                                                                                                        if (priority ==
                                                                                                                        p
                                                                                                        )
                                                                                                                2.dp
                                                                                                        else
                                                                                                                0.dp,
                                                                                                color =
                                                                                                        Color.White,
                                                                                                shape =
                                                                                                        CircleShape
                                                                                        )
                                                                                        .clickable {
                                                                                                priority =
                                                                                                        p
                                                                                        }
                                                                )
                                                        }
                                                }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                                Text("Alarme", fontSize = 12.sp, color = Color.Gray)
                                                Row(modifier = Modifier.padding(top = 8.dp)) {
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
                                                                                        28.dp
                                                                        }
                                                                Box(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                                start =
                                                                                                        8.dp
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
                                                                                                if (alarmLevel ==
                                                                                                                level
                                                                                                )
                                                                                                        Secondary
                                                                                                else
                                                                                                        Color.Gray,
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
                                                fontWeight = FontWeight.Medium
                                        )
                                }
                                        ?: Text(
                                                "Aucune échéance définie",
                                                fontSize = 13.sp,
                                                color = Color.Gray,
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 4.dp)
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
                                                        val now =
                                                                currentDueDate
                                                                        ?: LocalDateTime.now()
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
                                                                                                        now.hour,
                                                                                                        now.minute,
                                                                                                        true
                                                                                                )
                                                                                timePicker.show()
                                                                        },
                                                                        now.year,
                                                                        now.monthValue - 1,
                                                                        now.dayOfMonth
                                                                )
                                                        datePicker.show()
                                                }
                                        ) {
                                                Icon(
                                                        Icons.Default.AccessTime,
                                                        contentDescription =
                                                                "Choisir date et heure",
                                                        tint = Secondary
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
                                                                                SurfaceVariant,
                                                                        unfocusedContainerColor =
                                                                                SurfaceVariant,
                                                                        focusedTextColor =
                                                                                Color.White,
                                                                        unfocusedTextColor =
                                                                                Color.White
                                                                )
                                                )
                                                Spacer(Modifier.width(8.dp))
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
                                                                        SurfaceVariant,
                                                                unfocusedContainerColor =
                                                                        SurfaceVariant,
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White
                                                        )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        repeatUnitDropdown(
                                                selectedUnit = warningUnit,
                                                onUnitSelected = { warningUnit = it }
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
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
                                                placeholder = { Text("0", fontSize = 12.sp) },
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
                                                                        SurfaceVariant,
                                                                unfocusedContainerColor =
                                                                        SurfaceVariant,
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.White
                                                        )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        repeatUnitDropdown(
                                                selectedUnit = warningRepeatUnit,
                                                onUnitSelected = { warningRepeatUnit = it }
                                        )
                                }
                        }
                },
                confirmButton = {
                        Button(
                                onClick = {
                                        if (text.isNotBlank()) {
                                                val (cleanLabel, parsedDate) =
                                                        SmartParser.parse(text)
                                                val finalLabel =
                                                        if (cleanLabel.isNotBlank()) cleanLabel
                                                        else text
                                                val finalDate = currentDueDate ?: parsedDate

                                                onAdd(
                                                        finalLabel,
                                                        selectedFolderId,
                                                        priority,
                                                        alarmLevel,
                                                        taskType,
                                                        nValue.toIntOrNull(),
                                                        repeatUnit,
                                                        warningInterval.toIntOrNull() ?: 15,
                                                        warningUnit,
                                                        warningRepeatInterval.toIntOrNull(),
                                                        warningRepeatUnit,
                                                        finalDate
                                                )
                                        }
                                },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Secondary,
                                                contentColor = Black
                                        )
                        ) { Text(if (task == null) "Ajouter" else "Sauvegarder") }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text("Annuler", color = Color.Gray) }
                },
                containerColor = DarkGray,
                titleContentColor = Color.White,
                textContentColor = Color.White
        )
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
                        containerColor = Color(0xFF303030) // Gris plus clair
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
                                placeholder = { Text("Nom du dossier") },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor = SurfaceVariant,
                                                unfocusedContainerColor = SurfaceVariant,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                        )
                        )
                },
                confirmButton = {
                        Button(
                                onClick = { if (text.isNotBlank()) onAdd(text) },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = Secondary,
                                                contentColor = Black
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
