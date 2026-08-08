package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.pose.PoseLandmarkType
import kotlin.math.roundToInt

data class StrengthItem(
    val title: String,
    val evidence: String,
    val whyItMatters: String,
    val startTimestampMs: Long?,
    val endTimestampMs: Long?,
    val confidence: Float,
    val supportingEventIds: List<String>,
    val relatedLandmarks: Set<PoseLandmarkType> = emptySet(),
)

data class ImprovementItem(
    val issue: String,
    val measuredEvidence: String,
    val impact: String,
    val recommendation: String,
    val startTimestampMs: Long?,
    val endTimestampMs: Long?,
    val confidence: Float,
    val supportingEventIds: List<String>,
    val relatedLandmarks: Set<PoseLandmarkType> = emptySet(),
)

data class BetaOpportunity(
    val timestampMs: Long?,
    val observedIssue: String,
    val evidence: String,
    val suggestedAlternative: String,
    val confidence: Float,
    val requiresRouteContext: Boolean,
    val relatedLandmarks: Set<PoseLandmarkType> = emptySet(),
)

/**
 * Strengths, improvements, and beta opportunities are all derived here from [ClimbEvent]s and
 * [ClimbMetrics] already persisted and loaded on the analysis screen — same presentation-layer
 * approach as [buildQualityIndicators] and [buildTechnicalPerformanceReport], so none of this
 * needs a new pipeline stage, a new persisted field, or a Room migration.
 */
fun buildStrengths(metrics: ClimbMetrics, events: List<ClimbEvent>): List<StrengthItem> {
    val reliabilityConfidence = (metrics.reliableFramePercentage / 100f).coerceIn(0f, 1f)
    val strengths = mutableListOf<StrengthItem>()

    events.filter { it.type == ClimbEventType.EFFICIENT_SEQUENCE }.forEach { e ->
        strengths += StrengthItem(
            title = "Smooth, continuous movement",
            evidence = e.userVisibleDescription,
            whyItMatters = "Moving continuously without long pauses or lock-offs tends to conserve energy and time on the wall.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.filter { it.type == ClimbEventType.HIGH_STEP }.forEach { e ->
        strengths += StrengthItem(
            title = "Stable high-step execution",
            evidence = e.userVisibleDescription,
            whyItMatters = "Settling a foot high and still before weighting it suggests a confident, controlled placement rather than a rushed one.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.filter { it.type == ClimbEventType.LEG_DRIVE_CANDIDATE }.forEach { e ->
        strengths += StrengthItem(
            title = "Leg drive into a dynamic move",
            evidence = e.userVisibleDescription,
            whyItMatters = "Generating movement from the legs rather than pulling with the arms alone is generally more efficient and sustainable.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.filter { it.type == ClimbEventType.FINISH_STABILIZATION }.forEach { e ->
        strengths += StrengthItem(
            title = "Controlled finish",
            evidence = e.userVisibleDescription,
            whyItMatters = "Stabilizing at the top rather than scrambling suggests good control and body tension at the end of the attempt.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    if (metrics.footStabilityScore >= 70) {
        strengths += StrengthItem(
            title = "Stable foot placements",
            evidence = "Foot stability score of ${metrics.footStabilityScore}/100 across settled placements",
            whyItMatters = "A foot that stays still once placed is generally more secure and needs less regripping or readjustment.",
            startTimestampMs = null,
            endTimestampMs = null,
            confidence = reliabilityConfidence,
            supportingEventIds = emptyList(),
            relatedLandmarks = FOOT_LEG_LANDMARKS,
        )
    }
    if (metrics.straightArmPercentage >= 60f) {
        strengths += StrengthItem(
            title = "Efficient straight-arm positioning",
            evidence = "${metrics.straightArmPercentage.roundToInt()}% straight-arm time",
            whyItMatters = "Straighter arms rely more on the skeleton than the muscles to hang, which is generally more energy-efficient.",
            startTimestampMs = metrics.climbStartMs,
            endTimestampMs = metrics.climbEndMs,
            confidence = reliabilityConfidence,
            supportingEventIds = emptyList(),
            relatedLandmarks = ARM_LANDMARKS,
        )
    }

    return strengths.sortedByDescending { it.confidence }.take(5)
}

fun buildImprovements(metrics: ClimbMetrics, events: List<ClimbEvent>): List<ImprovementItem> {
    val reliabilityConfidence = (metrics.reliableFramePercentage / 100f).coerceIn(0f, 1f)
    val improvements = mutableListOf<ImprovementItem>()

    events.filter { it.type == ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT }.forEach { e ->
        improvements += ImprovementItem(
            issue = "Possible foot repositioning",
            measuredEvidence = e.userVisibleDescription,
            impact = "Repositioning a foot after weighting it can cost time and stability compared to committing to one placement.",
            recommendation = "Watch the hold until the foot fully settles before shifting weight onto it.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.filter { it.type == ClimbEventType.POSSIBLE_FOOT_SLIP }.forEach { e ->
        improvements += ImprovementItem(
            issue = "Possible foot slip",
            measuredEvidence = e.userVisibleDescription,
            impact = "A slipping foot forces an unplanned correction and can cost momentum or contribute to a fall.",
            recommendation = "Check foot-placement precision and weight-transfer speed around this moment in the video.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.filter { it.type == ClimbEventType.POSSIBLE_STABILITY_LOSS }.forEach { e ->
        improvements += ImprovementItem(
            issue = "Possible stability loss",
            measuredEvidence = e.userVisibleDescription,
            impact = "A sudden loss of control can force a costly correction, or contribute to a fall.",
            recommendation = "Review this moment for the triggering movement and whether a slower transition would help.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.filter { it.type == ClimbEventType.EXCESSIVE_BODY_REPOSITIONING }.forEach { e ->
        improvements += ImprovementItem(
            issue = "Repeated repositioning",
            measuredEvidence = e.userVisibleDescription,
            impact = "Frequent direction changes in a short window can indicate indecision about the next move, costing time and energy.",
            recommendation = "Plan the next two or three moves before entering this section, then commit.",
            startTimestampMs = e.startTimestampMs,
            endTimestampMs = e.endTimestampMs,
            confidence = e.confidence,
            supportingEventIds = listOf(e.id),
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    if (metrics.footStabilityScore in 1..39) {
        improvements += ImprovementItem(
            issue = "Foot jitter after placement",
            measuredEvidence = "Foot stability score of ${metrics.footStabilityScore}/100",
            impact = "A foot that keeps shifting after being placed may not be fully weighted or confidently placed.",
            recommendation = "Practice placing a foot once and keeping it completely still until the next move.",
            startTimestampMs = null,
            endTimestampMs = null,
            confidence = reliabilityConfidence,
            supportingEventIds = emptyList(),
            relatedLandmarks = FOOT_LEG_LANDMARKS,
        )
    }

    return improvements.sortedByDescending { it.confidence }.take(5)
}

/** Always hedged ("Consider testing...") — pose tracking can't confirm any alternative sequence
 * would actually be better, only that a pattern worth reconsidering was observed. */
fun buildBetaOpportunities(events: List<ClimbEvent>): List<BetaOpportunity> {
    val opportunities = mutableListOf<BetaOpportunity>()

    val footAdjustments = events.filter { it.type == ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT }
    if (footAdjustments.size >= 2) {
        val first = footAdjustments.first()
        opportunities += BetaOpportunity(
            timestampMs = first.startTimestampMs,
            observedIssue = "${footAdjustments.size} possible foot-adjustment events across the climb",
            evidence = first.userVisibleDescription,
            suggestedAlternative = "Consider testing a run where each foot is placed once, paused on briefly, and not moved again before the next hand move.",
            confidence = 0.4f,
            requiresRouteContext = false,
            relatedLandmarks = relatedLandmarksFor(first.type),
        )
    }
    events.filter { it.type == ClimbEventType.LONG_PAUSE }.maxByOrNull { it.endTimestampMs - it.startTimestampMs }?.let { e ->
        opportunities += BetaOpportunity(
            timestampMs = e.startTimestampMs,
            observedIssue = "Long pause around ${formatTimestampMs(e.startTimestampMs)}",
            evidence = e.userVisibleDescription,
            suggestedAlternative = "Consider testing previewing the next two or three moves from the ground so less time is needed to plan mid-climb here.",
            confidence = 0.35f,
            requiresRouteContext = false,
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.firstOrNull { it.type == ClimbEventType.EXCESSIVE_BODY_REPOSITIONING }?.let { e ->
        opportunities += BetaOpportunity(
            timestampMs = e.startTimestampMs,
            observedIssue = "Repeated direction changes around ${formatTimestampMs(e.startTimestampMs)}",
            evidence = e.userVisibleDescription,
            suggestedAlternative = "Consider testing committing to a single sequence through this section rather than adjusting position repeatedly.",
            confidence = 0.35f,
            requiresRouteContext = false,
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }
    events.firstOrNull { it.type == ClimbEventType.POSSIBLE_DISENGAGED_LEG }?.let { e ->
        opportunities += BetaOpportunity(
            timestampMs = e.startTimestampMs,
            observedIssue = "One leg extended and unweighted around ${formatTimestampMs(e.startTimestampMs)}",
            evidence = e.userVisibleDescription,
            suggestedAlternative = "Consider testing whether a nearby foothold, heel hook, or toe hook could engage that leg and share some load off the arms — unless this was already a deliberate flag.",
            confidence = 0.3f,
            requiresRouteContext = true,
            relatedLandmarks = relatedLandmarksFor(e.type),
        )
    }

    return opportunities.take(4)
}
