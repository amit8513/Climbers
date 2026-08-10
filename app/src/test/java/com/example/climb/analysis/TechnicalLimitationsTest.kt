package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.CategoryScore
import com.example.climb.analysis.scoring.PerformanceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun metrics(
    reliableFramePercentage: Float = 90f,
    totalDurationMs: Long = 30_000,
) = ClimbMetrics(
    totalDurationMs = totalDurationMs, activeMovementMs = totalDurationMs - 2_000, pauseTimeMs = 2_000, pauseCount = 1, longestPauseMs = 2_000,
    leftLockoffMs = 0, rightLockoffMs = 0, totalLockoffMs = 0, longestLockoffMs = 0,
    possibleFootAdjustments = 0, possibleFootSlips = 0, possibleDisengagedLegSegments = 0,
    straightArmPercentage = 50f, estimatedMovementEfficiency = 50, reliableFramePercentage = reliableFramePercentage,
    climbStartMs = 0, climbEndMs = totalDurationMs, highStepCount = 0, possibleStabilityLossCount = 0,
    possibleFallCandidateCount = 0, hasFinishStabilization = false, possibleMissedReachCount = 0,
)

private fun categoryScore(category: PerformanceCategory, confidence: Float) = CategoryScore(
    category = category, score = 50, confidence = confidence,
    contributingMetrics = emptyList(), positiveFactors = emptyList(), negativeFactors = emptyList(),
    unavailableFactors = emptyList(), explanation = "",
)

private val FULL_CONFIDENCE_SCORES = PerformanceCategory.entries.map { categoryScore(it, 0.9f) }

class TechnicalLimitationsTest {

    @Test
    fun `flags low tracking reliability as a session-specific limitation`() {
        val limitations = buildTechnicalLimitations(metrics(reliableFramePercentage = 40f), FULL_CONFIDENCE_SCORES)
        val warning = limitations.first { it.text.contains("reliable for only") }
        assertTrue(warning.evidence != null)
    }

    @Test
    fun `does not flag tracking reliability when it is high`() {
        val limitations = buildTechnicalLimitations(metrics(reliableFramePercentage = 95f), FULL_CONFIDENCE_SCORES)
        assertTrue(limitations.none { it.text.contains("reliable for only") })
    }

    @Test
    fun `flags a short attempt as a session-specific limitation`() {
        val limitations = buildTechnicalLimitations(metrics(totalDurationMs = 8_000), FULL_CONFIDENCE_SCORES)
        assertTrue(limitations.any { it.text.contains("under 15 seconds") })
    }

    @Test
    fun `does not flag duration for a long attempt`() {
        val limitations = buildTechnicalLimitations(metrics(totalDurationMs = 60_000), FULL_CONFIDENCE_SCORES)
        assertTrue(limitations.none { it.text.contains("under 15 seconds") })
    }

    @Test
    fun `names the specific low-confidence categories for this attempt`() {
        val scores = listOf(categoryScore(PerformanceCategory.STRATEGY, 0.2f), categoryScore(PerformanceCategory.TECHNIQUE, 0.9f))
        val limitations = buildTechnicalLimitations(metrics(), scores)
        val warning = limitations.first { it.text.contains("limited evidence") }
        assertTrue(warning.text.contains("Strategy"))
        assertTrue(!warning.text.contains("Technique"))
    }

    @Test
    fun `always includes the evergreen pipeline limitations regardless of tracking quality`() {
        val limitations = buildTechnicalLimitations(metrics(reliableFramePercentage = 100f), FULL_CONFIDENCE_SCORES)
        assertTrue(limitations.any { it.evidence == null && it.text.contains("hold", ignoreCase = true) })
        assertTrue(limitations.any { it.evidence == null && it.text.contains("depth", ignoreCase = true) })
    }

    @Test
    fun `a clean, well-tracked, long attempt still surfaces only the evergreen limitations`() {
        val limitations = buildTechnicalLimitations(metrics(reliableFramePercentage = 100f, totalDurationMs = 60_000), FULL_CONFIDENCE_SCORES)
        assertTrue(limitations.all { it.evidence == null })
        assertEquals(5, limitations.size)
    }
}
