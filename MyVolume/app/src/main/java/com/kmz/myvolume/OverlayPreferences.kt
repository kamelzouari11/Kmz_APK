package com.kmz.myvolume

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class OverlayPreferences(context: Context) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_ENABLED, false)
        set(value) = put(KEY_ENABLED, value)

    var color: Int
        get() = sharedPreferences.getInt(KEY_COLOR, DEFAULT_COLOR)
        set(value) = put(KEY_COLOR, value)

    var sizeDp: Float
        get() = sharedPreferences.getFloat(KEY_SIZE, DEFAULT_SIZE_DP)
        set(value) = put(KEY_SIZE, value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP))

    var opacity: Float
        get() = sharedPreferences.getFloat(KEY_OPACITY, DEFAULT_OPACITY)
        set(value) = put(KEY_OPACITY, value.coerceIn(MIN_OPACITY, 1f))

    var snapToEdge: Boolean
        get() = sharedPreferences.getBoolean(KEY_SNAP_TO_EDGE, true)
        set(value) = put(KEY_SNAP_TO_EDGE, value)

    var startOnBoot: Boolean
        get() = sharedPreferences.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = put(KEY_START_ON_BOOT, value)

    var positionX: Int
        get() = sharedPreferences.getInt(KEY_POSITION_X, POSITION_UNSET)
        set(value) = put(KEY_POSITION_X, value)

    var positionY: Int
        get() = sharedPreferences.getInt(KEY_POSITION_Y, POSITION_UNSET)
        set(value) = put(KEY_POSITION_Y, value)

    private fun put(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private fun put(key: String, value: Float) {
        sharedPreferences.edit().putFloat(key, value).apply()
    }

    private fun put(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    companion object {
        private const val FILE_NAME = "myvolume_preferences"

        const val KEY_ENABLED = "overlay_enabled"
        const val KEY_COLOR = "overlay_color"
        const val KEY_SIZE = "overlay_size_dp"
        const val KEY_OPACITY = "overlay_opacity"
        const val KEY_SNAP_TO_EDGE = "snap_to_edge"
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_POSITION_X = "position_x"
        const val KEY_POSITION_Y = "position_y"

        const val MIN_SIZE_DP = 42f
        const val MAX_SIZE_DP = 96f
        const val DEFAULT_SIZE_DP = 58f
        const val MIN_OPACITY = 0.25f
        const val DEFAULT_OPACITY = 0.82f
        const val POSITION_UNSET = Int.MIN_VALUE
        val DEFAULT_COLOR: Int = Color.rgb(0, 209, 178)
    }
}
