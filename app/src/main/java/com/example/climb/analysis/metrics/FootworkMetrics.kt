package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dedicated leg/foot analysis, mirroring what [LockoffDetector] and [BodyGeometry] already do
 * for arms — until now the only foot-derived signals anywhere in this pipeline were byproducts
 * of other checks ([FootAdjustmentDetector], [ExpandedMovementDetector]'s high-step detection,
 * and [DisengagedLegDetector]'s knee angle), and [scorePower][com.example.climb.analysis.scoring]
 * had no leg/foot signal at all.
 */

// -- Leg drive (feeds Power) ------------------------------------------------------------------

data class LegDriveCandidateEvent(
    val side: Side,
    val timestampMs: Long,
    val extensionDegreesPerSecond: Float,
    val dynamicMoveTimestampMs: Long,
)

private fun kneeAngleSeries(frames: List<PoseFrame>, side: Side): List<Pair<Long, Float>> {
    val hipType = if (side == Side.LEFT) PoseLandmarkType.LEFT_HIP else PoseLandmarkType.RIGHT_HIP
    val kneeType = if (side == Side.LEFT) PoseLandmarkType.LEFT_KNEE else PoseLandmarkType.RIGHT_KNEE
    val ankleType = if (side == Side.LEFT) PoseLandmarkType.LEFT_ANKLE else PoseLandmarkType.RIGHT_ANKLE
    val result = mutableListOf<Pair<Long, Float>>()
    for (frame in frames) {
        if (!frame.isReliable) continue
        val hip = frame.landmark(hipType) ?: continue
        val knee = frame.landmark(kneeType) ?: continue
        val ankle = frame.landmark(ankleType) ?: continue
        result += frame.timestampMs to kneeAngleDegrees(hip, knee, ankle)
    }
    return result
}

/**
 * A knee rapidly straightening (extending) in the window right before a large hip-velocity move
 * — the closest pose-only proxy for "pushed off a foothold into that move," as distinct from a
 * move driven purely by pulling with the arms. Always a "candidate": pose alone can't confirm
 * the foot was actually weighted on a hold while extending.
 */
fun detectLegDriveCandidates(
    frames: List<PoseFrame>,
    dynamicMoveTimestampsMs: List<Long>,
    config: MetricsConfiguration,
): List<LegDriveCandidateEvent> {
    if (dynamicMoveTimestampsMs.isEmpty()) return emptyList()
    val results = mutableListOf<LegDriveCandidateEvent>()
    for (side in Side.entries) {
        val series = kneeAngleSeries(frames, side)
        if (series.size < 2) continue
        for (moveMs in dynamicMoveTimestampsMs) {
            var bestRate = 0f
            var bestTimestamp = moveMs
            for (i in 1 until series.size) {
                val (prevMs, prevAngle) = series[i - 1]
                val (currMs, currAngle) = series[i]
                if (currMs < moveMs - config.legDriveLookbackWindowMs || currMs > moveMs) continue
                val dtSeconds = (currMs - prevMs) / 1000f
                if (dtSeconds <= 0f) continue
                val rate = (currAngle - prevAngle) / dtSeconds
                if (rate > bestRate) {
                    bestRate = rate
                    bestTimestamp = currMs
                }
            }
            if (bestRate >= config.legDriveExtensionDegreesPerSecond) {
                results += LegDriveCandidateEvent(side, bestTimestamp, bestRate, moveMs)
            }
        }
    }
    return results.sortedBy { it.timestampMs }
}

// -- Knee range of motion (feeds Flexibility / ObservedMovementRange) -------------------------

/** Average, across both legs, of the largest knee-angle swing (most bent to most straight)
 * observed anywhere in the climb — the leg-side counterpart to arm/shoulder range, and the
 * "Knee range of motion" signal the flexibility category was missing entirely. */
fun kneeRangeOfMotionDegrees(frames: List<PoseFrame>): Float {
    val ranges = Side.entries.mapNotNull { side ->
        val angles = kneeAngleSeries(frames, side).map { it.second }
        if (angles.size < 2) null else angles.max() - angles.min()
    }
    return if (ranges.isEmpty()) 0f else ranges.sum() / ranges.size
}

// -- Foot travel + weight asymmetry (feeds Balance) -------------------------------------------

private fun footTravelNormalized(frames: List<PoseFrame>, footType: PoseLandmarkType): Float {
    var total = 0f
    var previous: PoseLandmark? = null
    for (frame in frames) {
        if (!frame.isReliable) {
            previous = null
            continue
        }
        val landmark = frame.landmark(footType) ?: continue
        val bodyHeight = frame.bodyHeightEstimate() ?: continue
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

/** Combined left+right foot-index travel, body-height normalized — how much the feet moved
 * overall during the climb, independent of what caused it. */
fun totalFootTravelNormalized(frames: List<PoseFrame>): Float =
    footTravelNormalized(frames, PoseLandmarkType.LEFT_FOOT_INDEX) + footTravelNormalized(frames, PoseLandmarkType.RIGHT_FOOT_INDEX)

/**
 * How lopsided foot movement was between the two feet, from 0 (both feet moved equally) to 1
 * (only one foot ever moved). Deliberately neutral: one foot moving far more than the other is
 * completely normal climbing (one foot usually stays planted while the other explores), not a
 * flaw — this is reported as an observation, not scored as good or bad on its own.
 */
fun footWeightAsymmetry(frames: List<PoseFrame>): Float {
    val left = footTravelNormalized(frames, PoseLandmarkType.LEFT_FOOT_INDEX)
    val right = footTravelNormalized(frames, PoseLandmarkType.RIGHT_FOOT_INDEX)
    val total = left + right
    if (total < 0.01f) return 0f
    return (abs(left - right) / total).coerceIn(0f, 1f)
}

// -- Foot placement stability (feeds Technique) ------------------------------------------------

private data class FootVelocitySample(val timestampMs: Long, val velocity: Float)

private fun footVelocitySamples(frames: List<PoseFrame>, footType: PoseLandmarkType): List<FootVelocitySample> {
    val result = mutableListOf<FootVelocitySample>()
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
        result += FootVelocitySample(curr.timestampMs, (dist / bodyHeight) / dtSeconds)
    }
    return result
}

/**
 * 0-100: how still a "placed" foot actually stayed once settled, averaged across every settled
 * window of both feet — residual jitter under [MetricsConfiguration.stillVelocityThreshold] still
 * varies between a foot planted firmly on a hold and one being constantly micro-adjusted for
 * grip. Returns 0 when there isn't enough settled-foot data to measure this at all, matching
 * [estimateMovementEfficiency]'s existing convention for insufficient data.
 */
fun footStabilityScore(frames: List<PoseFrame>, config: MetricsConfiguration): Int {
    val windowAverageJitters = mutableListOf<Float>()
    for (footType in listOf(PoseLandmarkType.LEFT_FOOT_INDEX, PoseLandmarkType.RIGHT_FOOT_INDEX)) {
        var windowStart: Long? = null
        var windowLast: Long? = null
        var windowJitterSum = 0f
        var windowSampleCount = 0
        fun flush() {
            val start = windowStart
            val last = windowLast
            if (start != null && last != null && last - start >= config.footSettleDurationMs && windowSampleCount > 0) {
                windowAverageJitters += windowJitterSum / windowSampleCount
            }
            windowStart = null
            windowLast = null
            windowJitterSum = 0f
            windowSampleCount = 0
        }
        for (sample in footVelocitySamples(frames, footType)) {
            if (sample.velocity < config.stillVelocityThreshold) {
                if (windowStart == null) windowStart = sample.timestampMs
                windowLast = sample.timestampMs
                windowJitterSum += sample.velocity
                windowSampleCount++
            } else {
                flush()
            }
        }
        flush()
    }
    if (windowAverageJitters.isEmpty()) return 0
    val averageJitter = windowAverageJitters.sum() / windowAverageJitters.size
    return (100f - averageJitter * config.footJitterScoreScale).roundToInt().coerceIn(0, 100)
}
