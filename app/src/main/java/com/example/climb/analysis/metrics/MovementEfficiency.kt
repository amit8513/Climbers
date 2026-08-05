package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import kotlin.math.roundToInt

private fun totalLandmarkTravel(frames: List<PoseFrame>, type: PoseLandmarkType, bodyHeight: Float): Float {
    var total = 0f
    var previous: PoseLandmark? = null
    for (frame in frames) {
        val landmark = frame.landmark(type) ?: continue
        if (previous != null) {
            total += distance(
                Point2D(previous.normalizedX, previous.normalizedY),
                Point2D(landmark.normalizedX, landmark.normalizedY),
            ) / bodyHeight
        }
        previous = landmark
    }
    return total
}

/**
 * A relative 0-100 score: net upward hip progress against total limb travel, penalized for
 * pause time and foot adjustments. Explicitly a *relative* number for comparing similar climbs
 * by the same climber — not an objective technique rating. The "140" scale factor below is an
 * empirical fit to land typical climbs in a readable range, not a physical constant.
 */
fun estimateMovementEfficiency(
    frames: List<PoseFrame>,
    climbStartMs: Long,
    climbEndMs: Long,
    footAdjustmentCount: Int,
    pauseTimeMs: Long,
    config: MetricsConfiguration,
): Int {
    val climbFrames = frames.filter { it.timestampMs in climbStartMs..climbEndMs && it.isReliable }
    if (climbFrames.size < 2) return 0

    val hipPositions = climbFrames.mapNotNull { frame -> frame.hipCenter()?.let { frame.timestampMs to it } }
    if (hipPositions.size < 2) return 0

    val bodyHeight = climbFrames.mapNotNull { it.bodyHeightEstimate() }
        .average().takeIf { !it.isNaN() }?.toFloat()?.takeIf { it > 0.01f } ?: 1f

    // Net upward displacement — normalized Y decreases upward in image coordinates.
    val verticalProgress = ((hipPositions.first().second.y - hipPositions.last().second.y) / bodyHeight).coerceAtLeast(0f)

    var totalHipTravel = 0f
    for (i in 1 until hipPositions.size) {
        totalHipTravel += distance(hipPositions[i - 1].second, hipPositions[i].second) / bodyHeight
    }
    val totalWristTravel = totalLandmarkTravel(climbFrames, PoseLandmarkType.LEFT_WRIST, bodyHeight) +
        totalLandmarkTravel(climbFrames, PoseLandmarkType.RIGHT_WRIST, bodyHeight)
    val totalAnkleTravel = totalLandmarkTravel(climbFrames, PoseLandmarkType.LEFT_ANKLE, bodyHeight) +
        totalLandmarkTravel(climbFrames, PoseLandmarkType.RIGHT_ANKLE, bodyHeight)

    val totalMovement = totalHipTravel + totalWristTravel * 0.25f + totalAnkleTravel * 0.25f
    val epsilon = 0.01f
    val rawEfficiency = verticalProgress / (totalMovement + epsilon)

    val climbDurationSeconds = (climbEndMs - climbStartMs).coerceAtLeast(1L) / 1000f
    val pausePenalty = (pauseTimeMs / 1000f / climbDurationSeconds).coerceIn(0f, 1f)
    val adjustmentPenalty = (footAdjustmentCount * 0.03f).coerceIn(0f, 0.3f)

    val scaled = (rawEfficiency * 140f).coerceIn(0f, 100f)
    val penalized = scaled * (1f - pausePenalty * 0.3f) * (1f - adjustmentPenalty)
    return penalized.roundToInt().coerceIn(0, 100)
}
