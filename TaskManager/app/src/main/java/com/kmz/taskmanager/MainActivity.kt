package com.kmz.taskmanager

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kmz.taskmanager.ui.MainScreen
import com.kmz.taskmanager.ui.MyTasksTheme
import com.kmz.taskmanager.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var waitingForExactAlarmPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)
        requestPermissions()
        waitingForExactAlarmPermission = requestExactAlarmPermission()

        enableEdgeToEdge()
        setContent { MyTasksTheme { MainScreen() } }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationHelper.reschedulePendingTaskAlarms(applicationContext)
        }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!waitingForExactAlarmPermission || alarmManager.canScheduleExactAlarms()) {
            waitingForExactAlarmPermission = false
            requestFullScreenAlertPermission()
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        101
                )
            }
        }
    }

    private fun requestExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                )
                return true
            }
        }
        return false
    }

    private fun requestFullScreenAlertPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val preferences = getSharedPreferences("notification_permissions", MODE_PRIVATE)
        if (!notificationManager.canUseFullScreenIntent() &&
                        !preferences.getBoolean("full_screen_request_shown", false)
        ) {
            preferences.edit().putBoolean("full_screen_request_shown", true).apply()
            startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                    }
            )
        }
    }
}
