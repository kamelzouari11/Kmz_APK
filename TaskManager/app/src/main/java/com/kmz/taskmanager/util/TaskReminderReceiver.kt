package com.kmz.taskmanager.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.kmz.taskmanager.data.RepeatUnit
import com.kmz.taskmanager.data.Task
import com.kmz.taskmanager.data.TaskType
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, 0L)
        val type =
                intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE)
                        ?: intent.getStringExtra("TYPE")
                        ?: "ALARM"

        // Stop an insistent alarm immediately. Database work continues asynchronously below.
        if (intent.action in IMMEDIATE_SILENCE_ACTIONS && taskId != 0L) {
            NotificationHelper.cancelDisplayedNotifications(context, taskId)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.kmz.taskmanager.data.AppDatabase.getDatabase(context)
                val dao = db.taskDao()
                val task = dao.getTaskById(taskId) ?: return@launch

                when (intent.action) {
                    NotificationHelper.ACTION_SILENCE -> {
                        if (!task.isDone) {
                            NotificationHelper.showNotification(context, task, type, silent = true)
                        }
                    }

                    NotificationHelper.ACTION_SNOOZE -> {
                        val hours =
                                intent.getLongExtra(NotificationHelper.EXTRA_POSTPONE_HOURS, 0L)
                                        .coerceAtLeast(0L)
                        val days =
                                intent.getLongExtra(NotificationHelper.EXTRA_POSTPONE_DAYS, 0L)
                                        .coerceAtLeast(0L)
                        val months =
                                intent.getLongExtra(NotificationHelper.EXTRA_POSTPONE_MONTHS, 0L)
                                        .coerceAtLeast(0L)
                        val delay =
                                intent.getStringExtra(NotificationHelper.EXTRA_POSTPONE_DELAY)
                                        ?: RemoteInput.getResultsFromIntent(intent)
                                                ?.getCharSequence(NotificationHelper.REMOTE_INPUT_DELAY)
                                                ?.toString()
                        val newDueDate =
                                if (hours > 0L || days > 0L || months > 0L) {
                                    LocalDateTime.now()
                                            .plusMonths(months)
                                            .plusDays(days)
                                            .plusHours(hours)
                                } else {
                                    delay?.let(::parsePostponeDelay)
                                }
                        if (newDueDate != null) {
                            NotificationHelper.cancelTaskAlarm(context, task)
                            val postponedTask = task.copy(dueDate = newDueDate, isDone = false)
                            dao.updateTask(postponedTask)
                            NotificationHelper.cancelDisplayedNotifications(context, task.id)
                            NotificationHelper.scheduleTaskAlarm(context, postponedTask)
                        } else {
                            NotificationHelper.showNotification(context, task, type, silent = true)
                        }
                    }

                    NotificationHelper.ACTION_DONE -> {
                        NotificationHelper.cancelTaskAlarm(context, task)
                        dao.updateTask(task.copy(isDone = true))
                        NotificationHelper.cancelDisplayedNotifications(context, task.id)
                        createNextOccurrenceIfNeeded(context, task)
                    }

                    NotificationHelper.ACTION_DELETE -> {
                        NotificationHelper.cancelTaskAlarm(context, task)
                        dao.deleteTask(task)
                        NotificationHelper.cancelDisplayedNotifications(context, task.id)
                    }

                    else -> {
                        if (task.isDone) return@launch
                        NotificationHelper.showNotification(context, task, type)

                        if (type == "WARNING") {
                            NotificationHelper.scheduleWarning(context, task, LocalDateTime.now())
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parsePostponeDelay(value: String): LocalDateTime? {
        val match = DELAY_PATTERN.matchEntire(value.trim().lowercase()) ?: return null
        val amount = match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return when (match.groupValues[2]) {
            "", "min" -> LocalDateTime.now().plusMinutes(amount)
            "h" -> LocalDateTime.now().plusHours(amount)
            "j" -> LocalDateTime.now().plusDays(amount)
            "s" -> LocalDateTime.now().plusWeeks(amount)
            "m" -> LocalDateTime.now().plusMonths(amount)
            else -> null
        }
    }

    private suspend fun createNextOccurrenceIfNeeded(context: Context, task: Task) {
        if (task.type != TaskType.REPETITIVE ||
                        task.dueDate == null ||
                        task.repeatInterval == null ||
                        task.repeatUnit == null
        ) {
            return
        }

        val nextDueDate =
                when (task.repeatUnit) {
                    RepeatUnit.MINUTES -> task.dueDate.plusMinutes(task.repeatInterval.toLong())
                    RepeatUnit.HOURS -> task.dueDate.plusHours(task.repeatInterval.toLong())
                    RepeatUnit.D -> task.dueDate.plusDays(task.repeatInterval.toLong())
                    RepeatUnit.W -> task.dueDate.plusWeeks(task.repeatInterval.toLong())
                    RepeatUnit.M -> task.dueDate.plusMonths(task.repeatInterval.toLong())
                    RepeatUnit.Y -> task.dueDate.plusYears(task.repeatInterval.toLong())
                }
        val nextTask =
                task.copy(
                        id = 0,
                        isDone = false,
                        dueDate = nextDueDate,
                        createdAt = LocalDateTime.now()
                )
        val dao = com.kmz.taskmanager.data.AppDatabase.getDatabase(context).taskDao()
        val nextId = dao.insertTask(nextTask)
        NotificationHelper.scheduleTaskAlarm(context, nextTask.copy(id = nextId))
    }

    private companion object {
        val DELAY_PATTERN = Regex("^(\\d+)\\s*(min|h|j|s|m)?$", RegexOption.IGNORE_CASE)
        val IMMEDIATE_SILENCE_ACTIONS =
                setOf(
                        NotificationHelper.ACTION_SILENCE,
                        NotificationHelper.ACTION_SNOOZE,
                        NotificationHelper.ACTION_DONE,
                        NotificationHelper.ACTION_DELETE
                )
    }
}
