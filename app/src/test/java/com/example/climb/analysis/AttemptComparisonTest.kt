package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun metrics(
    totalDurationMs: Long = 20_000,
    possibleFootAdjustments: Int = 2,
    footStabilityScore: Int = 60,
    possibleStabilityLossCount: Int = 1,
    kneeRangeOfMotionDegrees: Float = 70f,
) = ClimbMetrics(
    totalDurationMs = totalDurationMs, activeMovementMs = 15_000, pauseTimeMs = 5_000, pauseCount = 2, longestPauseMs = 3_000,
    leftLockoffMs = 0, rightLockoffMs = 0, totalLockoffMs = 0, longestLockoffMs = 0,
    possibleFootAdjustments = possibleFootAdjustments, possibleFootSlips = 0, possibleDisengagedLegSegments = 0,
    straightArmPercentage = 50f, estimatedMovementEfficiency = 50, reliableFramePercentage = 90f,
    climbStartMs = 0, climbEndMs = totalDurationMs, highStepCount = 0, possibleStabilityLossCount = possibleStabilityLossCount,
    possibleFallCandidateCount = 0, hasFinishStabilization = false, possibleMissedReachCount = 0,
    footStabilityScore = footStabilityScore, kneeRangeOfMotionDegrees = kneeRangeOfMotionDegrees,
)

class AttemptComparisonTest {

    @Test
    fun `identical attempts produce no comparison lines`() {
        val lines = buildAttemptComparison(metrics(), 70, metrics(), 70)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `fewer foot adjustments is reported as an improvement`() {
        val lines = buildAttemptComparison(metrics(possibleFootAdjustments = 4), 70, metrics(possibleFootAdjustments = 1), 70)
        val line = lines.first { it.label.contains("foot adjustments") }
        assertTrue(line.label.contains("3 fewer"))
        assertEquals(true, line.improved)
    }

    @Test
    fun `more foot adjustments is reported as a regression, not hidden`() {
        val lines = buildAttemptComparison(metrics(possibleFootAdjustments = 1), 70, metrics(possibleFootAdjustments = 4), 70)
        val line = lines.first { it.label.contains("foot adjustments") }
        assertTrue(line.label.contains("3 more"))
        assertEquals(false, line.improved)
    }

    @Test
    fun `overall score change is reported with both values`() {
        val lines = buildAttemptComparison(metrics(), 61, metrics(), 72)
        val line = lines.first { it.label.contains("Overall score") }
        assertTrue(line.label.contains("61"))
        assertTrue(line.label.contains("72"))
        assertEquals(true, line.improved)
    }

    @Test
    fun `duration change is neutral, not labeled as improvement or regression`() {
        val lines = buildAttemptComparison(metrics(totalDurationMs = 20_000), 70, metrics(totalDurationMs = 12_000), 70)
        val line = lines.first { it.label.contains("duration") }
        assertEquals(null, line.improved)
    }
}
