package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import kotlin.math.roundToInt

data class TechnicalObservation(val section: String, val observation: String, val evidence: String)

/**
 * Synthesizes the metrics already computed by [com.example.climb.analysis.metrics.computeAnalysis]
 * into short, evidence-linked observations grouped by body region/timing — no new detection here,
 * just deterministic phrasing of numbers that already exist. "Lower Body Technique" is the
 * section that used to have nothing dedicated to it at all before this rework.
 */
fun buildTechnicalPerformanceReport(metrics: ClimbMetrics): List<TechnicalObservation> {
    val observations = mutableListOf<TechnicalObservation>()

    observations += TechnicalObservation(
        section = "Upper Body Efficiency",
        observation = "Arms were relatively straight for ${metrics.straightArmPercentage.roundToInt()}% of the climb" +
            if (metrics.totalLockoffMs > 0) ", with ${"%.1f".format(metrics.totalLockoffMs / 1000f)}s spent in sustained bent-arm lock-off positions." else " and no sustained bent-arm lock-off detected.",
        evidence = "straightArmPercentage=${metrics.straightArmPercentage.roundToInt()}%, totalLockoffMs=${metrics.totalLockoffMs}",
    )

    observations += TechnicalObservation(
        section = "Lower Body Technique",
        observation = "Knees swept a ${metrics.kneeRangeOfMotionDegrees.roundToInt()}° range of motion" +
            (if (metrics.footStabilityScore > 0) ", feet stayed steady once placed (${metrics.footStabilityScore}/100 foot stability)" else ", not enough settled-foot data to score foot stability") +
            ", with ${metrics.possibleFootAdjustments} possible foot adjustment${if (metrics.possibleFootAdjustments == 1) "" else "s"}" +
            (if (metrics.possibleFootSlips > 0) " and ${metrics.possibleFootSlips} possible foot slip${if (metrics.possibleFootSlips == 1) "" else "s"}" else "") +
            (if (metrics.legDriveCandidateCount > 0) ". ${metrics.legDriveCandidateCount} possible leg-drive contribution${if (metrics.legDriveCandidateCount == 1) "" else "s"} to a dynamic move were also detected." else "."),
        evidence = "kneeRangeOfMotionDegrees=${metrics.kneeRangeOfMotionDegrees.roundToInt()}, footStabilityScore=${metrics.footStabilityScore}, " +
            "possibleFootAdjustments=${metrics.possibleFootAdjustments}, possibleFootSlips=${metrics.possibleFootSlips}, legDriveCandidateCount=${metrics.legDriveCandidateCount}",
    )

    observations += TechnicalObservation(
        section = "Body Positioning",
        observation = if (metrics.possibleDisengagedLegSegments > 0) {
            "${metrics.possibleDisengagedLegSegments} moment${if (metrics.possibleDisengagedLegSegments == 1) "" else "s"} with one leg extended and unweighted relative to the other — this can be a deliberate flag for balance, not necessarily a technique issue."
        } else {
            "No sustained one-leg-extended asymmetry detected."
        },
        evidence = "possibleDisengagedLegSegments=${metrics.possibleDisengagedLegSegments}",
    )

    val pauseRatioPercent = if (metrics.totalDurationMs > 0) (metrics.pauseTimeMs.toFloat() / metrics.totalDurationMs * 100).roundToInt() else 0
    observations += TechnicalObservation(
        section = "Movement Timing",
        observation = "Paused ${metrics.pauseCount} time${if (metrics.pauseCount == 1) "" else "s"} (${pauseRatioPercent}% of total time), " +
            "with the longest pause lasting ${"%.1f".format(metrics.longestPauseMs / 1000f)}s.",
        evidence = "pauseCount=${metrics.pauseCount}, pauseTimeMs=${metrics.pauseTimeMs}, longestPauseMs=${metrics.longestPauseMs}",
    )

    observations += TechnicalObservation(
        section = "Stability and Balance",
        observation = if (metrics.possibleStabilityLossCount == 0) {
            "No possible stability losses detected."
        } else {
            "${metrics.possibleStabilityLossCount} possible stability loss${if (metrics.possibleStabilityLossCount == 1) "" else "es"} detected"
        } + (if (metrics.footWeightAsymmetry > 0.85f) ", and foot movement was heavily concentrated in one foot (${(metrics.footWeightAsymmetry * 100).roundToInt()}% asymmetry)." else ".") +
            if (metrics.hasFinishStabilization) " The climb ended with a controlled, stable finish." else "",
        evidence = "possibleStabilityLossCount=${metrics.possibleStabilityLossCount}, footWeightAsymmetry=${"%.2f".format(metrics.footWeightAsymmetry)}, hasFinishStabilization=${metrics.hasFinishStabilization}",
    )

    observations += TechnicalObservation(
        section = "Dynamic Movement",
        observation = if (metrics.possibleFallCandidateCount > 0 || metrics.possibleMissedReachCount > 0) {
            "${metrics.possibleFallCandidateCount} possible fall candidate${if (metrics.possibleFallCandidateCount == 1) "" else "s"} and ${metrics.possibleMissedReachCount} possible missed-reach candidate${if (metrics.possibleMissedReachCount == 1) "" else "s"} detected — pose tracking can't confirm whether a hold was targeted or touched."
        } else {
            "No fall or missed-reach candidates detected."
        },
        evidence = "possibleFallCandidateCount=${metrics.possibleFallCandidateCount}, possibleMissedReachCount=${metrics.possibleMissedReachCount}",
    )

    val activeRatioPercent = if (metrics.totalDurationMs > 0) (metrics.activeMovementMs.toFloat() / metrics.totalDurationMs * 100).roundToInt() else 0
    observations += TechnicalObservation(
        section = "Pacing and Endurance",
        observation = "Spent ${activeRatioPercent}% of the attempt actively moving over ${"%.1f".format(metrics.totalDurationMs / 1000f)}s total" +
            if (metrics.totalDurationMs < 15_000L) " — a short attempt, so pacing-over-time comparisons carry lower confidence." else ".",
        evidence = "activeMovementMs=${metrics.activeMovementMs}, totalDurationMs=${metrics.totalDurationMs}",
    )

    return observations
}
