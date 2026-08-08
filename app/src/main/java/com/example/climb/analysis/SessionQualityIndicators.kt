package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.CategoryScore

data class QualityIndicator(val label: String, val positive: Boolean)

/**
 * Short, scannable positive/warning chips summarizing this analysis's own reliability and
 * standout signals — deterministic rules over data already computed, not a new report-generation
 * pass. Deliberately built from [ClimbMetrics]/[ClimbEvent]/[CategoryScore] (all already
 * persisted and loaded on the analysis screen) rather than the transient [com.example.climb.analysis.metrics.AnalysisComputation],
 * so this needs no new persistence or pipeline stage.
 */
fun buildQualityIndicators(metrics: ClimbMetrics, events: List<ClimbEvent>, categoryScores: List<CategoryScore>): List<QualityIndicator> {
    val indicators = mutableListOf<QualityIndicator>()

    if (metrics.reliableFramePercentage >= 80f) {
        indicators += QualityIndicator("Reliable pose tracking throughout (${metrics.reliableFramePercentage.toInt()}%)", positive = true)
    } else if (metrics.reliableFramePercentage < 50f) {
        indicators += QualityIndicator("Low tracking confidence (${metrics.reliableFramePercentage.toInt()}%) — results may be less reliable", positive = false)
    }

    if (metrics.footStabilityScore >= 70) {
        indicators += QualityIndicator("Stable foot placements once settled", positive = true)
    } else if (metrics.footStabilityScore in 1..39) {
        indicators += QualityIndicator("Noticeable foot jitter after placement", positive = false)
    }

    if (metrics.legDriveCandidateCount > 0) {
        indicators += QualityIndicator("Leg-drive detected on at least one dynamic move", positive = true)
    }

    if (metrics.hasFinishStabilization) {
        indicators += QualityIndicator("Controlled, stable finish", positive = true)
    }

    if (metrics.possibleStabilityLossCount == 0) {
        indicators += QualityIndicator("No possible stability losses detected", positive = true)
    } else {
        indicators += QualityIndicator("${metrics.possibleStabilityLossCount} possible stability loss${if (metrics.possibleStabilityLossCount == 1) "" else "es"} detected", positive = false)
    }

    if (metrics.possibleFootAdjustments >= 3) {
        indicators += QualityIndicator("${metrics.possibleFootAdjustments} possible foot adjustments", positive = false)
    }
    if (metrics.possibleFootSlips > 0) {
        indicators += QualityIndicator("${metrics.possibleFootSlips} possible foot slip${if (metrics.possibleFootSlips == 1) "" else "s"} detected", positive = false)
    }

    val lowConfidenceRangeCount = events.count { it.type == ClimbEventType.LOW_CONFIDENCE_RANGE }
    if (lowConfidenceRangeCount > 0) {
        indicators += QualityIndicator("$lowConfidenceRangeCount stretch${if (lowConfidenceRangeCount == 1) "" else "es"} of unreliable body tracking", positive = false)
    }

    val lowConfidenceCategoryCount = categoryScores.count { it.confidence < 0.4f }
    if (lowConfidenceCategoryCount > 0) {
        indicators += QualityIndicator("$lowConfidenceCategoryCount score${if (lowConfidenceCategoryCount == 1) "" else "s"} based on limited evidence — treat with caution", positive = false)
    }

    return indicators
}
