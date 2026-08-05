package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame

data class PauseSegment(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/** Contiguous stretches of near-zero hip velocity lasting at least [MetricsConfiguration.minPauseDurationMs]. */
fun detectPauses(velocities: List<TimedVelocity>, config: MetricsConfiguration): List<PauseSegment> {
    val pauses = mutableListOf<PauseSegment>()
    var segmentStart: Long? = null
    var lastTimestamp: Long? = null

    fun flush() {
        val start = segmentStart
        val end = lastTimestamp
        if (start != null && end != null && end - start >= config.minPauseDurationMs) {
            pauses += PauseSegment(start, end)
        }
        segmentStart = null
        lastTimestamp = null
    }

    for (v in velocities) {
        if (v.normalizedVelocity < config.stillVelocityThreshold) {
            if (segmentStart == null) segmentStart = v.timestampMs
            lastTimestamp = v.timestampMs
        } else {
            flush()
        }
    }
    flush()
    return pauses
}

/**
 * Start: first moment of sustained movement (rough proxy for "after setup, once the climb
 * actually begins"). End: last moment of movement before the climb settles. This is a simple
 * heuristic, not frame-perfect — a manual start/end correction UI is a reasonable follow-up,
 * not built here.
 */
fun estimateClimbBounds(frames: List<PoseFrame>, velocities: List<TimedVelocity>, config: MetricsConfiguration): Pair<Long, Long> {
    if (frames.isEmpty()) return 0L to 0L
    val defaultStart = frames.first().timestampMs
    val defaultEnd = frames.last().timestampMs
    val start = velocities.firstOrNull { it.normalizedVelocity >= config.stillVelocityThreshold }?.timestampMs ?: defaultStart
    val end = (velocities.lastOrNull { it.normalizedVelocity >= config.stillVelocityThreshold }?.timestampMs ?: defaultEnd)
        .coerceAtLeast(start)
    return start to end
}
