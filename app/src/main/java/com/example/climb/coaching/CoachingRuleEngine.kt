package com.example.climb.coaching

import com.example.climb.analysis.ClimbEvent
import com.example.climb.analysis.ClimbEventType
import com.example.climb.analysis.formatTimestampMs
import com.example.climb.analysis.metrics.AnalysisComputation
import com.example.climb.analysis.metrics.ClimbMetrics
import kotlin.math.roundToInt

interface CoachingRuleEngine {
    fun generateTips(
        computation: AnalysisComputation,
        events: List<ClimbEvent>,
        userHistory: UserClimbingHistory? = null,
    ): List<CoachingTip>
}

/**
 * Rule-based coaching, no LLM involved — every tip must trace back to an actual measured
 * number or detected event (the [CoachingTip.evidence] field), never a fabricated observation.
 * Caps output at one positive observation + up to two priority improvements + one secondary,
 * each with its own drill, so the user isn't overwhelmed with a wall of feedback.
 */
class DeterministicCoachingRuleEngine : CoachingRuleEngine {

    override fun generateTips(
        computation: AnalysisComputation,
        events: List<ClimbEvent>,
        userHistory: UserClimbingHistory?,
    ): List<CoachingTip> {
        val metrics = computation.metrics

        val improvements = listOfNotNull(
            longCruxPauseTip(events, metrics),
            repeatedLockoffsTip(metrics),
            repeatedFootAdjustmentsTip(metrics, events),
            excessivePauseRatioTip(metrics),
        ).sortedBy { it.priority }.take(3)

        val positive = positiveObservationTip(metrics, events)

        return listOfNotNull(positive) + improvements
    }

    private fun longCruxPauseTip(events: List<ClimbEvent>, metrics: ClimbMetrics): CoachingTip? {
        val longest = events
            .filter { it.type == ClimbEventType.LONG_PAUSE && it.startTimestampMs in metrics.climbStartMs..metrics.climbEndMs }
            .maxByOrNull { it.endTimestampMs - it.startTimestampMs }
            ?: return null
        val seconds = (longest.endTimestampMs - longest.startTimestampMs) / 1000f
        return CoachingTip(
            id = "long_crux_pause",
            category = "Pacing",
            title = "Plan before leaving the previous position",
            explanation = "You paused for ${"%.1f".format(seconds)} seconds around ${formatTimestampMs(longest.startTimestampMs)}. " +
                "Try identifying the next hand and foot sequence before moving into this position.",
            drill = "Preview three moves from the ground, then climb without changing the plan unless necessary.",
            timestampMs = longest.startTimestampMs,
            confidence = longest.confidence,
            priority = 1,
            evidence = "${"%.1f".format(seconds)}s pause near ${formatTimestampMs(longest.startTimestampMs)}",
            source = CoachingSource.DETERMINISTIC,
        )
    }

    private fun repeatedLockoffsTip(metrics: ClimbMetrics): CoachingTip? {
        if (metrics.totalDurationMs <= 0) return null
        val ratio = metrics.totalLockoffMs.toFloat() / metrics.totalDurationMs
        if (ratio < 0.12f) return null
        val seconds = metrics.totalLockoffMs / 1000f
        return CoachingTip(
            id = "repeated_lockoffs",
            category = "Technique",
            title = "Look for straighter-arm positions",
            explanation = "You spent approximately ${"%.1f".format(seconds)} seconds in deep bent-arm positions. " +
                "Rotating your hips or changing your feet may reduce the time spent holding tension.",
            drill = "Repeat an easier problem while trying to relax each arm whenever the opposite hand is stable.",
            timestampMs = null,
            confidence = 0.7f,
            priority = 1,
            evidence = "${"%.1f".format(seconds)}s of sustained lock-off (${(ratio * 100).roundToInt()}% of climb time)",
            source = CoachingSource.DETERMINISTIC,
        )
    }

    private fun repeatedFootAdjustmentsTip(metrics: ClimbMetrics, events: List<ClimbEvent>): CoachingTip? {
        if (metrics.possibleFootAdjustments < 3 || metrics.reliableFramePercentage < 50f) return null
        val firstCluster = events.firstOrNull { it.type == ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT }
        val nearText = firstCluster?.let { ", especially near ${formatTimestampMs(it.startTimestampMs)}" }.orEmpty()
        return CoachingTip(
            id = "repeated_foot_adjustments",
            category = "Footwork",
            title = "Commit to foot placements",
            explanation = "You made several possible foot adjustments$nearText. Watch the hold until your foot is settled before shifting weight.",
            drill = "Climb an easy problem silently and avoid repositioning a foot after contact.",
            timestampMs = firstCluster?.startTimestampMs,
            confidence = 0.6f,
            priority = 2,
            evidence = "${metrics.possibleFootAdjustments} possible foot adjustments detected",
            source = CoachingSource.DETERMINISTIC,
        )
    }

    private fun excessivePauseRatioTip(metrics: ClimbMetrics): CoachingTip? {
        if (metrics.totalDurationMs <= 0) return null
        val ratio = metrics.pauseTimeMs.toFloat() / metrics.totalDurationMs
        if (ratio < 0.4f) return null
        return CoachingTip(
            id = "excessive_pause_ratio",
            category = "Pacing",
            title = "Improve movement flow",
            explanation = "About ${(ratio * 100).roundToInt()}% of this climb was spent paused rather than moving. " +
                "Building a continuous rhythm can save energy on longer or harder problems.",
            drill = "Repeat the problem at lower intensity with the goal of continuous movement.",
            timestampMs = null,
            confidence = 0.7f,
            priority = 2,
            evidence = "${(ratio * 100).roundToInt()}% of total time spent paused",
            source = CoachingSource.DETERMINISTIC,
        )
    }

    /** Only ever returns a tip when there's real supporting evidence — no praise without a
     * measured signal behind it. */
    private fun positiveObservationTip(metrics: ClimbMetrics, events: List<ClimbEvent>): CoachingTip? {
        val efficient = events.firstOrNull { it.type == ClimbEventType.EFFICIENT_SEQUENCE }
        return when {
            efficient != null -> CoachingTip(
                id = "positive_efficient_sequence",
                category = "Positive",
                title = "Smooth, continuous sequence",
                explanation = "Between ${formatTimestampMs(efficient.startTimestampMs)} and ${formatTimestampMs(efficient.endTimestampMs)} " +
                    "you climbed with no long pauses or lock-offs — that's the kind of flow to build on.",
                drill = null,
                timestampMs = efficient.startTimestampMs,
                confidence = efficient.confidence,
                priority = 0,
                evidence = "Efficient sequence ${formatTimestampMs(efficient.startTimestampMs)}-${formatTimestampMs(efficient.endTimestampMs)}",
                source = CoachingSource.DETERMINISTIC,
            )
            metrics.straightArmPercentage >= 60f -> CoachingTip(
                id = "positive_straight_arm",
                category = "Positive",
                title = "Good use of straight-arm positions",
                explanation = "You kept relatively straight arms for ${metrics.straightArmPercentage.roundToInt()}% of the climb, " +
                    "which saves energy compared to climbing bent-armed.",
                drill = null,
                timestampMs = null,
                confidence = 0.6f,
                priority = 0,
                evidence = "${metrics.straightArmPercentage.roundToInt()}% straight-arm time",
                source = CoachingSource.DETERMINISTIC,
            )
            metrics.possibleFootAdjustments <= 1 -> CoachingTip(
                id = "positive_committed_feet",
                category = "Positive",
                title = "Committed foot placements",
                explanation = "You barely repositioned your feet during this climb — that's efficient, confident footwork.",
                drill = null,
                timestampMs = null,
                confidence = 0.5f,
                priority = 0,
                evidence = "${metrics.possibleFootAdjustments} possible foot adjustments detected",
                source = CoachingSource.DETERMINISTIC,
            )
            else -> null
        }
    }
}
