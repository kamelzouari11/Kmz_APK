package com.kmz.myvolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class VolumeOverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var preferences: OverlayPreferences
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var overlayView: FrameLayout? = null
    private var shadowView: View? = null
    private var buttonSurface: FrameLayout? = null
    private var iconView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayTemporarilyHidden = false

    override fun onCreate() {
        super.onCreate()
        preferences = OverlayPreferences(this)
        windowManager = getSystemService(WindowManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        preferences.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        createNotificationChannel()
        startAsForeground()
        showOverlayIfAllowed()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                overlayTemporarilyHidden = true
                removeOverlay()
                updateNotification()
                return START_STICKY
            }
            ACTION_SHOW -> {
                overlayTemporarilyHidden = false
                showOverlayIfAllowed()
                updateNotification()
                return START_STICKY
            }
            ACTION_STOP -> {
                preferences.enabled = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!preferences.enabled || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlayIfAllowed()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.post { clampAndUpdatePosition() }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            OverlayPreferences.KEY_ENABLED -> {
                if (!preferences.enabled) stopSelf() else showOverlayIfAllowed()
            }
            OverlayPreferences.KEY_COLOR,
            OverlayPreferences.KEY_SIZE,
            OverlayPreferences.KEY_OPACITY,
            OverlayPreferences.KEY_SNAP_TO_EDGE -> updateOverlayAppearance()
        }
    }

    override fun onDestroy() {
        preferences.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        removeOverlay()
        super.onDestroy()
    }

    private fun removeOverlay() {
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
        shadowView = null
        buttonSurface = null
        iconView = null
        layoutParams = null
    }

    private fun showOverlayIfAllowed() {
        if (
            overlayView != null ||
            overlayTemporarilyHidden ||
            !preferences.enabled ||
            !Settings.canDrawOverlays(this)
        ) return

        val sizePx = dpToPx(preferences.sizeDp)
        val shadowPaddingPx = dpToPx(SHADOW_PADDING_DP)
        val windowSizePx = sizePx + shadowPaddingPx * 2
        val bounds = currentBounds()
        val initialX = preferences.positionX.takeUnless {
            it == OverlayPreferences.POSITION_UNSET
        } ?: (bounds.width() - windowSizePx - dpToPx(6f))
        val initialY = preferences.positionY.takeUnless {
            it == OverlayPreferences.POSITION_UNSET
        } ?: ((bounds.height() - windowSizePx) / 2)

        val params = WindowManager.LayoutParams(
            windowSizePx,
            windowSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        layoutParams = params

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_volume)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Ouvrir le volume multimédia"
        }
        iconView = icon

        val button = FrameLayout(this).apply {
            elevation = dpToPx(12f).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = Color.argb(190, 0, 0, 0)
                outlineSpotShadowColor = Color.argb(220, 0, 0, 0)
            }
            addView(
                icon,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        buttonSurface = button

        val shadow = View(this).apply {
            translationX = dpToPx(SHADOW_OFFSET_X_DP).toFloat()
            translationY = dpToPx(SHADOW_OFFSET_Y_DP).toFloat()
        }
        shadowView = shadow

        val container = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                shadow,
                FrameLayout.LayoutParams(windowSizePx, windowSizePx, Gravity.CENTER),
            )
            addView(
                button,
                FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER),
            )
            setOnTouchListener(OverlayTouchListener())
        }
        overlayView = container
        updateOverlayAppearance()
        clampPosition(params, bounds.width(), bounds.height(), windowSizePx)
        runCatching { windowManager.addView(container, params) }
            .onFailure { stopSelf() }
    }

    private fun updateOverlayAppearance() {
        val view = overlayView ?: return
        val shadow = shadowView ?: return
        val button = buttonSurface ?: return
        val params = layoutParams ?: return
        val sizePx = dpToPx(preferences.sizeDp)
        val shadowPaddingPx = dpToPx(SHADOW_PADDING_DP)
        val windowSizePx = sizePx + shadowPaddingPx * 2
        val background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(preferences.color or (0xFF shl 24))
            setStroke(dpToPx(1.5f), Color.argb(205, 255, 255, 255))
        }
        val red = Color.red(preferences.color)
        val green = Color.green(preferences.color)
        val blue = Color.blue(preferences.color)
        shadow.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = windowSizePx / 2f
            colors = intArrayOf(
                Color.argb(180, 0, 0, 0),
                Color.argb(115, red, green, blue),
                Color.TRANSPARENT,
            )
        }
        shadow.layoutParams = FrameLayout.LayoutParams(windowSizePx, windowSizePx, Gravity.CENTER)
        button.background = background
        button.elevation = dpToPx(12f).toFloat()
        button.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
        button.alpha = preferences.opacity
        shadow.alpha = 0.9f
        iconView?.setPadding(sizePx / 4, sizePx / 4, sizePx / 4, sizePx / 4)
        params.width = windowSizePx
        params.height = windowSizePx
        clampPosition(params, currentBounds().width(), currentBounds().height(), windowSizePx)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun openNativeVolumePanel(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun clampAndUpdatePosition() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val bounds = currentBounds()
        clampPosition(params, bounds.width(), bounds.height(), params.width)
        windowManager.updateViewLayout(view, params)
        savePosition(params)
    }

    private fun clampPosition(
        params: WindowManager.LayoutParams,
        screenWidth: Int,
        screenHeight: Int,
        sizePx: Int,
    ) {
        val margin = dpToPx(4f)
        params.x = params.x.coerceIn(margin, (screenWidth - sizePx - margin).coerceAtLeast(margin))
        params.y = params.y.coerceIn(margin, (screenHeight - sizePx - margin).coerceAtLeast(margin))
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams) {
        if (!preferences.snapToEdge) return
        val bounds = currentBounds()
        val margin = dpToPx(6f)
        val right = (bounds.width() - params.width - margin).coerceAtLeast(margin)
        params.x = if (params.x + params.width / 2 < bounds.width() / 2) margin else right
    }

    private fun savePosition(params: WindowManager.LayoutParams) {
        preferences.positionX = params.x
        preferences.positionY = params.y
    }

    @Suppress("DEPRECATION")
    private fun currentBounds(): Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds
    } else {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).roundToInt()

    private inner class OverlayTouchListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(this@VolumeOverlayService).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    buttonSurface?.alpha = (preferences.opacity + 0.12f).coerceAtMost(1f)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = startX + deltaX.roundToInt()
                        params.y = startY + deltaY.roundToInt()
                        val bounds = currentBounds()
                        clampPosition(params, bounds.width(), bounds.height(), params.width)
                        windowManager.updateViewLayout(view, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    buttonSurface?.alpha = preferences.opacity
                    if (dragging) {
                        snapToNearestEdge(params)
                        clampAndUpdatePosition()
                    } else {
                        openNativeVolumePanel(view)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    buttonSurface?.alpha = preferences.opacity
                    return true
                }
            }
            return false
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Maintient le bouton de volume flottant actif"
                setShowBadge(false)
            },
        )
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(),
        )
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VolumeOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val visibilityIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VolumeOverlayService::class.java).setAction(
                if (overlayTemporarilyHidden) ACTION_SHOW else ACTION_HIDE,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(
                getString(
                    if (overlayTemporarilyHidden) {
                        R.string.overlay_notification_text_hidden
                    } else {
                        R.string.overlay_notification_text
                    },
                ),
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                getString(
                    if (overlayTemporarilyHidden) R.string.show else R.string.hide,
                ),
                visibilityIntent,
            )
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_HIDE = "com.kmz.myvolume.action.HIDE"
        const val ACTION_SHOW = "com.kmz.myvolume.action.SHOW"
        const val ACTION_STOP = "com.kmz.myvolume.action.STOP"
        private const val CHANNEL_ID = "myvolume_overlay"
        private const val NOTIFICATION_ID = 7101
        private const val SHADOW_PADDING_DP = 14f
        private const val SHADOW_OFFSET_X_DP = 7f
        private const val SHADOW_OFFSET_Y_DP = 9f
    }
}
