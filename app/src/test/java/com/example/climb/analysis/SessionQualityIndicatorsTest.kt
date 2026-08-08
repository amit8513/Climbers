package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.CategoryScore
import com.example.climb.analysis.scoring.PerformanceCategory
import org.junit.Assert.assertTrue
import org.junit.Test

private fun metrics(
    reliableFramePercentage: Float = 90f,
    footStabilityScore: Int = 0,
    legDriveCandidateCount: Int = 0,
    hasFinishStabilization: Boolean = false,
    possibleStabilityLossCount: Int = 0,
    possibleFootAdjustments: Int = 0,
    possibleFootSlips: Int = 0,
) = ClimbMetrics(
    totalDurationMs = 10_000, activeMovementMs = 8_000, pauseTimeMs = 2_000, pauseCount = 1, longestPauseMs = 2_000,
    leftLockoffMs = 0, rightLockoffMs = 0, totalLockoffMs = 0, longestLockoffMs = 0,
    possibleFootAdjustments = possibleFootAdjustments, possibleFootSlips = possibleFootSlips, possibleDisengagedLegSegments = 0,
    straightArmPercentage = 50f, estimatedMovementEfficiency = 50, reliableFramePercentage = reliableFramePercentage,
    climbStartMs = 0, climbEndMs = 10_000, highStepCount = 0, possibleStabilityLossCount = possibleStabilityLossCount,
    possibleFallCandidateCount = 0, hasFinishStabilization = hasFinishStabilization, possibleMissedReachCount = 0,
    legDriveCandidateCount = legDriveCandidateCount, footStabilityScore = footStabilityScore,
)

private fun categoryScore(confidence: Float) = CategoryScore(
    category = PerformanceCategory.TECHNIQUE, score = 50, confidence = confidence,
    contributingMetrics = emptyList(), positiveFactors = emptyList(), negativeFactors = emptyList(),
    unavailableFactors = emptyList(), explanation = "",
)

class SessionQualityIndicatorsTest {

    @Test
    fun `flags low tracking confidence as a warning`() {
        val indicators = buildQualityIndicators(metrics(reliableFramePercentage = 30f), emptyList(), listOf(categoryScore(0.8f)))
        assertTrue(indicators.any { !it.positive && it.label.contains("Low tracking confidence") })
    }

    @Test
    fun `flags stable feet as positive`() {
        val indicators = buildQualityIndicators(metrics(footStabilityScore = 85), emptyList(), listOf(categoryScore(0.8f)))
        assertTrue(indicators.any { it.positive && it.label.contains("Stable foot placements") })
    }

    @Test
    fun `flags leg drive as positive`() {
        val indicators = buildQualityIndicators(metrics(legDriveCandidateCount = 2), emptyList(), listOf(categoryScore(0.8f)))
        assertTrue(indicators.any { it.positive && it.label.contains("Leg-drive detected") })
    }

    @Test
    fun `flags low-confidence categories as a caution`() {
        val indicators = buildQualityIndicators(metrics(), emptyList(), listOf(categoryScore(0.1f)))
        assertTrue(indicators.any { !it.positive && it.label.contains("limited evidence") })
    }

    @Test
    fun `no stability losses is reported as positive`() {
        val indicators = buildQualityIndicators(metrics(possibleStabilityLossCount = 0), emptyList(), listOf(categoryScore(0.8f)))
        assertTrue(indicators.any { it.positive && it.label.contains("No possible stability losses") })
    }
}
