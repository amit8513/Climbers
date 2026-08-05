package com.example.climb.analysis

/** "00:23" style mm:ss, used by both event descriptions and coaching tip text. */
fun formatTimestampMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
