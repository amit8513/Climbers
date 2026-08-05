package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame

private const val LARGE_GAP_MS = 500L

data class TimedVelocity(val timestampMs: Long, val normalizedVelocity: Float)

/**
 * Hip-center velocity between consecutive frames, normalized by body height so it's comparable
 * across different camera distances. Only computed between two *reliable* frames — an
 * unreliable frame's noisy landmark position would otherwise show up as a fake spike of motion.
 */
fun computeHipVelocities(frames: List<PoseFrame>): List<TimedVelocity> {
    val result = mutableListOf<TimedVelocity>()
    for (i in 1 until frames.size) {
        val prev = frames[i - 1]
        val curr = frames[i]
        if (!prev.isReliable || !curr.isReliable) continue
        val prevHip = prev.hipCenter() ?: continue
        val currHip = curr.hipCenter() ?: continue
        val bodyHeight = curr.bodyHeightEstimate() ?: prev.bodyHeightEstimate() ?: continue
        val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000f
        if (dtSeconds <= 0f) continue
        val velocity = (distance(prevHip, currHip) / bodyHeight) / dtSeconds
        result += TimedVelocity(curr.timestampMs, velocity)
    }
    return result
}

/** Simple exponential moving average — the "simplest reliable option that fits the project"
 * per the smoothing requirement, reset (not blended) across gaps bigger than [LARGE_GAP_MS]. */
fun smoothVelocities(velocities: List<TimedVelocity>, alpha: Float = 0.3f): List<TimedVelocity> {
    if (velocities.isEmpty()) return velocities
    val smoothed = ArrayList<TimedVelocity>(velocities.size)
    var previousSmoothed = velocities.first().normalizedVelocity
    smoothed += velocities.first()
    for (i in 1 until velocities.size) {
        val curr = velocities[i]
        val prev = velocities[i - 1]
        val gapMs = curr.timestampMs - prev.timestampMs
        val value = if (gapMs > LARGE_GAP_MS) {
            curr.normalizedVelocity
        } else {
            alpha * curr.normalizedVelocity + (1 - alpha) * previousSmoothed
        }
        previousSmoothed = value
        smoothed += TimedVelocity(curr.timestampMs, value)
    }
    return smoothed
}
