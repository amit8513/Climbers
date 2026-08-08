package com.example.climb.analysis

import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLandmarksTest {

    @Test
    fun `foot-related events map to foot and knee landmarks`() {
        val landmarks = relatedLandmarksFor(ClimbEventType.POSSIBLE_FOOT_SLIP)
        assertTrue(landmarks.contains(PoseLandmarkType.LEFT_FOOT_INDEX))
        assertTrue(landmarks.contains(PoseLandmarkType.RIGHT_FOOT_INDEX))
        assertTrue(landmarks.contains(PoseLandmarkType.LEFT_KNEE))
    }

    @Test
    fun `lock-off events map to arm landmarks`() {
        val landmarks = relatedLandmarksFor(ClimbEventType.SUSTAINED_LOCKOFF)
        assertTrue(landmarks.contains(PoseLandmarkType.LEFT_ELBOW))
        assertTrue(landmarks.contains(PoseLandmarkType.RIGHT_WRIST))
    }

    @Test
    fun `every event type has a defined (possibly empty) mapping without crashing`() {
        for (type in ClimbEventType.entries) {
            relatedLandmarksFor(type)
        }
    }

    @Test
    fun `strengths carry the related landmarks for their source event`() {
        val event = ClimbEvent(
            id = "hs1", type = ClimbEventType.HIGH_STEP, startTimestampMs = 1000L, endTimestampMs = 1000L,
            peakTimestampMs = 1000L, confidence = 0.6f, severity = 1, userVisibleTitle = "t", userVisibleDescription = "d",
        )
        val metrics = com.example.climb.analysis.metrics.ClimbMetrics(
            totalDurationMs = 10_000, activeMovementMs = 8_000, pauseTimeMs = 2_000, pauseCount = 1, longestPauseMs = 2_000,
            leftLockoffMs = 0, rightLockoffMs = 0, totalLockoffMs = 0, longestLockoffMs = 0,
            possibleFootAdjustments = 0, possibleFootSlips = 0, possibleDisengagedLegSegments = 0,
            straightArmPercentage = 0f, estimatedMovementEfficiency = 50, reliableFramePercentage = 90f,
            climbStartMs = 0, climbEndMs = 10_000, highStepCount = 1, possibleStabilityLossCount = 0,
            possibleFallCandidateCount = 0, hasFinishStabilization = false, possibleMissedReachCount = 0,
        )
        val strengths = buildStrengths(metrics, listOf(event))
        val highStepStrength = strengths.first { it.title.contains("high-step") }
        assertTrue(highStepStrength.relatedLandmarks.contains(PoseLandmarkType.LEFT_FOOT_INDEX))
    }
}
