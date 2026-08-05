package com.example.climb.pose

/**
 * Every pose-analysis threshold and limit lives here, not scattered as magic numbers through
 * the estimator/worker/metrics code. Tune these in one place.
 */
data class PoseAnalysisConfiguration(
    /** How many frames per second of video to actually run pose detection on. */
    val targetFps: Int = 10,
    /** Below this, a single landmark is not trusted. */
    val minLandmarkConfidence: Float = 0.5f,
    /** Below this average confidence, a whole frame is marked unreliable. */
    val minReliableFrameConfidence: Float = 0.5f,
    val maxDurationMs: Long = MAX_DURATION_MS,
    val maxFileSizeBytes: Long = MAX_FILE_SIZE_BYTES,
    val minResolutionPx: Int = MIN_RESOLUTION_PX,
) {
    init {
        require(targetFps in MIN_TARGET_FPS..MAX_TARGET_FPS) {
            "targetFps must be within the supported $MIN_TARGET_FPS-$MAX_TARGET_FPS range"
        }
    }

    companion object {
        const val MIN_TARGET_FPS = 8
        const val MAX_TARGET_FPS = 15
        const val MAX_DURATION_MS = 5 * 60_000L
        const val MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024
        const val MIN_RESOLUTION_PX = 240
    }
}
