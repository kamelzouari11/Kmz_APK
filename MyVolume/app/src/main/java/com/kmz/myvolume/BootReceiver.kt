package com.kmz.myvolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val preferences = OverlayPreferences(context)
        if (!preferences.enabled || !preferences.startOnBoot || !Settings.canDrawOverlays(context)) {
            return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, VolumeOverlayService::class.java),
        )
    }
}
