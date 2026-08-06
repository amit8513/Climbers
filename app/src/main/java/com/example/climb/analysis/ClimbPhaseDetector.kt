package com.example.climb.analysis

import com.example.climb.analysis.metrics.AnalysisComputation
import com.example.climb.analysis.metrics.MetricsConfiguration
import com.example.climb.pose.PoseFrame

enum class ClimbPhaseType {
    PREPARATION,
    ACTIVE_CLIMBING,
    STATIC_POSITION,
    REST,
    DYNAMIC_MOVEMENT,
    RECOVERY,
    POSSIBLE_SLIP,
    FALL,
    FINISH,
    INCOMPLETE,
}

data class ClimbPhase(
    val type: ClimbPhaseType,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val supportingSignals: String,
)

private data class PhaseCandidate(
    val type: ClimbPhaseType,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val supportingSignals: String,
    val priority: Int,
)

/**
 * Segments the climb into a simple, non-overlapping phase timeline built on top of the events
 * [buildEvents] already detected, rather than re-deriving raw signals a second time. Higher-
 * priority phase candidates (a possible fall) win the moments they cover over lower-priority
 * ones (a generic pause); any leftover time inside the climb window defaults to ACTIVE_CLIMBING
 * rather than being left unlabeled. Does not assume every video ends in success — FINISH is only
 * assigned when a real controlled-stillness signal supports it, and INCOMPLETE only when the
 * climb ends shortly after a possible fall or slip with no confirmed recovery.
 */
fun detectPhases(frames: List<PoseFrame>, computation: AnalysisComputation, events: List<ClimbEvent>, config: MetricsConfiguration): List<ClimbPhase> {
    if (frames.isEmpty()) return emptyList()
    val metrics = computation.metrics
    val videoStartMs = frames.first().timestampMs

    val candidates = mutableListOf<PhaseCandidate>()

    if (metrics.climbStartMs > videoStartMs + 300L) {
        candidates += PhaseCandidate(ClimbPhaseType.PREPARATION, videoStartMs, metrics.climbStartMs, 0.6f, "Before first sustained movement", priority = 1)
    }

    events.filter { it.type == ClimbEventType.POSSIBLE_FALL || it.type == ClimbEventType.POSSIBLE_MISSED_REACH }.forEach {
        candidates += PhaseCandidate(ClimbPhaseType.FALL, it.startTimestampMs, it.endTimestampMs, it.confidence, it.userVisibleTitle, priority = 6)
    }
    events.filter { it.type == ClimbEventType.POSSIBLE_FOOT_SLIP }.forEach {
        candidates += PhaseCandidate(ClimbPhaseType.POSSIBLE_SLIP, it.startTimestampMs, it.endTimestampMs, it.confidence, it.userVisibleTitle, priority = 5)
    }
    events.filter { it.type == ClimbEventType.POSSIBLE_STABILITY_LOSS }.forEach {
        candidates += PhaseCandidate(ClimbPhaseType.RECOVERY, it.startTimestampMs, it.endTimestampMs, it.confidence, it.userVisibleTitle, priority = 4)
    }
    events.filter { it.type == ClimbEventType.LARGE_DYNAMIC_MOVE }.forEach {
        val start = (it.startTimestampMs - 400L).coerceAtLeast(metrics.climbStartMs)
        val end = (it.endTimestampMs + 400L).coerceAtMost(metrics.climbEndMs)
        candidates += PhaseCandidate(ClimbPhaseType.DYNAMIC_MOVEMENT, start, end, it.confidence, it.userVisibleTitle, priority = 3)
    }
    computation.pauses.forEach { pause ->
        val type = if (pause.durationMs >= config.longPauseDurationMs) ClimbPhaseType.REST else ClimbPhaseType.STATIC_POSITION
        candidates += PhaseCandidate(type, pause.startMs, pause.endMs, 0.7f, "Pause of ${"%.1f".format(pause.durationMs / 1000f)}s", priority = 2)
    }

    val finish = computation.finishStabilization
    if (finish != null) {
        candidates += PhaseCandidate(ClimbPhaseType.FINISH, finish.startMs, finish.endMs, 0.6f, "Controlled stillness at the end of the climb", priority = 7)
    } else {
        val lastFallOrSlip = candidates
            .filter { it.type == ClimbPhaseType.FALL || it.type == ClimbPhaseType.POSSIBLE_SLIP }
            .maxByOrNull { it.endMs }
        if (lastFallOrSlip != null && lastFallOrSlip.endMs >= metrics.climbEndMs - 1_500L) {
            candidates += PhaseCandidate(
                ClimbPhaseType.INCOMPLETE,
                lastFallOrSlip.startMs,
                metrics.climbEndMs,
                0.4f,
                "Climb ended shortly after a possible fall or slip, with no confirmed controlled finish",
                priority = 7,
            )
        }
    }

    // Resolve overlaps: highest-priority candidates claim their time range first; anything that
    // would overlap an already-claimed range is dropped rather than layered on top of it.
    val resolved = mutableListOf<PhaseCandidate>()
    for (candidate in candidates.sortedByDescending { it.priority }) {
        val overlapsClaimed = resolved.any { it.startMs < candidate.endMs && candidate.startMs < it.endMs }
        if (!overlapsClaimed) resolved += candidate
    }

    val specificPhases = resolved.sortedBy { it.startMs }.map { ClimbPhase(it.type, it.startMs, it.endMs, it.confidence, it.supportingSignals) }

    // Fill any gap inside [climbStartMs, climbEndMs] not covered by a more specific phase with
    // ACTIVE_CLIMBING, so the whole attempt window is always accounted for.
    val filled = mutableListOf<ClimbPhase>()
    filled += specificPhases.filter { it.endMs <= metrics.climbStartMs }
    var cursor = metrics.climbStartMs
    for (phase in specificPhases.filter { it.startMs >= metrics.climbStartMs && it.endMs <= metrics.climbEndMs }.sortedBy { it.startMs }) {
        if (phase.startMs > cursor) {
            filled += ClimbPhase(ClimbPhaseType.ACTIVE_CLIMBING, cursor, phase.startMs, 0.5f, "General climbing movement")
        }
        filled += phase
        cursor = maxOf(cursor, phase.endMs)
    }
    if (cursor < metrics.climbEndMs) {
        filled += ClimbPhase(ClimbPhaseType.ACTIVE_CLIMBING, cursor, metrics.climbEndMs, 0.5f, "General climbing movement")
    }
    filled += specificPhases.filter { it.startMs > metrics.climbEndMs }

    return filled.sortedBy { it.startMs }
}
