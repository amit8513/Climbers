package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType

/** Same shape and normalization as [computeHipVelocities], but for a single tracked landmark
 * (e.g. a wrist) rather than the hip-center midpoint — used wherever one specific limb's speed
 * matters on its own, like flagging a fast reach toward a hold. */
fun computeLandmarkVelocities(frames: List<PoseFrame>, landmarkType: PoseLandmarkType): List<TimedVelocity> {
    val result = mutableListOf<TimedVelocity>()
    for (i in 1 until frames.size) {
        val prev = frames[i - 1]
        val curr = frames[i]
        if (!prev.isReliable || !curr.isReliable) continue
        val prevLandmark = prev.landmark(landmarkType) ?: continue
        val currLandmark = curr.landmark(landmarkType) ?: continue
        val bodyHeight = curr.bodyHeightEstimate() ?: prev.bodyHeightEstimate() ?: continue
        val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000f
        if (dtSeconds <= 0f) continue
        val dist = distance(
            Point2D(prevLandmark.normalizedX, prevLandmark.normalizedY),
            Point2D(currLandmark.normalizedX, currLandmark.normalizedY),
        )
        result += TimedVelocity(curr.timestampMs, (dist / bodyHeight) / dtSeconds)
    }
    return result
}
