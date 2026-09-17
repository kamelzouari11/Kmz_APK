package com.kmz.taskmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kmz.taskmanager.data.*
import com.kmz.taskmanager.util.BackupUtils
import com.kmz.taskmanager.util.DataManagementHelper
import com.kmz.taskmanager.util.NotificationHelper
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val taskDao = AppDatabase.getDatabase(application).taskDao()

    val tasks = taskDao.getAllTasks()
    val folders = taskDao.getAllFolders()

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = taskDao.insertTask(task)
            NotificationHelper.scheduleTaskAlarm(getApplication(), task.copy(id = id))
        }
    }

    fun addFolder(name: String) {
        viewModelScope.launch { taskDao.insertFolder(Folder(name = name)) }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch { taskDao.updateFolder(folder) }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { taskDao.deleteFolder(folder) }
    }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch {
            val newIsDone = !task.isDone
            val updatedTask = task.copy(isDone = newIsDone)
            taskDao.updateTask(updatedTask)

            if (newIsDone) {
                NotificationHelper.cancelTaskAlarm(getApplication(), task)
            } else {
                NotificationHelper.scheduleTaskAlarm(getApplication(), updatedTask)
            }

            if (newIsDone &&
                            task.type == TaskType.REPETITIVE &&
                            task.dueDate != null &&
                            task.repeatInterval != null &&
                            task.repeatUnit != null
            ) {
                val nextDueDate =
                        when (task.repeatUnit) {
                            RepeatUnit.MINUTES ->
                                    task.dueDate.plusMinutes(task.repeatInterval.toLong())
                            RepeatUnit.HOURS -> task.dueDate.plusHours(task.repeatInterval.toLong())
                            RepeatUnit.D -> task.dueDate.plusDays(task.repeatInterval.toLong())
                            RepeatUnit.W -> task.dueDate.plusWeeks(task.repeatInterval.toLong())
                            RepeatUnit.M -> task.dueDate.plusMonths(task.repeatInterval.toLong())
                            RepeatUnit.Y -> task.dueDate.plusYears(task.repeatInterval.toLong())
                        }
                val newTask =
                        task.copy(
                                id = 0,
                                isDone = false,
                                dueDate = nextDueDate,
                                createdAt = LocalDateTime.now()
                        )
                val newId = taskDao.insertTask(newTask)
                NotificationHelper.scheduleTaskAlarm(getApplication(), newTask.copy(id = newId))
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
            NotificationHelper.cancelTaskAlarm(getApplication(), task)
        }
    }

    fun postponeTask(task: Task, newDueDate: LocalDateTime) {
        viewModelScope.launch {
            val updatedTask = task.copy(dueDate = newDueDate)
            taskDao.updateTask(updatedTask)
            NotificationHelper.scheduleTaskAlarm(getApplication(), updatedTask)
        }
    }

    fun filterTasks(
        list: List<Task>,
        viewType: ViewType,
        folderId: Long? = null,
        searchQuery: String = ""
    ): List<Task> {
        val now = LocalDate.now()
        val trimmedQuery = searchQuery.trim()
        val folderFiltered =
                if (folderId != null && folderId != 0L) {
                    list.filter { it.folderId == folderId }
                } else {
                    list
                }
        val textFiltered =
                if (trimmedQuery.isEmpty()) {
                    folderFiltered
                } else {
                    folderFiltered.filter { task ->
                        task.label.contains(trimmedQuery, ignoreCase = true)
                    }
                }

        val filtered =
                when (viewType) {
                    ViewType.ALL -> textFiltered
                    ViewType.TODAY ->
                            textFiltered.filter {
                                it.dueDate?.toLocalDate()?.let { date ->
                                    !date.isAfter(now)
                                }
                                        ?: false
                            }
                    ViewType.WEEK ->
                            textFiltered.filter {
                                it.dueDate?.toLocalDate()?.let { date ->
                                    date.isAfter(now) && !date.isAfter(now.plusDays(7))
                                }
                                        ?: false
                            }
                    ViewType.MONTH ->
                            textFiltered.filter {
                                it.dueDate?.toLocalDate()?.let { date ->
                                    date.isAfter(now.plusDays(7)) &&
                                            !date.isAfter(now.plusMonths(1))
                                }
                                        ?: false
                            }
                    ViewType.YEAR ->
                            textFiltered.filter {
                                it.dueDate?.toLocalDate()?.let { date ->
                                    date.isAfter(now.plusMonths(1)) &&
                                            !date.isAfter(now.plusYears(1))
                                }
                                        ?: false
                            }
                    ViewType.LATER ->
                            textFiltered.filter {
                                it.dueDate == null ||
                                        it.dueDate.toLocalDate().isAfter(now.plusYears(1))
                            }
                }

        return if (viewType == ViewType.ALL) {
            filtered.sortedBy { it.dueDate ?: LocalDateTime.MAX }
        } else {
            filtered.sortedWith(
                    compareBy<Task> { it.isDone }.thenBy { it.dueDate ?: LocalDateTime.MAX }
            )
        }
    }

    fun deleteTasks(tasksToDelete: List<Task>) {
        viewModelScope.launch {
            tasksToDelete.forEach { task ->
                taskDao.deleteTask(task)
                NotificationHelper.cancelTaskAlarm(getApplication(), task)
            }
        }
    }

    fun moveTasksToFolder(taskIds: List<Long>, folderId: Long) {
        viewModelScope.launch {
            taskIds.forEach { id ->
                val task = taskDao.getTaskById(id)
                if (task != null) {
                    val updatedTask = task.copy(folderId = folderId)
                    taskDao.updateTask(updatedTask)
                }
            }
        }
    }

    fun backupDataCloud(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val allTasks = taskDao.getAllTasksSync()
            val allFolders = taskDao.getAllFoldersSync()
            val json = DataManagementHelper.getJson(allTasks, allFolders)
            BackupUtils.saveToCloud(getApplication(), json)
            onResult("Sync Cloud en cours...")
        }
    }

    fun restoreDataCloud(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = BackupUtils.fetchFromCloud(getApplication())
            if (json != null) {
                val data = DataManagementHelper.parseJson(json)
                if (data != null) {
                    applyImportedData(data, onResult)
                } else {
                    onResult("Erreur format Cloud")
                }
            }
        }
    }

    private suspend fun applyImportedData(data: Pair<List<Folder>, List<Task>>, onResult: (String) -> Unit) {
        taskDao.nukeTasks()
        taskDao.nukeFolders()

        val folderIdMap = mutableMapOf<Long, Long>()
        data.first.forEach { folder ->
            val oldId = folder.id
            val newId = taskDao.insertFolder(folder.copy(id = 0))
            folderIdMap[oldId] = newId
        }

        data.second.forEach { task ->
            val newFolderId = folderIdMap[task.folderId] ?: 0L
            val newTask = task.copy(id = 0, folderId = newFolderId)
            val insertedId = taskDao.insertTask(newTask)
            if (!newTask.isDone) {
                NotificationHelper.scheduleTaskAlarm(
                    getApplication(),
                    newTask.copy(id = insertedId)
                )
            }
        }
        onResult("Données restaurées")
    }
}

enum class ViewType {
    ALL,
    TODAY,
    WEEK,
    MONTH,
    YEAR,
    LATER
}
