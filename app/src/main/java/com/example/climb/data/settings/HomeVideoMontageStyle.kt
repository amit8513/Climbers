package com.example.climb.data.settings

/** How [com.example.climb.ui.home.HomeVideoBackground] cycles through the user's climb videos. */
enum class HomeVideoMontageStyle(val storageKey: String, val label: String, val description: String) {
    FULL_CLIPS(
        storageKey = "full_clips",
        label = "Full clips",
        description = "Plays each video through, cutting to the next when it ends.",
    ),
    SHORT_MONTAGE(
        storageKey = "short_montage",
        label = "Short montage",
        description = "A few seconds of each video, dissolving slowly into the next.",
    ),
    ;

    companion object {
        val DEFAULT = FULL_CLIPS
        fun fromStorageKey(key: String?): HomeVideoMontageStyle = entries.find { it.storageKey == key } ?: DEFAULT
    }
}
