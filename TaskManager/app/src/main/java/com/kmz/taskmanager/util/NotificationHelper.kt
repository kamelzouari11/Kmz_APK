package com.kmz.taskmanager.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kmz.taskmanager.MainActivity
import com.kmz.taskmanager.R
import com.kmz.taskmanager.data.AlarmLevel
import com.kmz.taskmanager.data.Task
import java.time.ZoneId

object NotificationHelper {
        private const val CHANNEL_ID = "task_reminders"
        private const val ALARM_CHANNEL_ID = "task_alarms"

        fun createNotificationChannel(context: Context) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val notificationManager: NotificationManager =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                        NotificationManager

                        // 1. Reminder Channel (Normal)
                        val name = "Rappels de tâches"
                        val descriptionText = "Notifications pour vos tâches à venir"
                        val importance = NotificationManager.IMPORTANCE_HIGH
                        val soundUri =
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                        val channel =
                                NotificationChannel(CHANNEL_ID, name, importance).apply {
                                        description = descriptionText
                                        setSound(
                                                soundUri,
                                                android.media.AudioAttributes.Builder()
                                                        .setUsage(
                                                                android.media.AudioAttributes
                                                                        .USAGE_NOTIFICATION
                                                        )
                                                        .setContentType(
                                                                android.media.AudioAttributes
                                                                        .CONTENT_TYPE_SONIFICATION
                                                        )
                                                        .build()
                                        )
                                        enableVibration(true)
                                        vibrationPattern = longArrayOf(0, 500, 1000)
                                        lockscreenVisibility =
                                                android.app.Notification.VISIBILITY_PUBLIC
                                        setShowBadge(true)
                                }
                        notificationManager.createNotificationChannel(channel)

                        // 2. Alarm Channel (High priority)
                        val alarmName = "Alarmes de tâches"
                        val alarmDesc = "Sonneries de type réveil pour les tâches urgentes"
                        val alarmImportance = NotificationManager.IMPORTANCE_HIGH
                        val alarmSoundUri =
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

                        val alarmChannel =
                                NotificationChannel(ALARM_CHANNEL_ID, alarmName, alarmImportance)
                                        .apply {
                                                description = alarmDesc
                                                setSound(
                                                        alarmSoundUri,
                                                        android.media.AudioAttributes.Builder()
                                                                .setUsage(
                                                                        android.media
                                                                                .AudioAttributes
                                                                                .USAGE_ALARM
                                                                )
                                                                .setContentType(
                                                                        android.media
                                                                                .AudioAttributes
                                                                                .CONTENT_TYPE_SONIFICATION
                                                                )
                                                                .build()
                                                )
                                                enableVibration(true)
                                                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                                                lockscreenVisibility =
                                                        android.app.Notification.VISIBILITY_PUBLIC
                                                setShowBadge(true)
                                                enableLights(true)
                                                lightColor = android.graphics.Color.RED
                                        }
                        notificationManager.createNotificationChannel(alarmChannel)
                }
        }

        fun scheduleTaskAlarm(context: Context, task: Task) {
                if (task.dueDate == null || task.isDone) return

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val alarmIntent =
                        Intent(context, TaskReminderReceiver::class.java).apply {
                                putExtra("TASK_LABEL", task.label)
                                putExtra("TASK_ID", task.id)
                                putExtra("ALARM_LEVEL", task.alarmLevel.name)
                                putExtra("TYPE", "ALARM")
                        }

                val pendingIntent =
                        PendingIntent.getBroadcast(
                                context,
                                task.id.toInt(),
                                alarmIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                // Main Alarm trigger
                val triggerAt =
                        task.dueDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                scheduleAlarm(alarmManager, triggerAt, pendingIntent)

                // Schedule Warning if applicable
                scheduleWarning(context, task)
        }

        private fun scheduleAlarm(
                alarmManager: AlarmManager,
                triggerAt: Long,
                pendingIntent: PendingIntent
        ) {
                try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (alarmManager.canScheduleExactAlarms()) {
                                        alarmManager.setExactAndAllowWhileIdle(
                                                AlarmManager.RTC_WAKEUP,
                                                triggerAt,
                                                pendingIntent
                                        )
                                } else {
                                        alarmManager.set(
                                                AlarmManager.RTC_WAKEUP,
                                                triggerAt,
                                                pendingIntent
                                        )
                                }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        triggerAt,
                                        pendingIntent
                                )
                        } else {
                                alarmManager.setExact(
                                        AlarmManager.RTC_WAKEUP,
                                        triggerAt,
                                        pendingIntent
                                )
                        }
                } catch (e: SecurityException) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
        }

        fun scheduleWarning(
                context: Context,
                task: Task,
                fromTime: java.time.LocalDateTime? = null
        ) {
                if (task.dueDate == null || task.isDone) return

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                // Calculate warning time: dueDate - warningInterval
                val warningBase =
                        task.dueDate.minus(
                                task.warningInterval.toLong(),
                                when (task.warningUnit) {
                                        com.kmz.taskmanager.data.RepeatUnit.MINUTES ->
                                                java.time.temporal.ChronoUnit.MINUTES
                                        com.kmz.taskmanager.data.RepeatUnit.HOURS ->
                                                java.time.temporal.ChronoUnit.HOURS
                                        com.kmz.taskmanager.data.RepeatUnit.D ->
                                                java.time.temporal.ChronoUnit.DAYS
                                        com.kmz.taskmanager.data.RepeatUnit.W ->
                                                java.time.temporal.ChronoUnit.WEEKS
                                        com.kmz.taskmanager.data.RepeatUnit.M ->
                                                java.time.temporal.ChronoUnit.MONTHS
                                        com.kmz.taskmanager.data.RepeatUnit.Y ->
                                                java.time.temporal.ChronoUnit.YEARS
                                }
                        )

                var nextWarningTime = fromTime ?: warningBase

                // If fromTime is provided (for repetition), calculate next point
                if (fromTime != null &&
                                task.warningRepeatInterval != null &&
                                task.warningRepeatUnit != null
                ) {
                        nextWarningTime =
                                fromTime.plus(
                                        task.warningRepeatInterval.toLong(),
                                        when (task.warningRepeatUnit) {
                                                com.kmz.taskmanager.data.RepeatUnit.MINUTES ->
                                                        java.time.temporal.ChronoUnit.MINUTES
                                                com.kmz.taskmanager.data.RepeatUnit.HOURS ->
                                                        java.time.temporal.ChronoUnit.HOURS
                                                com.kmz.taskmanager.data.RepeatUnit.D ->
                                                        java.time.temporal.ChronoUnit.DAYS
                                                com.kmz.taskmanager.data.RepeatUnit.W ->
                                                        java.time.temporal.ChronoUnit.WEEKS
                                                com.kmz.taskmanager.data.RepeatUnit.M ->
                                                        java.time.temporal.ChronoUnit.MONTHS
                                                com.kmz.taskmanager.data.RepeatUnit.Y ->
                                                        java.time.temporal.ChronoUnit.YEARS
                                        }
                                )
                }

                // Don't schedule if warning is after or at dueDate, or if it's in the past
                if (nextWarningTime.isAfter(task.dueDate) || nextWarningTime.isEqual(task.dueDate))
                        return
                if (nextWarningTime.isBefore(java.time.LocalDateTime.now())) return

                val intent =
                        Intent(context, TaskReminderReceiver::class.java).apply {
                                putExtra("TASK_LABEL", task.label)
                                putExtra("TASK_ID", task.id)
                                putExtra("TYPE", "WARNING")
                        }

                val pendingIntent =
                        PendingIntent.getBroadcast(
                                context,
                                task.id.toInt() + 1000000,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                val triggerAt =
                        nextWarningTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                scheduleAlarm(alarmManager, triggerAt, pendingIntent)
        }

        fun cancelTaskAlarm(context: Context, task: Task) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                // Cancel Main Alarm
                val alarmIntent = Intent(context, TaskReminderReceiver::class.java)
                val alarmPI =
                        PendingIntent.getBroadcast(
                                context,
                                task.id.toInt(),
                                alarmIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                alarmManager.cancel(alarmPI)

                // Cancel Warning
                val warningIntent = Intent(context, TaskReminderReceiver::class.java)
                val warningPI =
                        PendingIntent.getBroadcast(
                                context,
                                task.id.toInt() + 1000000,
                                warningIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                alarmManager.cancel(warningPI)
        }

        fun showNotification(
                context: Context,
                label: String,
                taskId: Long,
                alarmLevel: AlarmLevel,
                type: String = "ALARM",
                isLate: Boolean = false
        ) {
                val intent =
                        Intent(context, MainActivity::class.java).apply {
                                flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                val pendingIntent =
                        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

                val useAlarmChannel =
                        (type == "ALARM" &&
                                (alarmLevel == AlarmLevel.VERY_HIGH ||
                                        alarmLevel == AlarmLevel.HIGH)) || isLate
                val currentChannelId = if (useAlarmChannel) ALARM_CHANNEL_ID else CHANNEL_ID

                val soundUri =
                        RingtoneManager.getDefaultUri(
                                if (useAlarmChannel) RingtoneManager.TYPE_ALARM
                                else RingtoneManager.TYPE_NOTIFICATION
                        )

                val builder =
                        NotificationCompat.Builder(context, currentChannelId)
                                .setSmallIcon(
                                        if (isLate) android.R.drawable.stat_notify_error
                                        else android.R.drawable.ic_lock_idle_alarm
                                )
                                .setContentTitle(
                                        when {
                                                isLate -> "🚨 EN RETARD : $label"
                                                type == "WARNING" -> "Avertissement : $label"
                                                else -> "⏰ C'est l'heure : $label"
                                        }
                                )
                                .setContentText(
                                        if (isLate) "Cette tâche est dépassée !"
                                        else if (type == "WARNING") "La tâche arrive bientôt"
                                        else label
                                )
                                .setColor(
                                        if (isLate) android.graphics.Color.RED else 0x4CAF50
                                ) // Vert TaskManager
                                .setPriority(
                                        if (isLate || alarmLevel == AlarmLevel.VERY_HIGH)
                                                NotificationCompat.PRIORITY_MAX
                                        else NotificationCompat.PRIORITY_HIGH
                                )
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true)
                                .setSound(soundUri)
                                .setVibrate(
                                        if (useAlarmChannel) longArrayOf(0, 1000, 500, 1000)
                                        else longArrayOf(0, 500, 1000)
                                )
                                .setCategory(
                                        if (useAlarmChannel) NotificationCompat.CATEGORY_ALARM
                                        else NotificationCompat.CATEGORY_EVENT
                                )
                                .setDefaults(
                                        NotificationCompat.DEFAULT_LIGHTS
                                ) // Don't use DEFAULT_ALL as we set sound/vibrate manually for
                                // Alarms
                                .setOnlyAlertOnce(false)

                // Use insistent flag for high priority levels to make it ring like an alarm
                val notification = builder.build()
                if (useAlarmChannel) {
                        notification.flags =
                                notification.flags or android.app.Notification.FLAG_INSISTENT
                }

                val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                NotificationManager

                val notificationId =
                        if (type == "WARNING") taskId.toInt() + 1000000 else taskId.toInt()
                notificationManager.notify(notificationId, notification)
        }
}
