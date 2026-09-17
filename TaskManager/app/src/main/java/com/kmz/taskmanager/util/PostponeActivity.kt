package com.kmz.taskmanager.util

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.kmz.taskmanager.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PostponeActivity : ComponentActivity() {
    private var taskId = 0L
    private var type = "ALARM"
    private var hours = 0L
    private var days = 0L
    private var weeks = 0L
    private var months = 0L
    private var completed = false

    private lateinit var hourButton: TextView
    private lateinit var dayButton: TextView
    private lateinit var weekButton: TextView
    private lateinit var monthButton: TextView
    private lateinit var summary: TextView
    private lateinit var newDate: TextView
    private lateinit var validateButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setFinishOnTouchOutside(false)
        setContentView(R.layout.activity_postpone)

        taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, 0L)
        type = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE) ?: "ALARM"
        if (taskId == 0L) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        handleSystemBack()
                    }
                }
        )

        // Cancelling the insistent notification is the fastest and most reliable sound stop.
        NotificationHelper.cancelDisplayedNotifications(this, taskId)

        hours = savedInstanceState?.getLong(STATE_HOURS) ?: 0L
        days = savedInstanceState?.getLong(STATE_DAYS) ?: 0L
        weeks = savedInstanceState?.getLong(STATE_WEEKS) ?: 0L
        months = savedInstanceState?.getLong(STATE_MONTHS) ?: 0L

        hourButton = findViewById(R.id.postpone_hour)
        dayButton = findViewById(R.id.postpone_day)
        weekButton = findViewById(R.id.postpone_week)
        monthButton = findViewById(R.id.postpone_month)
        summary = findViewById(R.id.postpone_summary)
        newDate = findViewById(R.id.postpone_new_date)
        validateButton = findViewById(R.id.postpone_validate)
        findViewById<TextView>(R.id.postpone_task_label).text =
                intent.getStringExtra(EXTRA_TASK_LABEL).orEmpty().ifBlank { "Tâche" }

        hourButton.setOnClickListener {
            hours++
            updateUi()
        }
        dayButton.setOnClickListener {
            days++
            updateUi()
        }
        weekButton.setOnClickListener {
            weeks++
            updateUi()
        }
        monthButton.setOnClickListener {
            months++
            updateUi()
        }
        validateButton.setOnClickListener { validateSelection() }
        updateUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_HOURS, hours)
        outState.putLong(STATE_DAYS, days)
        outState.putLong(STATE_WEEKS, weeks)
        outState.putLong(STATE_MONTHS, months)
        super.onSaveInstanceState(outState)
    }

    private fun handleSystemBack() {
        if (hasSelection()) {
            validateSelection()
        } else {
            restoreSilentNotificationAndFinish()
        }
    }

    private fun updateUi() {
        hourButton.text = if (hours == 0L) "H+" else "H+\n${hours} h"
        dayButton.text = if (days == 0L) "J+" else "J+\n${days} j"
        weekButton.text = if (weeks == 0L) "S+" else "S+\n${weeks} sem"
        monthButton.text = if (months == 0L) "M+" else "M+\n${months} mois"

        val selected = hasSelection()
        validateButton.isEnabled = selected
        validateButton.alpha = if (selected) 1f else 0.4f
        if (!selected) {
            summary.text = "Appuyez plusieurs fois pour cumuler"
            newDate.text = "H = heure  •  J = jour  •  S = semaine  •  M = mois"
            return
        }

        val parts = mutableListOf<String>()
        if (months > 0L) parts += "$months mois"
        if (weeks > 0L) parts += "$weeks sem"
        if (days > 0L) parts += "$days j"
        if (hours > 0L) parts += "$hours h"
        summary.text = "Report : +${parts.joinToString("  •  +")}"
        val postponedDate =
                LocalDateTime.now().plusMonths(months).plusWeeks(weeks).plusDays(days).plusHours(hours)
        newDate.text = "Nouveau rappel : ${postponedDate.format(DATE_FORMATTER)}"
    }

    private fun hasSelection() = hours > 0L || days > 0L || weeks > 0L || months > 0L

    private fun validateSelection() {
        if (!hasSelection() || completed) return
        completed = true
        sendBroadcast(
                Intent(this, TaskReminderReceiver::class.java).apply {
                    action = NotificationHelper.ACTION_SNOOZE
                    putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE, type)
                    putExtra(NotificationHelper.EXTRA_POSTPONE_HOURS, hours)
                    putExtra(NotificationHelper.EXTRA_POSTPONE_DAYS, days + weeks * 7L)
                    putExtra(NotificationHelper.EXTRA_POSTPONE_MONTHS, months)
                }
        )
        finish()
    }

    private fun restoreSilentNotificationAndFinish() {
        if (completed) return
        completed = true
        sendBroadcast(
                Intent(this, TaskReminderReceiver::class.java).apply {
                    action = NotificationHelper.ACTION_SILENCE
                    putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE, type)
                }
        )
        finish()
    }

    companion object {
        const val EXTRA_TASK_LABEL = "POSTPONE_TASK_LABEL"
        private const val STATE_HOURS = "state_hours"
        private const val STATE_DAYS = "state_days"
        private const val STATE_WEEKS = "state_weeks"
        private const val STATE_MONTHS = "state_months"
        private val DATE_FORMATTER =
                DateTimeFormatter.ofPattern("EEE d MMM • HH:mm", Locale.FRENCH)
    }
}
