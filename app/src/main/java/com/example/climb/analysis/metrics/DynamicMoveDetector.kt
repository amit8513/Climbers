package com.example.climb.analysis.metrics

/** Local peaks in hip velocity at or above [MetricsConfiguration.dynamicMoveVelocityThreshold] —
 * shared by event-timeline building ([com.example.climb.analysis.buildEvents]) and power scoring
 * so both agree on what counts as a "large dynamic move." */
fun detectLargeDynamicMoves(velocities: List<TimedVelocity>, config: MetricsConfiguration): List<Long> {
    val peaks = mutableListOf<Long>()
    var lastPeakMs = Long.MIN_VALUE
    for (i in velocities.indices) {
        val v = velocities[i]
        if (v.normalizedVelocity < config.dynamicMoveVelocityThreshold) continue
        val isLocalMax = (i == 0 || velocities[i - 1].normalizedVelocity <= v.normalizedVelocity) &&
            (i == velocities.lastIndex || velocities[i + 1].normalizedVelocity <= v.normalizedVelocity)
        if (isLocalMax && v.timestampMs - lastPeakMs > 500L) {
            peaks += v.timestampMs
            lastPeakMs = v.timestampMs
        }
    }
    return peaks
}
