package com.example.climb.analysis

import com.example.climb.analysis.metrics.ClimbMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sampleMetrics() = ClimbMetrics(
    totalDurationMs = 30_000, activeMovementMs = 22_000, pauseTimeMs = 8_000, pauseCount = 3, longestPauseMs = 4_000,
    leftLockoffMs = 1_000, rightLockoffMs = 500, totalLockoffMs = 1_500, longestLockoffMs = 1_000,
    possibleFootAdjustments = 2, possibleFootSlips = 1, possibleDisengagedLegSegments = 1,
    straightArmPercentage = 62f, estimatedMovementEfficiency = 55, reliableFramePercentage = 85f,
    climbStartMs = 0, climbEndMs = 30_000, highStepCount = 1, possibleStabilityLossCount = 1,
    possibleFallCandidateCount = 0, hasFinishStabilization = true, possibleMissedReachCount = 0,
    legDriveCandidateCount = 1, kneeRangeOfMotionDegrees = 75f, footStabilityScore = 80,
    totalFootTravelNormalized = 1.2f, footWeightAsymmetry = 0.3f,
)

class TechnicalPerformanceReportTest {

    @Test
    fun `covers all seven technical sections`() {
        val report = buildTechnicalPerformanceReport(sampleMetrics())
        val sections = report.map { it.section }
        assertEquals(7, report.size)
        assertTrue(sections.containsAll(listOf(
            "Upper Body Efficiency", "Lower Body Technique", "Body Positioning",
            "Movement Timing", "Stability and Balance", "Dynamic Movement", "Pacing and Endurance",
        )))
    }

    @Test
    fun `lower body section cites the new footwork metrics as evidence`() {
        val lowerBody = buildTechnicalPerformanceReport(sampleMetrics()).first { it.section == "Lower Body Technique" }
        assertTrue(lowerBody.evidence.contains("kneeRangeOfMotionDegrees"))
        assertTrue(lowerBody.evidence.contains("footStabilityScore"))
        assertTrue(lowerBody.evidence.contains("legDriveCandidateCount"))
    }

    @Test
    fun `every observation carries non-blank evidence`() {
        val report = buildTechnicalPerformanceReport(sampleMetrics())
        assertTrue(report.all { it.evidence.isNotBlank() && it.observation.isNotBlank() })
    }
}
