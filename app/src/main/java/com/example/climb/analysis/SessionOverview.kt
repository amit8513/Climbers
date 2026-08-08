package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics

data class SessionOverview(
    val summary: String,
    val attemptResult: String,
    val topStrength: String?,
    val topWeakness: String?,
    val qualityWarning: String?,
)

/** Pure synthesis of already-computed [ClimbMetrics] and the strengths/improvements built by
 * [buildStrengths]/[buildImprovements] — no new detection, and never claims a hold, wall angle,
 * or route style that wasn't detected or provided. */
fun buildSessionOverview(metrics: ClimbMetrics, strengths: List<StrengthItem>, improvements: List<ImprovementItem>): SessionOverview {
    val durationSeconds = metrics.totalDurationMs / 1000f
    val movementStyle = when {
        metrics.legDriveCandidateCount > 0 && metrics.totalLockoffMs > 2_000L -> "a mix of dynamic, leg-driven movement and sustained static holds"
        metrics.legDriveCandidateCount > 0 -> "mostly dynamic movement, with possible leg-drive contributions"
        metrics.totalLockoffMs > 2_000L -> "mostly static, controlled movement with sustained bent-arm positions"
        else -> "general climbing movement, without a strongly dynamic or strongly static pattern"
    }
    val attemptResult = when {
        metrics.hasFinishStabilization -> "Likely completed, with a controlled, stable finish"
        metrics.possibleFallCandidateCount > 0 -> "A possible fall or incomplete attempt was detected near the end"
        else -> "Attempt result isn't clearly determined from pose tracking alone"
    }
    return SessionOverview(
        summary = "A ${"%.0f".format(durationSeconds)}s attempt with $movementStyle.",
        attemptResult = attemptResult,
        topStrength = strengths.maxByOrNull { it.confidence }?.title,
        topWeakness = improvements.maxByOrNull { it.confidence }?.issue,
        qualityWarning = if (metrics.reliableFramePercentage < 60f) {
            "Pose tracking was unreliable for part of this video (${metrics.reliableFramePercentage.toInt()}% reliable frames) — treat this analysis with extra caution."
        } else {
            null
        },
    )
}

data class NextSessionFocusItem(val title: String, val action: String, val evidence: String, val successCriterion: String)

/**
 * Always exactly 3 items: the highest-confidence detected improvements first, topped up with
 * generic focus points that are always true (never a fabricated specific issue) whenever there
 * aren't 3 specific issues to fill every slot.
 */
fun buildNextSessionFocus(improvements: List<ImprovementItem>, metrics: ClimbMetrics): List<NextSessionFocusItem> {
    val fromIssues = improvements.sortedByDescending { it.confidence }.take(3).map {
        NextSessionFocusItem(
            title = it.issue,
            action = it.recommendation,
            evidence = it.measuredEvidence,
            successCriterion = "Complete a similar section with this addressed, then compare against this attempt's numbers.",
        )
    }
    if (fromIssues.size >= 3) return fromIssues

    val genericFallbacks = listOf(
        NextSessionFocusItem(
            title = "Watch pacing and rest points",
            action = "Preview upcoming moves before committing, to keep pausing deliberate rather than reactive.",
            evidence = "This attempt lasted ${"%.1f".format(metrics.totalDurationMs / 1000f)}s with ${metrics.pauseCount} pause(s)",
            successCriterion = "Compare total pause time against this attempt on a similar climb.",
        ),
        NextSessionFocusItem(
            title = "Review footwork specifically",
            action = "Rewatch this video focused only on foot placement, timing, and leg drive.",
            evidence = "Foot placement and leg-drive timing are now tracked in this analysis",
            successCriterion = "Note one concrete foot-placement observation before the next attempt.",
        ),
        NextSessionFocusItem(
            title = "Build a comparison baseline",
            action = "Record another attempt on the same or a similar route.",
            evidence = "A single attempt can't show a trend over time",
            successCriterion = "A second analysis exists to compare category scores against.",
        ),
    )
    return (fromIssues + genericFallbacks).take(3)
}
