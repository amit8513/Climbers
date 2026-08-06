package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame

/** Pose-tracking quality for one contiguous time range of the analyzed video. */
data class PoseQualityRange(
    val startMs: Long,
    val endMs: Long,
    val averageConfidence: Float,
    val reliableFramePercentage: Float,
)

data class PoseQualityReport(
    val overallReliableFramePercentage: Float,
    val ranges: List<PoseQualityRange>,
)

/**
 * Buckets the whole analyzed video into fixed-size windows and reports tracking quality for each,
 * rather than a single number for the whole video — a climb tracked well at the start but poorly
 * once the climber's back turns to the camera near the top should say exactly that, not average
 * the two into a misleadingly middling overall figure.
 */
fun evaluatePoseQuality(frames: List<PoseFrame>, windowMs: Long = 2_000L): PoseQualityReport {
    if (frames.isEmpty()) return PoseQualityReport(0f, emptyList())

    val overallReliablePercentage = frames.count { it.isReliable }.toFloat() / frames.size * 100f

    val startMs = frames.first().timestampMs
    val endMs = frames.last().timestampMs
    val ranges = mutableListOf<PoseQualityRange>()
    var windowStart = startMs
    while (windowStart < endMs) {
        val windowEnd = (windowStart + windowMs).coerceAtMost(endMs + 1)
        val windowFrames = frames.filter { it.timestampMs >= windowStart && it.timestampMs < windowEnd }
        if (windowFrames.isNotEmpty()) {
            ranges += PoseQualityRange(
                startMs = windowStart,
                endMs = windowEnd,
                averageConfidence = windowFrames.map { it.averageConfidence }.average().toFloat(),
                reliableFramePercentage = windowFrames.count { it.isReliable }.toFloat() / windowFrames.size * 100f,
            )
        }
        windowStart = windowEnd
    }

    return PoseQualityReport(overallReliablePercentage, ranges)
}

/** The single worst-tracked range (if any range is long enough to be meaningful) — for surfacing
 * "tracking was unreliable between X and Y" rather than only a vague overall percentage. */
fun PoseQualityReport.worstRange(minRangeMs: Long = 1_000L): PoseQualityRange? =
    ranges.filter { it.endMs - it.startMs >= minRangeMs }.minByOrNull { it.reliableFramePercentage }
