package com.example.climb.analysis.metrics

/** Local peaks in hip velocity at or above [MetricsConfiguration.dynamicMoveVelocityThreshold] —
 * shared by event-timeline building ([com.example.climb.analysis.buildEvents]) and power scoring
 * so both agree on what counts as a "large dynamic move." */
fun detectLargeDynamicMoves(velocities: List<TimedVelocity>, config: MetricsConfiguration): List<Long> {
    val peaks = mutableListOf<Long>()
    // Nullable rather than a Long.MIN_VALUE sentinel: real video timestamps start near zero, so
    // `v.timestampMs - Long.MIN_VALUE` overflows and wraps negative, silently failing the ">
    // 500L" check below for the very first candidate peak in every sequence.
    var lastPeakMs: Long? = null
    for (i in velocities.indices) {
        val v = velocities[i]
        if (v.normalizedVelocity < config.dynamicMoveVelocityThreshold) continue
        val isLocalMax = (i == 0 || velocities[i - 1].normalizedVelocity <= v.normalizedVelocity) &&
            (i == velocities.lastIndex || velocities[i + 1].normalizedVelocity <= v.normalizedVelocity)
        if (isLocalMax && (lastPeakMs == null || v.timestampMs - lastPeakMs > 500L)) {
            peaks += v.timestampMs
            lastPeakMs = v.timestampMs
        }
    }
    return peaks
}
