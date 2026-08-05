package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType

enum class Side { LEFT, RIGHT }

data class LockoffSegment(val side: Side, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

private fun armAngleDegrees(frame: PoseFrame, side: Side): Float? {
    val (shoulderType, elbowType, wristType) = when (side) {
        Side.LEFT -> Triple(PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.LEFT_ELBOW, PoseLandmarkType.LEFT_WRIST)
        Side.RIGHT -> Triple(PoseLandmarkType.RIGHT_SHOULDER, PoseLandmarkType.RIGHT_ELBOW, PoseLandmarkType.RIGHT_WRIST)
    }
    val shoulder: PoseLandmark = frame.landmark(shoulderType) ?: return null
    val elbow = frame.landmark(elbowType) ?: return null
    val wrist = frame.landmark(wristType) ?: return null
    return elbowAngleDegrees(shoulder, elbow, wrist)
}

/**
 * A "deep" lock-off is a bent-elbow angle (<= [MetricsConfiguration.deepLockoffAngleDegrees])
 * sustained for at least [MetricsConfiguration.minSustainedLockoffMs] — long enough to be a
 * held position, not just a brief pass-through while reaching.
 */
fun detectLockoffs(frames: List<PoseFrame>, config: MetricsConfiguration): List<LockoffSegment> {
    val segments = mutableListOf<LockoffSegment>()
    for (side in Side.entries) {
        var segmentStart: Long? = null
        var lastTimestamp: Long? = null

        fun flush() {
            val start = segmentStart
            val end = lastTimestamp
            if (start != null && end != null && end - start >= config.minSustainedLockoffMs) {
                segments += LockoffSegment(side, start, end)
            }
            segmentStart = null
            lastTimestamp = null
        }

        for (frame in frames) {
            val angle = if (frame.isReliable) armAngleDegrees(frame, side) else null
            if (angle != null && angle <= config.deepLockoffAngleDegrees) {
                if (segmentStart == null) segmentStart = frame.timestampMs
                lastTimestamp = frame.timestampMs
            } else {
                flush()
            }
        }
        flush()
    }
    return segments.sortedBy { it.startMs }
}

/** Share of reliable frames where the arms (averaged when both are measurable) are relatively
 * straight — the inverse signal to time spent locked off. */
fun straightArmPercentage(frames: List<PoseFrame>, config: MetricsConfiguration): Float {
    var reliableCount = 0
    var straightCount = 0
    for (frame in frames) {
        if (!frame.isReliable) continue
        val angles = listOfNotNull(armAngleDegrees(frame, Side.LEFT), armAngleDegrees(frame, Side.RIGHT))
        if (angles.isEmpty()) continue
        reliableCount++
        if (angles.average() >= config.straightArmAngleDegrees) straightCount++
    }
    return if (reliableCount == 0) 0f else (straightCount.toFloat() / reliableCount) * 100f
}
