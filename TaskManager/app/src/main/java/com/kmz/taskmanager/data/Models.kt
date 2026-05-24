package com.kmz.taskmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class TaskType {
    ONCE,
    REPETITIVE
}

enum class AlarmLevel {
    MEDIUM,
    HIGH,
    VERY_HIGH
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

enum class RepeatUnit {
    MINUTES,
    HOURS,
    D,
    W,
    M,
    Y
}

@Entity(tableName = "tasks")
data class Task(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val folderId: Long,
        val label: String,
        val type: TaskType = TaskType.ONCE,
        val dueDate: LocalDateTime?,
        val alarmLevel: AlarmLevel = AlarmLevel.MEDIUM,
        val priority: Priority = Priority.MEDIUM,
        val isDone: Boolean = false,
        val repeatInterval: Int? = null,
        val repeatUnit: RepeatUnit? = null,
        val warningInterval: Int = 15,
        val warningUnit: RepeatUnit = RepeatUnit.MINUTES,
        val warningRepeatInterval: Int? = null,
        val warningRepeatUnit: RepeatUnit? = null,
        val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity(tableName = "folders")
data class Folder(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val name: String,
        val color: Int? = null
)
