package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType

// -- High step -----------------------------------------------------------------------------

data class HighStepEvent(val side: Side, val timestampMs: Long, val hipRelativeHeight: Float)

/**
 * A foot that settles (goes still) at or above the hip line, normalized by body height — the
 * most direct pose-only signature of a "high step" in climbing. Only the highest moment in each
 * settled run is kept, not every qualifying frame.
 */
fun detectHighSteps(frames: List<PoseFrame>, config: MetricsConfiguration): List<HighStepEvent> {
    val events = mutableListOf<HighStepEvent>()
    for (side in Side.entries) {
        val footType = if (side == Side.LEFT) PoseLandmarkType.LEFT_FOOT_INDEX else PoseLandmarkType.RIGHT_FOOT_INDEX
        val velocityByTimestamp = computeLandmarkVelocities(frames, footType).associateBy { it.timestampMs }

        var bestFrame: PoseFrame? = null
        var bestRatio = 0f
        fun flush() {
            bestFrame?.let { events += HighStepEvent(side, it.timestampMs, bestRatio) }
            bestFrame = null
            bestRatio = 0f
        }

        for (frame in frames) {
            if (!frame.isReliable) {
                flush()
                continue
            }
            val hip = frame.hipCenter()
            val foot = frame.landmark(footType)
            val bodyHeight = frame.bodyHeightEstimate()
            if (hip == null || foot == null || bodyHeight == null) {
                flush()
                continue
            }
            val velocity = velocityByTimestamp[frame.timestampMs]?.normalizedVelocity ?: Float.MAX_VALUE
            val ratio = (hip.y - foot.normalizedY) / bodyHeight
            val isHighAndStill = ratio >= config.highStepHipRatio && velocity < config.stillVelocityThreshold
            if (isHighAndStill) {
                if (ratio > bestRatio) {
                    bestRatio = ratio
                    bestFrame = frame
                }
            } else {
                flush()
            }
        }
        flush()
    }
    return events.sortedBy { it.timestampMs }
}

// -- Stability loss / recovery ---------------------------------------------------------------

data class StabilityLossEvent(val timestampMs: Long, val velocityJump: Float)

/** A sudden frame-to-frame jump in hip velocity — a jerk, as distinct from a smooth
 * acceleration into a deliberate dynamic move. Both can look similar in raw speed alone, so this
 * is always worded as a possible loss of control, not a confirmed one. */
fun detectStabilityLoss(velocities: List<TimedVelocity>, config: MetricsConfiguration): List<StabilityLossEvent> {
    val events = mutableListOf<StabilityLossEvent>()
    for (i in 1 until velocities.size) {
        val jump = velocities[i].normalizedVelocity - velocities[i - 1].normalizedVelocity
        if (jump >= config.stabilityLossVelocityJumpThreshold) {
            events += StabilityLossEvent(velocities[i].timestampMs, jump)
        }
    }
    return events
}

data class RecoveryEvent(val stabilityLossTimestampMs: Long, val recoveredAtMs: Long, val recoveryDurationMs: Long)

/** How long after a [StabilityLossEvent] it took hip velocity to settle back below
 * [MetricsConfiguration.stillVelocityThreshold] and stay there — omitted entirely when the
 * climb ends (or tracking degrades) before recovery is ever confirmed. */
fun detectRecoveries(velocities: List<TimedVelocity>, stabilityLossEvents: List<StabilityLossEvent>, config: MetricsConfiguration): List<RecoveryEvent> {
    val results = mutableListOf<RecoveryEvent>()
    for (event in stabilityLossEvents) {
        var stableSinceMs: Long? = null
        for (v in velocities) {
            if (v.timestampMs <= event.timestampMs) continue
            if (v.normalizedVelocity < config.stillVelocityThreshold) {
                if (stableSinceMs == null) stableSinceMs = v.timestampMs
                val duration = v.timestampMs - stableSinceMs
                if (duration >= config.recoveryStableDurationMs) {
                    results += RecoveryEvent(event.timestampMs, stableSinceMs, v.timestampMs - event.timestampMs)
                    break
                }
            } else {
                stableSinceMs = null
            }
        }
    }
    return results
}

// -- Fall candidate ---------------------------------------------------------------------------

data class FallCandidateEvent(val timestampMs: Long, val downwardVelocity: Float)

/** A large, predominantly downward hip-center velocity — well above the threshold that flags an
 * ordinary dynamic move, since falls are both fast and specifically downward rather than toward
 * a hold. This cannot distinguish a real fall from an unusually hard controlled drop, so it is
 * always a "candidate," never a confirmed fall. */
fun detectFallCandidates(frames: List<PoseFrame>, config: MetricsConfiguration): List<FallCandidateEvent> {
    val results = mutableListOf<FallCandidateEvent>()
    for (i in 1 until frames.size) {
        val prev = frames[i - 1]
        val curr = frames[i]
        if (!prev.isReliable || !curr.isReliable) continue
        val prevHip = prev.hipCenter() ?: continue
        val currHip = curr.hipCenter() ?: continue
        val bodyHeight = curr.bodyHeightEstimate() ?: prev.bodyHeightEstimate() ?: continue
        val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000f
        if (dtSeconds <= 0f) continue
        // Normalized Y increases downward in image coordinates.
        val downwardVelocity = (currHip.y - prevHip.y) / bodyHeight / dtSeconds
        if (downwardVelocity >= config.fallVelocityThreshold) {
            results += FallCandidateEvent(curr.timestampMs, downwardVelocity)
        }
    }
    return results
}

// -- Finish stabilization ----------------------------------------------------------------------

data class FinishStabilizationEvent(val startMs: Long, val endMs: Long)

/** The last pause of the climb, only when it both ends close to the estimated climb end and
 * lasts long enough — a controlled finish, as distinct from a fall or an arbitrary late pause
 * mid-sequence. */
fun detectFinishStabilization(pauses: List<PauseSegment>, climbEndMs: Long, config: MetricsConfiguration): FinishStabilizationEvent? {
    val candidate = pauses
        .filter { it.endMs >= climbEndMs - config.finishStabilizationMaxGapFromEndMs }
        .filter { it.durationMs >= config.finishStabilizationMinDurationMs }
        .maxByOrNull { it.endMs }
        ?: return null
    return FinishStabilizationEvent(candidate.startMs, candidate.endMs)
}

// -- Missed reach candidate ---------------------------------------------------------------------

data class MissedReachCandidateEvent(val side: Side, val reachTimestampMs: Long, val fallTimestampMs: Long)

/**
 * A fast hand movement shortly before a [FallCandidateEvent] — the closest pose-only proxy for
 * "reached for something and then fell," without ever claiming a hold was targeted, touched, or
 * missed, since none of that is knowable from pose alone.
 */
fun detectMissedReachCandidates(frames: List<PoseFrame>, fallCandidates: List<FallCandidateEvent>, config: MetricsConfiguration): List<MissedReachCandidateEvent> {
    if (fallCandidates.isEmpty()) return emptyList()
    val results = mutableListOf<MissedReachCandidateEvent>()
    for (side in Side.entries) {
        val wristType = if (side == Side.LEFT) PoseLandmarkType.LEFT_WRIST else PoseLandmarkType.RIGHT_WRIST
        val velocities = computeLandmarkVelocities(frames, wristType)
        for (fall in fallCandidates) {
            val reach = velocities
                .filter { it.timestampMs in (fall.timestampMs - config.missedReachWindowMs) until fall.timestampMs }
                .maxByOrNull { it.normalizedVelocity }
                ?.takeIf { it.normalizedVelocity >= config.dynamicMoveVelocityThreshold }
            if (reach != null) {
                results += MissedReachCandidateEvent(side, reach.timestampMs, fall.timestampMs)
            }
        }
    }
    return results.sortedBy { it.reachTimestampMs }
}
