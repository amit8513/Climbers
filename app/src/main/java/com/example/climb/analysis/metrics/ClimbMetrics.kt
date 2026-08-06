package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame

data class ClimbMetrics(
    val totalDurationMs: Long,
    val activeMovementMs: Long,
    val pauseTimeMs: Long,
    val pauseCount: Int,
    val longestPauseMs: Long,
    val leftLockoffMs: Long,
    val rightLockoffMs: Long,
    val totalLockoffMs: Long,
    val longestLockoffMs: Long,
    val possibleFootAdjustments: Int,
    val possibleFootSlips: Int,
    val possibleDisengagedLegSegments: Int,
    val straightArmPercentage: Float,
    val estimatedMovementEfficiency: Int,
    val reliableFramePercentage: Float,
    val climbStartMs: Long,
    val climbEndMs: Long,
    val highStepCount: Int,
    val possibleStabilityLossCount: Int,
    val possibleFallCandidateCount: Int,
    val hasFinishStabilization: Boolean,
    val possibleMissedReachCount: Int,
)

/** Metrics plus the raw detection results they were built from — [com.example.climb.analysis.buildEvents]
 * consumes these directly rather than re-running every detector a second time. */
data class AnalysisComputation(
    val metrics: ClimbMetrics,
    val pauses: List<PauseSegment>,
    val lockoffs: List<LockoffSegment>,
    val footAdjustments: List<FootAdjustmentEvent>,
    val footSlips: List<Long>,
    val disengagedLegs: List<DisengagedLegSegment>,
    val highSteps: List<HighStepEvent>,
    val stabilityLossEvents: List<StabilityLossEvent>,
    val recoveries: List<RecoveryEvent>,
    val fallCandidates: List<FallCandidateEvent>,
    val finishStabilization: FinishStabilizationEvent?,
    val missedReachCandidates: List<MissedReachCandidateEvent>,
)

fun computeAnalysis(frames: List<PoseFrame>, config: MetricsConfiguration = MetricsConfiguration()): AnalysisComputation {
    if (frames.size < 2) {
        val ts = frames.firstOrNull()?.timestampMs ?: 0L
        return AnalysisComputation(
            metrics = ClimbMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0f, 0, 0f, ts, ts, 0, 0, 0, false, 0),
            pauses = emptyList(),
            lockoffs = emptyList(),
            footAdjustments = emptyList(),
            footSlips = emptyList(),
            disengagedLegs = emptyList(),
            highSteps = emptyList(),
            stabilityLossEvents = emptyList(),
            recoveries = emptyList(),
            fallCandidates = emptyList(),
            finishStabilization = null,
            missedReachCandidates = emptyList(),
        )
    }

    val totalDurationMs = frames.last().timestampMs - frames.first().timestampMs
    val reliableFramePercentage = frames.count { it.isReliable }.toFloat() / frames.size * 100f

    val velocities = smoothVelocities(computeHipVelocities(frames))
    val (climbStartMs, climbEndMs) = estimateClimbBounds(frames, velocities, config)

    val pauses = detectPauses(velocities, config)
    val pauseTimeMs = pauses.sumOf { it.durationMs }
    val longestPauseMs = pauses.maxOfOrNull { it.durationMs } ?: 0L
    val activeMovementMs = (totalDurationMs - pauseTimeMs).coerceAtLeast(0L)

    val lockoffs = detectLockoffs(frames, config)
    val leftLockoffMs = lockoffs.filter { it.side == Side.LEFT }.sumOf { it.durationMs }
    val rightLockoffMs = lockoffs.filter { it.side == Side.RIGHT }.sumOf { it.durationMs }
    val longestLockoffMs = lockoffs.maxOfOrNull { it.durationMs } ?: 0L

    val footAdjustments = detectFootAdjustments(frames, config)
    val footSlips = detectPossibleFootSlips(frames, config)
    val disengagedLegs = detectDisengagedLeg(frames, config)

    val highSteps = detectHighSteps(frames, config)
    val stabilityLossEvents = detectStabilityLoss(velocities, config)
    val recoveries = detectRecoveries(velocities, stabilityLossEvents, config)
    val fallCandidates = detectFallCandidates(frames, config)
    val finishStabilization = detectFinishStabilization(pauses, climbEndMs, config)
    val missedReachCandidates = detectMissedReachCandidates(frames, fallCandidates, config)

    val metrics = ClimbMetrics(
        totalDurationMs = totalDurationMs,
        activeMovementMs = activeMovementMs,
        pauseTimeMs = pauseTimeMs,
        pauseCount = pauses.size,
        longestPauseMs = longestPauseMs,
        leftLockoffMs = leftLockoffMs,
        rightLockoffMs = rightLockoffMs,
        totalLockoffMs = leftLockoffMs + rightLockoffMs,
        longestLockoffMs = longestLockoffMs,
        possibleFootAdjustments = footAdjustments.size,
        possibleFootSlips = footSlips.size,
        possibleDisengagedLegSegments = disengagedLegs.size,
        straightArmPercentage = straightArmPercentage(frames, config),
        estimatedMovementEfficiency = estimateMovementEfficiency(frames, climbStartMs, climbEndMs, footAdjustments.size, pauseTimeMs, config),
        reliableFramePercentage = reliableFramePercentage,
        climbStartMs = climbStartMs,
        climbEndMs = climbEndMs,
        highStepCount = highSteps.size,
        possibleStabilityLossCount = stabilityLossEvents.size,
        possibleFallCandidateCount = fallCandidates.size,
        hasFinishStabilization = finishStabilization != null,
        possibleMissedReachCount = missedReachCandidates.size,
    )

    return AnalysisComputation(
        metrics = metrics,
        pauses = pauses,
        lockoffs = lockoffs,
        footAdjustments = footAdjustments,
        footSlips = footSlips,
        disengagedLegs = disengagedLegs,
        highSteps = highSteps,
        stabilityLossEvents = stabilityLossEvents,
        recoveries = recoveries,
        fallCandidates = fallCandidates,
        finishStabilization = finishStabilization,
        missedReachCandidates = missedReachCandidates,
    )
}
