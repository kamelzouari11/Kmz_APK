package com.kmz.miniserver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class ServerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val isRunning = HttpServerService.isRunning
            val views = createRemoteViews(context, isRunning)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "TOGGLE_SERVER") {
            val serviceIntent = Intent(context, HttpServerService::class.java)
            val isRunning = HttpServerService.isRunning

            val appCtx = context.applicationContext
            if (isRunning) {
                val toast =
                        android.widget.Toast.makeText(
                                appCtx,
                                "MiniSERVER is OFF",
                                android.widget.Toast.LENGTH_SHORT
                        )
                toast.setGravity(android.view.Gravity.TOP, 0, 200)
                toast.show()
                context.stopService(serviceIntent)
            } else {
                val toast =
                        android.widget.Toast.makeText(
                                appCtx,
                                "MiniSERVER is ON",
                                android.widget.Toast.LENGTH_SHORT
                        )
                toast.setGravity(android.view.Gravity.TOP, 0, 200)
                toast.show()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    companion object {
        private fun createRemoteViews(context: Context, isRunning: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Fond : Vert si On, Rouge si Off
            val bgRes = if (isRunning) R.drawable.widget_bg_on else R.drawable.widget_bg_off
            views.setImageViewResource(R.id.widget_bg, bgRes)

            val intent =
                    Intent(context, ServerWidgetProvider::class.java).apply {
                        action = "TOGGLE_SERVER"
                    }

            val flags =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            return views
        }

        fun updateAllWidgets(context: Context, isRunning: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, ServerWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            if (allWidgetIds.isNotEmpty()) {
                val views = createRemoteViews(context, isRunning)
                appWidgetManager.updateAppWidget(allWidgetIds, views)
            }
        }
    }
}
