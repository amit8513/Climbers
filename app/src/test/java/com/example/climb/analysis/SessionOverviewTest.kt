package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun metrics(
    reliableFramePercentage: Float = 90f,
    hasFinishStabilization: Boolean = false,
    possibleFallCandidateCount: Int = 0,
    pauseCount: Int = 2,
) = ClimbMetrics(
    totalDurationMs = 20_000, activeMovementMs = 15_000, pauseTimeMs = 5_000, pauseCount = pauseCount, longestPauseMs = 3_000,
    leftLockoffMs = 0, rightLockoffMs = 0, totalLockoffMs = 0, longestLockoffMs = 0,
    possibleFootAdjustments = 0, possibleFootSlips = 0, possibleDisengagedLegSegments = 0,
    straightArmPercentage = 40f, estimatedMovementEfficiency = 50, reliableFramePercentage = reliableFramePercentage,
    climbStartMs = 0, climbEndMs = 20_000, highStepCount = 0, possibleStabilityLossCount = 0,
    possibleFallCandidateCount = possibleFallCandidateCount, hasFinishStabilization = hasFinishStabilization, possibleMissedReachCount = 0,
)

class SessionOverviewTest {

    @Test
    fun `flags low reliability as a quality warning`() {
        val overview = buildSessionOverview(metrics(reliableFramePercentage = 30f), emptyList(), emptyList())
        assertNotNull(overview.qualityWarning)
    }

    @Test
    fun `no quality warning when tracking was reliable`() {
        val overview = buildSessionOverview(metrics(reliableFramePercentage = 95f), emptyList(), emptyList())
        assertNull(overview.qualityWarning)
    }

    @Test
    fun `attempt result reflects a controlled finish`() {
        val overview = buildSessionOverview(metrics(hasFinishStabilization = true), emptyList(), emptyList())
        assertTrue(overview.attemptResult.contains("completed"))
    }

    @Test
    fun `next session focus always returns exactly three items when possible`() {
        val focus = buildNextSessionFocus(emptyList(), metrics(pauseCount = 3))
        assertEquals(3, focus.size)
    }

    @Test
    fun `next session focus never fabricates an issue that was not detected`() {
        val improvement = ImprovementItem(
            issue = "Possible foot slip", measuredEvidence = "evidence", impact = "impact", recommendation = "recommendation",
            startTimestampMs = 1000L, endTimestampMs = 1000L, confidence = 0.9f, supportingEventIds = listOf("e1"),
        )
        val focus = buildNextSessionFocus(listOf(improvement), metrics())
        assertTrue(focus.first().title == "Possible foot slip")
    }
}
