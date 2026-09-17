package com.football.footballapp.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.football.footballapp.MainActivity
import com.football.footballapp.R
import com.football.footballapp.data.model.Match
import org.json.JSONObject
import java.time.OffsetDateTime

private const val REMINDER_MINUTES = 15L
private const val REMINDER_MILLIS = REMINDER_MINUTES * 60L * 1000L
private const val PREFS_NAME = "match_reminders"
private const val PREF_KEY_PREFIX = "match_"
private const val CHANNEL_ID = "match_reminders_v2"
private const val EXTRA_MATCH_ID = "match_id"
private const val EXTRA_HOME = "home"
private const val EXTRA_AWAY = "away"

class MatchReminderManager(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class ScheduleResult {
        EXACT,
        APPROXIMATE,
        TOO_LATE
    }

    fun isEnabled(match: Match): Boolean {
        val record = readRecord(match.id) ?: return false
        if (record.kickoffMillis - REMINDER_MILLIS <= System.currentTimeMillis()) {
            cancel(match.id)
            return false
        }
        if (record.kickoffMillis != match.kickoffMillisOrNull()) {
            cancel(match.id)
            return false
        }
        return true
    }

    fun schedule(match: Match): ScheduleResult {
        val kickoffMillis = match.kickoffMillisOrNull() ?: return ScheduleResult.TOO_LATE
        val triggerAtMillis = kickoffMillis - REMINDER_MILLIS
        if (triggerAtMillis <= System.currentTimeMillis()) return ScheduleResult.TOO_LATE

        val record = ReminderRecord(
            matchId = match.id,
            kickoffMillis = kickoffMillis,
            home = match.homeTeam.name,
            away = match.awayTeam.name
        )
        saveRecord(record)
        return scheduleRecord(record)
    }

    fun cancel(matchId: Long) {
        alarmManager.cancel(reminderPendingIntent(appContext, matchId))
        preferences.edit().remove(recordKey(matchId)).apply()
    }

    fun rescheduleAll() {
        storedRecords().forEach { record ->
            if (record.kickoffMillis - REMINDER_MILLIS <= System.currentTimeMillis()) {
                cancel(record.matchId)
            } else {
                scheduleRecord(record)
            }
        }
    }

    private fun scheduleRecord(record: ReminderRecord): ScheduleResult {
        val triggerAtMillis = record.kickoffMillis - REMINDER_MILLIS
        val operation = reminderPendingIntent(appContext, record.matchId, record)
        val exactAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            val exactScheduled = runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }.isSuccess
            if (exactScheduled) return ScheduleResult.EXACT
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation
        )
        return ScheduleResult.APPROXIMATE
    }

    private fun saveRecord(record: ReminderRecord) {
        val json = JSONObject()
            .put("matchId", record.matchId)
            .put("kickoffMillis", record.kickoffMillis)
            .put("home", record.home)
            .put("away", record.away)
            .toString()
        preferences.edit().putString(recordKey(record.matchId), json).apply()
    }

    private fun readRecord(matchId: Long): ReminderRecord? =
        preferences.getString(recordKey(matchId), null)?.toReminderRecordOrNull()

    private fun storedRecords(): List<ReminderRecord> = preferences.all
        .filterKeys { it.startsWith(PREF_KEY_PREFIX) }
        .values
        .mapNotNull { (it as? String)?.toReminderRecordOrNull() }

    companion object {
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val notificationSound = RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION
            )
            val soundAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rappels de matchs",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification 15 minutes avant le coup d'envoi"
                setSound(notificationSound, soundAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 180L, 120L, 180L)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        internal fun consume(context: Context, matchId: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(recordKey(matchId))
                .apply()
        }
    }
}

class MatchReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getLongExtra(EXTRA_MATCH_ID, Long.MIN_VALUE)
        if (matchId == Long.MIN_VALUE) return
        val home = intent.getStringExtra(EXTRA_HOME) ?: return
        val away = intent.getStringExtra(EXTRA_AWAY) ?: return

        MatchReminderManager.createNotificationChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            requestCode(matchId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_football)
            .setContentTitle("Match dans 15 minutes")
            .setContentText("$home – $away")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$home – $away"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0L, 180L, 120L, 180L))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (notificationsAllowed) {
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(requestCode(matchId), notification)
            }
        }
        MatchReminderManager.consume(context, matchId)
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            MatchReminderManager(context).rescheduleAll()
        }
    }
}

private data class ReminderRecord(
    val matchId: Long,
    val kickoffMillis: Long,
    val home: String,
    val away: String
)

private fun String.toReminderRecordOrNull(): ReminderRecord? = runCatching {
    val json = JSONObject(this)
    ReminderRecord(
        matchId = json.getLong("matchId"),
        kickoffMillis = json.getLong("kickoffMillis"),
        home = json.getString("home"),
        away = json.getString("away")
    )
}.getOrNull()

private fun Match.kickoffMillisOrNull(): Long? = runCatching {
    OffsetDateTime.parse(utcDate).toInstant().toEpochMilli()
}.getOrNull()

private fun reminderPendingIntent(
    context: Context,
    matchId: Long,
    record: ReminderRecord? = null
): PendingIntent {
    val intent = Intent(context, MatchReminderReceiver::class.java).apply {
        action = "com.football.footballapp.MATCH_REMINDER"
        data = Uri.parse("football://match-reminder/$matchId")
        record?.let {
            putExtra(EXTRA_MATCH_ID, it.matchId)
            putExtra(EXTRA_HOME, it.home)
            putExtra(EXTRA_AWAY, it.away)
        }
    }
    return PendingIntent.getBroadcast(
        context,
        requestCode(matchId),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun recordKey(matchId: Long): String = "$PREF_KEY_PREFIX$matchId"

private fun requestCode(matchId: Long): Int = (matchId xor (matchId ushr 32)).toInt()
