package com.kmz.taskmanager.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.kmz.taskmanager.R
import com.kmz.taskmanager.data.Task
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {
        private const val ALERT_CHANNEL_ID = "task_alerts_v3"
        private const val SILENT_CHANNEL_ID = "task_alerts_silent_v3"

        const val ACTION_SNOOZE = "com.kmz.taskmanager.action.SNOOZE"
        const val ACTION_DONE = "com.kmz.taskmanager.action.DONE"
        const val ACTION_DELETE = "com.kmz.taskmanager.action.DELETE"
        const val ACTION_SILENCE = "com.kmz.taskmanager.action.SILENCE"
        const val EXTRA_TASK_ID = "TASK_ID"
        const val EXTRA_NOTIFICATION_TYPE = "NOTIFICATION_TYPE"
        const val EXTRA_POSTPONE_DELAY = "POSTPONE_DELAY_VALUE"
        const val EXTRA_POSTPONE_HOURS = "POSTPONE_HOURS"
        const val EXTRA_POSTPONE_DAYS = "POSTPONE_DAYS"
        const val EXTRA_POSTPONE_MONTHS = "POSTPONE_MONTHS"
        const val REMOTE_INPUT_DELAY = "POSTPONE_DELAY"

        private val dueTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun createNotificationChannel(context: Context) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val notificationManager: NotificationManager =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                        NotificationManager

                        val alarmSoundUri =
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val alertChannel =
                                NotificationChannel(
                                                ALERT_CHANNEL_ID,
                                                "Alertes de tâches",
                                                NotificationManager.IMPORTANCE_HIGH
                                        )
                                        .apply {
                                        description = "Alertes visibles au-dessus des applications"
                                        setSound(
                                                alarmSoundUri,
                                                android.media.AudioAttributes.Builder()
                                                        .setUsage(
                                                                android.media.AudioAttributes
                                                                        .USAGE_ALARM
                                                        )
                                                        .setContentType(
                                                                android.media.AudioAttributes
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
                                        lightColor = android.graphics.Color.rgb(64, 245, 155)
                                }
                        notificationManager.createNotificationChannel(alertChannel)

                        val silentChannel =
                                NotificationChannel(
                                                SILENT_CHANNEL_ID,
                                                "Tâches en sourdine",
                                                NotificationManager.IMPORTANCE_LOW
                                        )
                                        .apply {
                                                description = "Alertes conservées sans son ni vibration"
                                                setSound(null, null)
                                                enableVibration(false)
                                                lockscreenVisibility =
                                                        android.app.Notification.VISIBILITY_PUBLIC
                                                setShowBadge(false)
                                        }
                        notificationManager.createNotificationChannel(silentChannel)
                }
        }

        fun openAlertSoundSettings(context: Context) {
                createNotificationChannel(context)
                val intent =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, ALERT_CHANNEL_ID)
                                }
                        } else {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                        }
                context.startActivity(intent)
        }

        fun openFullScreenAlertSettings(context: Context) {
                val intent =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                }
                        } else {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                        }
                context.startActivity(intent)
        }

        fun scheduleTaskAlarm(context: Context, task: Task) {
                if (task.dueDate == null || task.isDone) return

                if (!task.dueDate.isAfter(LocalDateTime.now())) {
                        if (!isNotificationDisplayed(context, task.id)) {
                                showNotification(context, task)
                        }
                        return
                }

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

        suspend fun reschedulePendingTaskAlarms(context: Context) {
                val taskDao = com.kmz.taskmanager.data.AppDatabase.getDatabase(context).taskDao()
                taskDao.getAllTasksSync()
                        .filter { !it.isDone && it.dueDate != null }
                        .forEach { scheduleTaskAlarm(context, it) }
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
                if (task.dueDate == null || task.isDone || task.warningInterval <= 0) return

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
                task: Task,
                type: String = "ALARM",
                silent: Boolean = false
        ) {
                createNotificationChannel(context)
                val taskId = task.id
                val silenceIntent = actionPendingIntent(context, ACTION_SILENCE, taskId, type, 10)
                val postponeIntent = postponePendingIntent(context, taskId, type, task.label)
                val doneIntent = actionPendingIntent(context, ACTION_DONE, taskId, type, 30)
                val deleteIntent = actionPendingIntent(context, ACTION_DELETE, taskId, type, 40)

                val channelId = if (silent) SILENT_CHANNEL_ID else ALERT_CHANNEL_ID
                val dueText = task.dueDate?.format(dueTimeFormatter) ?: "--:--"
                val fullScreenIntent =
                        Intent(context, TaskAlarmActivity::class.java).apply {
                                putExtra(EXTRA_TASK_ID, taskId)
                                putExtra(EXTRA_NOTIFICATION_TYPE, type)
                                putExtra(TaskAlarmActivity.EXTRA_TASK_LABEL, task.label)
                                putExtra(TaskAlarmActivity.EXTRA_TASK_TIME, dueText)
                                flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                val fullScreenPendingIntent =
                        PendingIntent.getActivity(
                                context,
                                taskId.toInt() + 300_000_000,
                                fullScreenIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                val notificationView =
                        RemoteViews(context.packageName, R.layout.notification_task).apply {
                                setTextViewText(R.id.notification_task_label, task.label)
                                setTextViewText(R.id.notification_task_time, dueText)
                                setOnClickPendingIntent(R.id.notification_root, silenceIntent)
                                setOnClickPendingIntent(R.id.notification_postpone, postponeIntent)
                                setOnClickPendingIntent(R.id.notification_done, doneIntent)
                                setOnClickPendingIntent(R.id.notification_delete, deleteIntent)
                        }
                val expandedNotificationView =
                        RemoteViews(context.packageName, R.layout.notification_task_big).apply {
                                setTextViewText(R.id.notification_task_label, task.label)
                                setTextViewText(R.id.notification_task_time, dueText)
                                setOnClickPendingIntent(R.id.notification_root, silenceIntent)
                                setOnClickPendingIntent(R.id.notification_postpone, postponeIntent)
                                setOnClickPendingIntent(R.id.notification_done, doneIntent)
                                setOnClickPendingIntent(R.id.notification_delete, deleteIntent)
                        }
                val builder =
                        NotificationCompat.Builder(context, channelId)
                                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                                .setContentTitle(task.label)
                                .setContentText(dueText)
                                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                                .setCustomContentView(notificationView)
                                .setCustomBigContentView(expandedNotificationView)
                                .setCustomHeadsUpContentView(notificationView)
                                .setColor(android.graphics.Color.rgb(64, 245, 155))
                                .setPriority(
                                        if (silent) NotificationCompat.PRIORITY_LOW
                                        else NotificationCompat.PRIORITY_MAX
                                )
                                .setCategory(NotificationCompat.CATEGORY_ALARM)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setContentIntent(silenceIntent)
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setOnlyAlertOnce(silent)

                if (!silent && type == "ALARM") {
                        builder.setFullScreenIntent(fullScreenPendingIntent, true)
                }

                if (silent) {
                        builder.setSilent(true)
                } else {
                        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                                .setVibrate(longArrayOf(0, 1000, 500, 1000))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                }

                val notification = builder.build()
                if (!silent) {
                        notification.flags =
                                notification.flags or android.app.Notification.FLAG_INSISTENT
                }

                val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId(taskId, type), notification)
        }

        private fun postponePendingIntent(
                context: Context,
                taskId: Long,
                type: String,
                taskLabel: String
        ): PendingIntent {
                val intent =
                        Intent(context, PostponeActivity::class.java).apply {
                                putExtra(EXTRA_TASK_ID, taskId)
                                putExtra(EXTRA_NOTIFICATION_TYPE, type)
                                putExtra(PostponeActivity.EXTRA_TASK_LABEL, taskLabel)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                val typeOffset = if (type == "WARNING") 1_000_000 else 0
                return PendingIntent.getActivity(
                        context,
                        taskId.toInt() + typeOffset + 200_000_000,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        }

        fun cancelDisplayedNotifications(context: Context, taskId: Long) {
                val manager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(notificationId(taskId, "ALARM"))
                manager.cancel(notificationId(taskId, "WARNING"))
        }

        private fun actionPendingIntent(
                context: Context,
                action: String,
                taskId: Long,
                type: String,
                offset: Int,
                mutable: Boolean = false
        ): PendingIntent {
                val intent =
                        Intent(context, TaskReminderReceiver::class.java).apply {
                                this.action = action
                                putExtra(EXTRA_TASK_ID, taskId)
                                putExtra(EXTRA_NOTIFICATION_TYPE, type)
                        }
                val mutabilityFlag =
                        if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                PendingIntent.FLAG_MUTABLE
                        else PendingIntent.FLAG_IMMUTABLE
                val typeOffset = if (type == "WARNING") 1_000_000 else 0
                return PendingIntent.getBroadcast(
                        context,
                        taskId.toInt() + typeOffset + offset * 10_000_000,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
                )
        }

        private fun notificationId(taskId: Long, type: String): Int =
                if (type == "WARNING") taskId.toInt() + 1_000_000 else taskId.toInt()

        private fun isNotificationDisplayed(context: Context, taskId: Long): Boolean {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
                val manager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                return manager.activeNotifications.any {
                        it.packageName == context.packageName &&
                                (it.id == notificationId(taskId, "ALARM") ||
                                        it.id == notificationId(taskId, "WARNING"))
                }
        }

}
