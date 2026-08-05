package com.example.climb.pose

/** Where the video being analyzed comes from. Only local files are analyzable today. */
sealed interface VideoSource {
    data class LocalFile(val path: String) : VideoSource
}
