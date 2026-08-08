package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import org.junit.Assert.assertTrue
import org.junit.Test

private fun event(type: ClimbEventType, start: Long = 1_000L, end: Long = 1_000L, confidence: Float = 0.5f, id: String = type.name) = ClimbEvent(
    id = id, type = type, startTimestampMs = start, endTimestampMs = end, peakTimestampMs = start,
    confidence = confidence, severity = 1, userVisibleTitle = type.name, userVisibleDescription = "desc for $id",
)

private fun metrics(footStabilityScore: Int = 0, straightArmPercentage: Float = 0f) = ClimbMetrics(
    totalDurationMs = 20_000, activeMovementMs = 15_000, pauseTimeMs = 5_000, pauseCount = 2, longestPauseMs = 3_000,
    leftLockoffMs = 0, rightLockoffMs = 0, totalLockoffMs = 0, longestLockoffMs = 0,
    possibleFootAdjustments = 0, possibleFootSlips = 0, possibleDisengagedLegSegments = 0,
    straightArmPercentage = straightArmPercentage, estimatedMovementEfficiency = 50, reliableFramePercentage = 90f,
    climbStartMs = 0, climbEndMs = 20_000, highStepCount = 0, possibleStabilityLossCount = 0,
    possibleFallCandidateCount = 0, hasFinishStabilization = false, possibleMissedReachCount = 0,
    footStabilityScore = footStabilityScore,
)

class StrengthsWeaknessesBetaTest {

    @Test
    fun `high step event becomes a timestamped strength`() {
        val strengths = buildStrengths(metrics(), listOf(event(ClimbEventType.HIGH_STEP, start = 5_000L)))
        val highStep = strengths.first { it.title.contains("high-step") }
        assertTrue(highStep.startTimestampMs == 5_000L)
        assertTrue(highStep.supportingEventIds.isNotEmpty())
    }

    @Test
    fun `leg drive event becomes a strength`() {
        val strengths = buildStrengths(metrics(), listOf(event(ClimbEventType.LEG_DRIVE_CANDIDATE)))
        assertTrue(strengths.any { it.title.contains("Leg drive") })
    }

    @Test
    fun `low foot stability score becomes an improvement item`() {
        val improvements = buildImprovements(metrics(footStabilityScore = 20), emptyList())
        assertTrue(improvements.any { it.issue.contains("Foot jitter") })
    }

    @Test
    fun `high foot stability score does not become an improvement item`() {
        val improvements = buildImprovements(metrics(footStabilityScore = 90), emptyList())
        assertTrue(improvements.none { it.issue.contains("Foot jitter") })
    }

    @Test
    fun `repeated foot adjustments produce a beta opportunity`() {
        val opportunities = buildBetaOpportunities(
            listOf(
                event(ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT, id = "a"),
                event(ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT, id = "b"),
            ),
        )
        assertTrue(opportunities.any { it.observedIssue.contains("foot-adjustment") })
        assertTrue(opportunities.all { it.suggestedAlternative.startsWith("Consider testing") })
    }

    @Test
    fun `disengaged leg beta opportunity is marked as requiring route context`() {
        val opportunities = buildBetaOpportunities(listOf(event(ClimbEventType.POSSIBLE_DISENGAGED_LEG)))
        val opportunity = opportunities.first { it.observedIssue.contains("extended and unweighted") }
        assertTrue(opportunity.requiresRouteContext)
    }
}
