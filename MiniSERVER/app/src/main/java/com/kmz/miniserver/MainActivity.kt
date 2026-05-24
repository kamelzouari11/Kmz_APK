package com.kmz.miniserver

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

        private var isServiceRunning by mutableStateOf(false)
        private var ipAddress by mutableStateOf("Recherche...")

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)

                checkStoragePermission()
                updateIpAddress()

                setContent {
                        MiniServerTheme {
                                MainScreen(
                                        isRunning = isServiceRunning,
                                        ip = ipAddress,
                                        onToggle = { toggleServer() },
                                        onRefreshIp = { updateIpAddress() }
                                )
                        }
                }
        }

        override fun onResume() {
                super.onResume()
                isServiceRunning = HttpServerService.isRunning
                updateIpAddress()
        }

        private fun checkStoragePermission() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!Environment.isExternalStorageManager()) {
                                val intent =
                                        Intent(
                                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                        )
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivity(intent)
                        }
                }
        }

        private fun updateIpAddress() {
                ipAddress = NetworkUtils.getLocalIpAddress() ?: "Non connecté"
        }

        private fun toggleServer() {
                val intent = Intent(this, HttpServerService::class.java)
                if (isServiceRunning) {
                        val toast =
                                android.widget.Toast.makeText(
                                        this,
                                        "MiniSERVER is OFF",
                                        android.widget.Toast.LENGTH_SHORT
                                )
                        toast.setGravity(android.view.Gravity.TOP, 0, 200)
                        toast.show()
                        stopService(intent)
                        isServiceRunning = false
                } else {
                        val toast =
                                android.widget.Toast.makeText(
                                        this,
                                        "MiniSERVER is ON",
                                        android.widget.Toast.LENGTH_SHORT
                                )
                        toast.setGravity(android.view.Gravity.TOP, 0, 200)
                        toast.show()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                        } else {
                                startService(intent)
                        }
                        isServiceRunning = true
                }
        }
}
