package com.kmz.taskmanager.util

import android.content.Context
import android.os.Environment
import com.kmz.taskmanager.data.AlarmLevel
import com.kmz.taskmanager.data.Folder
import com.kmz.taskmanager.data.Priority
import com.kmz.taskmanager.data.RepeatUnit
import com.kmz.taskmanager.data.Task
import com.kmz.taskmanager.data.TaskType
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

object DataManagementHelper {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun exportData(context: Context, tasks: List<Task>, folders: List<Folder>): String {
        return try {
            val root = JSONObject()

            val foldersArray = JSONArray()
            folders.forEach { folder ->
                val f = JSONObject()
                f.put("id", folder.id)
                f.put("name", folder.name)
                foldersArray.put(f)
            }
            root.put("folders", foldersArray)

            val tasksArray = JSONArray()
            tasks.forEach { task ->
                val t = JSONObject()
                t.put("label", task.label)
                t.put("folderId", task.folderId)
                t.put("type", task.type.name)
                t.put("dueDate", task.dueDate?.format(formatter))
                t.put("alarmLevel", task.alarmLevel.name)
                t.put("priority", task.priority.name)
                t.put("isDone", task.isDone)
                t.put("repeatInterval", task.repeatInterval)
                t.put("repeatUnit", task.repeatUnit?.name)
                t.put("warningInterval", task.warningInterval)
                t.put("warningUnit", task.warningUnit.name)
                t.put("warningRepeatInterval", task.warningRepeatInterval)
                t.put("warningRepeatUnit", task.warningRepeatUnit?.name)
                tasksArray.put(t)
            }
            root.put("tasks", tasksArray)

            val jsonContent = root.toString(4)
            // Use standard public Downloads folder
            val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val file = File(downloadDir, "TaskManager_Backup.json")
            file.writeText(jsonContent)
            "Sauvegardé dans Download/TaskManager_Backup.json"
        } catch (e: Exception) {
            "Erreur: ${e.message}"
        }
    }

    fun importData(context: Context): Pair<List<Folder>, List<Task>>? {
        return try {
            val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "TaskManager_Backup.json")
            if (!file.exists()) return null

            val content = file.readText()
            val root = JSONObject(content)

            val folders = mutableListOf<Folder>()
            val foldersArray = root.getJSONArray("folders")
            for (i in 0 until foldersArray.length()) {
                val f = foldersArray.getJSONObject(i)
                folders.add(Folder(id = f.getLong("id"), name = f.getString("name")))
            }

            val tasks = mutableListOf<Task>()
            val tasksArray = root.getJSONArray("tasks")
            for (i in 0 until tasksArray.length()) {
                val t = tasksArray.getJSONObject(i)
                tasks.add(
                        Task(
                                folderId = t.getLong("folderId"),
                                label = t.getString("label"),
                                type = TaskType.valueOf(t.getString("type")),
                                dueDate =
                                        if (t.isNull("dueDate")) null
                                        else LocalDateTime.parse(t.getString("dueDate"), formatter),
                                alarmLevel = AlarmLevel.valueOf(t.getString("alarmLevel")),
                                priority = Priority.valueOf(t.getString("priority")),
                                isDone = t.getBoolean("isDone"),
                                repeatInterval =
                                        if (t.isNull("repeatInterval")) null
                                        else t.getInt("repeatInterval"),
                                repeatUnit =
                                        if (t.isNull("repeatUnit")) null
                                        else RepeatUnit.valueOf(t.getString("repeatUnit")),
                                warningInterval = t.optInt("warningInterval", 15),
                                warningUnit =
                                        if (t.isNull("warningUnit")) RepeatUnit.MINUTES
                                        else RepeatUnit.valueOf(t.getString("warningUnit")),
                                warningRepeatInterval =
                                        if (t.isNull("warningRepeatInterval")) null
                                        else t.getInt("warningRepeatInterval"),
                                warningRepeatUnit =
                                        if (t.isNull("warningRepeatUnit")) null
                                        else RepeatUnit.valueOf(t.getString("warningRepeatUnit"))
                        )
                )
            }
            Pair(folders, tasks)
        } catch (e: Exception) {
            null
        }
    }
}
