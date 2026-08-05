package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType

data class FootAdjustmentEvent(val side: Side, val timestampMs: Long)

private fun footVelocities(frames: List<PoseFrame>, footType: PoseLandmarkType): List<TimedVelocity> {
    val result = mutableListOf<TimedVelocity>()
    for (i in 1 until frames.size) {
        val prev = frames[i - 1]
        val curr = frames[i]
        if (!prev.isReliable || !curr.isReliable) continue
        val prevLandmark = prev.landmark(footType) ?: continue
        val currLandmark = curr.landmark(footType) ?: continue
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

private data class SettlePoint(val timestampMs: Long, val position: Point2D)

/** A "settle" is a foot going still for at least [MetricsConfiguration.footSettleDurationMs] —
 * a candidate placement. Recorded once per still run, at the moment it's confirmed settled. */
private fun findSettlePoints(frames: List<PoseFrame>, velocities: List<TimedVelocity>, footType: PoseLandmarkType, config: MetricsConfiguration): List<SettlePoint> {
    val frameByTimestamp = frames.associateBy { it.timestampMs }
    val settlePoints = mutableListOf<SettlePoint>()
    var stillStart: Long? = null
    var recordedForRun = false
    for (v in velocities) {
        if (v.normalizedVelocity < config.stillVelocityThreshold) {
            if (stillStart == null) {
                stillStart = v.timestampMs
                recordedForRun = false
            }
            if (!recordedForRun && v.timestampMs - stillStart!! >= config.footSettleDurationMs) {
                frameByTimestamp[v.timestampMs]?.landmark(footType)?.let { landmark ->
                    settlePoints += SettlePoint(v.timestampMs, Point2D(landmark.normalizedX, landmark.normalizedY))
                }
                recordedForRun = true
            }
        } else {
            stillStart = null
            recordedForRun = false
        }
    }
    return settlePoints
}

/**
 * A possible foot adjustment: the foot settles, then re-settles at a meaningfully different
 * spot within [MetricsConfiguration.footAdjustmentWindowMs] — never claimed as a certain hold
 * re-contact, since no hold positions are marked.
 */
fun detectFootAdjustments(frames: List<PoseFrame>, config: MetricsConfiguration): List<FootAdjustmentEvent> {
    val sides = listOf(Side.LEFT to PoseLandmarkType.LEFT_FOOT_INDEX, Side.RIGHT to PoseLandmarkType.RIGHT_FOOT_INDEX)
    val events = mutableListOf<FootAdjustmentEvent>()
    for ((side, footType) in sides) {
        val settlePoints = findSettlePoints(frames, footVelocities(frames, footType), footType, config)
        for (i in 1 until settlePoints.size) {
            val previous = settlePoints[i - 1]
            val current = settlePoints[i]
            val gapMs = current.timestampMs - previous.timestampMs
            if (gapMs in 1..config.footAdjustmentWindowMs &&
                distance(previous.position, current.position) >= config.footAdjustmentDisplacementThreshold
            ) {
                events += FootAdjustmentEvent(side, current.timestampMs)
            }
        }
    }
    return events.sortedBy { it.timestampMs }
}

private data class FootSample(val timestampMs: Long, val velocity: Float, val downwardVelocity: Float)

private fun footSamples(frames: List<PoseFrame>, footType: PoseLandmarkType): List<FootSample> {
    val samples = mutableListOf<FootSample>()
    for (i in 1 until frames.size) {
        val prev = frames[i - 1]
        val curr = frames[i]
        if (!prev.isReliable || !curr.isReliable) continue
        val prevLandmark = prev.landmark(footType) ?: continue
        val currLandmark = curr.landmark(footType) ?: continue
        val bodyHeight = curr.bodyHeightEstimate() ?: prev.bodyHeightEstimate() ?: continue
        val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000f
        if (dtSeconds <= 0f) continue
        val dist = distance(
            Point2D(prevLandmark.normalizedX, prevLandmark.normalizedY),
            Point2D(currLandmark.normalizedX, currLandmark.normalizedY),
        )
        // Normalized Y increases downward in image coordinates.
        val downward = (currLandmark.normalizedY - prevLandmark.normalizedY) / bodyHeight / dtSeconds
        samples += FootSample(curr.timestampMs, (dist / bodyHeight) / dtSeconds, downward)
    }
    return samples
}

/**
 * Conservative by design: a downward velocity spike alone also happens during completely normal
 * footwork (stepping down, kicking into a foot hold), so this only fires when the foot was
 * actually settled for [MetricsConfiguration.footSlipPriorStabilityMs] immediately beforehand —
 * i.e. it *lost* a stable position, rather than simply moving fast. Labelled "possible"
 * everywhere it surfaces; this is a coarse proxy, not a confirmed slip.
 */
fun detectPossibleFootSlips(frames: List<PoseFrame>, config: MetricsConfiguration): List<Long> {
    val slipTimestamps = mutableListOf<Long>()
    for (footType in listOf(PoseLandmarkType.LEFT_FOOT_INDEX, PoseLandmarkType.RIGHT_FOOT_INDEX)) {
        var stableSinceMs: Long? = null
        for (sample in footSamples(frames, footType)) {
            if (sample.velocity < config.stillVelocityThreshold) {
                if (stableSinceMs == null) stableSinceMs = sample.timestampMs
                continue
            }
            val stableDurationMs = stableSinceMs?.let { sample.timestampMs - it } ?: 0L
            if (stableDurationMs >= config.footSlipPriorStabilityMs && sample.downwardVelocity >= config.footSlipVelocityThreshold) {
                slipTimestamps += sample.timestampMs
            }
            stableSinceMs = null
        }
    }
    return slipTimestamps.distinct().sorted()
}
