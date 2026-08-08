package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import kotlin.math.abs

/** [improved] is null when a change is neither clearly better nor worse on its own (e.g. a
 * faster or slower attempt isn't inherently an improvement without knowing whether it was sent). */
data class ComparisonLine(val label: String, val improved: Boolean?)

/**
 * Compares two completed attempts on the same route — always both directions (a regression is
 * reported exactly like an improvement, not hidden), and only emits a line when the underlying
 * metric actually changed by a meaningful amount, so an unchanged metric doesn't manufacture
 * noise.
 */
fun buildAttemptComparison(previous: ClimbMetrics, previousScore: Int?, current: ClimbMetrics, currentScore: Int?): List<ComparisonLine> {
    val lines = mutableListOf<ComparisonLine>()

    if (previousScore != null && currentScore != null && previousScore != currentScore) {
        val diff = currentScore - previousScore
        lines += ComparisonLine(
            label = "Overall score ${if (diff > 0) "improved" else "dropped"} from $previousScore to $currentScore",
            improved = diff > 0,
        )
    }

    val durationDiffSeconds = (current.totalDurationMs - previous.totalDurationMs) / 1000f
    if (abs(durationDiffSeconds) >= 1f) {
        lines += ComparisonLine(
            label = "Attempt duration ${if (durationDiffSeconds < 0) "decreased" else "increased"} by ${"%.1f".format(abs(durationDiffSeconds))}s",
            improved = null,
        )
    }

    val footAdjustmentDiff = previous.possibleFootAdjustments - current.possibleFootAdjustments
    if (footAdjustmentDiff != 0) {
        lines += ComparisonLine(
            label = "${abs(footAdjustmentDiff)} ${if (footAdjustmentDiff > 0) "fewer" else "more"} possible foot adjustments",
            improved = footAdjustmentDiff > 0,
        )
    }

    if (previous.footStabilityScore > 0 && current.footStabilityScore > 0 && previous.footStabilityScore != current.footStabilityScore) {
        lines += ComparisonLine(
            label = "Foot stability ${if (current.footStabilityScore > previous.footStabilityScore) "improved" else "dropped"} from ${previous.footStabilityScore} to ${current.footStabilityScore}",
            improved = current.footStabilityScore > previous.footStabilityScore,
        )
    }

    val stabilityLossDiff = previous.possibleStabilityLossCount - current.possibleStabilityLossCount
    if (stabilityLossDiff != 0) {
        lines += ComparisonLine(
            label = "${abs(stabilityLossDiff)} ${if (stabilityLossDiff > 0) "fewer" else "more"} possible stability losses",
            improved = stabilityLossDiff > 0,
        )
    }

    val kneeRomDiff = current.kneeRangeOfMotionDegrees - previous.kneeRangeOfMotionDegrees
    if (abs(kneeRomDiff) >= 5f) {
        lines += ComparisonLine(
            label = "Knee range of motion ${if (kneeRomDiff > 0) "increased" else "decreased"} by ${abs(kneeRomDiff).toInt()}°",
            improved = null,
        )
    }

    return lines.take(5)
}
