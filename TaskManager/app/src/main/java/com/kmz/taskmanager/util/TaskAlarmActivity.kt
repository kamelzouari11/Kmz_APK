package com.kmz.taskmanager.util

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.kmz.taskmanager.R

class TaskAlarmActivity : ComponentActivity() {
    private var taskId: Long = 0L
    private var type: String = "ALARM"
    private var notificationSilenced = false

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
        setContentView(R.layout.activity_task_alarm)

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
                        sendAction(NotificationHelper.ACTION_SILENCE)
                    }
                }
        )

        findViewById<TextView>(R.id.alarm_task_label).text =
                intent.getStringExtra(EXTRA_TASK_LABEL).orEmpty()
        findViewById<TextView>(R.id.alarm_task_time).text =
                intent.getStringExtra(EXTRA_TASK_TIME).orEmpty()
        findViewById<TextView>(R.id.alarm_postpone).setOnClickListener { openPostponePicker() }
        findViewById<TextView>(R.id.alarm_done).setOnClickListener {
            sendAction(NotificationHelper.ACTION_DONE)
        }
        findViewById<TextView>(R.id.alarm_delete).setOnClickListener {
            sendAction(NotificationHelper.ACTION_DELETE)
        }
        findViewById<android.view.View>(R.id.alarm_root).setOnClickListener {
            sendAction(NotificationHelper.ACTION_SILENCE)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !notificationSilenced) {
            notificationSilenced = true
            NotificationHelper.cancelDisplayedNotifications(this, taskId)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun openPostponePicker() {
        startActivity(
                Intent(this, PostponeActivity::class.java).apply {
                    putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE, type)
                    putExtra(
                            PostponeActivity.EXTRA_TASK_LABEL,
                            intent.getStringExtra(EXTRA_TASK_LABEL).orEmpty()
                    )
                }
        )
        // Keep the newly opened picker in the task; only close this alarm screen.
        finish()
    }

    private fun sendAction(action: String) {
        NotificationHelper.cancelDisplayedNotifications(this, taskId)
        sendBroadcast(
                Intent(this, TaskReminderReceiver::class.java).apply {
                    this.action = action
                    putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationHelper.EXTRA_NOTIFICATION_TYPE, type)
                }
        )
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_TASK_LABEL = "FULL_SCREEN_TASK_LABEL"
        const val EXTRA_TASK_TIME = "FULL_SCREEN_TASK_TIME"
    }
}
