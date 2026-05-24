package com.kmz.taskmanager.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kmz.taskmanager.data.AlarmLevel
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("TASK_LABEL") ?: "Tâche à faire"
        val taskId = intent.getLongExtra("TASK_ID", 0L)
        val alarmLevelStr = intent.getStringExtra("ALARM_LEVEL") ?: AlarmLevel.MEDIUM.name
        val alarmLevel = AlarmLevel.valueOf(alarmLevelStr)
        val type = intent.getStringExtra("TYPE") ?: "ALARM"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.kmz.taskmanager.data.AppDatabase.getDatabase(context)
                val task = db.taskDao().getTaskById(taskId)

                var isLate = false
                if (type == "ALARM" && task != null && task.dueDate != null) {
                    // Buffer of 60 seconds to avoid false positives due to system delay
                    if (LocalDateTime.now().isAfter(task.dueDate.plusSeconds(60))) {
                        isLate = true
                    }
                }

                NotificationHelper.showNotification(
                        context,
                        label,
                        taskId,
                        alarmLevel,
                        type,
                        isLate
                )

                // Handle warning repetition or rescheduling
                if (type == "WARNING" && task != null && !task.isDone) {
                    NotificationHelper.scheduleWarning(context, task, LocalDateTime.now())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
