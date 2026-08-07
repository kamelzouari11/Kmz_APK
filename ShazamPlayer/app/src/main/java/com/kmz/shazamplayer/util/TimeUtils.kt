package com.kmz.shazamplayer.util

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%02d:%02d".format(min, sec)
}
