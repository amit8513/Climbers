package com.example.climb.analysis

import com.example.climb.analysis.metrics.AnalysisComputation
import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.metrics.FallCandidateEvent
import com.example.climb.analysis.metrics.FinishStabilizationEvent
import com.example.climb.analysis.metrics.MetricsConfiguration
import com.example.climb.analysis.metrics.PauseSegment
import com.example.climb.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val CONFIG = MetricsConfiguration()

/** Only [PoseFrame.timestampMs] of the first frame is ever read by [detectPhases] (as the video's
 * start), so a single dummy frame is enough to drive every scenario here. */
private fun dummyFrames(videoStartMs: Long) = listOf(PoseFrame(videoStartMs, emptyList(), 1f, true, null))

private fun metrics(climbStartMs: Long, climbEndMs: Long) = ClimbMetrics(
    totalDurationMs = climbEndMs - climbStartMs,
    activeMovementMs = climbEndMs - climbStartMs,
    pauseTimeMs = 0,
    pauseCount = 0,
    longestPauseMs = 0,
    leftLockoffMs = 0,
    rightLockoffMs = 0,
    totalLockoffMs = 0,
    longestLockoffMs = 0,
    possibleFootAdjustments = 0,
    possibleFootSlips = 0,
    possibleDisengagedLegSegments = 0,
    straightArmPercentage = 0f,
    estimatedMovementEfficiency = 0,
    reliableFramePercentage = 100f,
    climbStartMs = climbStartMs,
    climbEndMs = climbEndMs,
    highStepCount = 0,
    possibleStabilityLossCount = 0,
    possibleFallCandidateCount = 0,
    hasFinishStabilization = false,
    possibleMissedReachCount = 0,
)

private fun computation(
    climbStartMs: Long,
    climbEndMs: Long,
    pauses: List<PauseSegment> = emptyList(),
    fallCandidates: List<FallCandidateEvent> = emptyList(),
    finishStabilization: FinishStabilizationEvent? = null,
) = AnalysisComputation(
    metrics = metrics(climbStartMs, climbEndMs).copy(
        hasFinishStabilization = finishStabilization != null,
        possibleFallCandidateCount = fallCandidates.size,
    ),
    pauses = pauses,
    lockoffs = emptyList(),
    footAdjustments = emptyList(),
    footSlips = emptyList(),
    disengagedLegs = emptyList(),
    highSteps = emptyList(),
    stabilityLossEvents = emptyList(),
    recoveries = emptyList(),
    fallCandidates = fallCandidates,
    finishStabilization = finishStabilization,
    missedReachCandidates = emptyList(),
)

private fun event(
    type: ClimbEventType,
    startMs: Long,
    endMs: Long,
    confidence: Float = 0.6f,
) = ClimbEvent(
    id = "${type.name}_$startMs",
    type = type,
    startTimestampMs = startMs,
    endTimestampMs = endMs,
    peakTimestampMs = startMs,
    confidence = confidence,
    severity = 2,
    userVisibleTitle = type.name,
    userVisibleDescription = type.name,
)

class ClimbPhaseDetectorTest {

    @Test
    fun `empty frames produce no phases`() {
        assertTrue(detectPhases(emptyList(), computation(0, 0), emptyList(), CONFIG).isEmpty())
    }

    @Test
    fun `a plain climb with no events is entirely active climbing`() {
        val phases = detectPhases(dummyFrames(0), computation(0, 5000), emptyList(), CONFIG)
        assertEquals(1, phases.size)
        assertEquals(ClimbPhaseType.ACTIVE_CLIMBING, phases.first().type)
        assertEquals(0L, phases.first().startMs)
        assertEquals(5000L, phases.first().endMs)
    }

    @Test
    fun `a real gap before the climb starts becomes a preparation phase`() {
        val phases = detectPhases(dummyFrames(0), computation(2000, 6000), emptyList(), CONFIG)
        val preparation = phases.first()
        assertEquals(ClimbPhaseType.PREPARATION, preparation.type)
        assertEquals(0L, preparation.startMs)
        assertEquals(2000L, preparation.endMs)
    }

    @Test
    fun `no preparation phase when the climb starts right at the video's start`() {
        val phases = detectPhases(dummyFrames(0), computation(100, 5000), emptyList(), CONFIG)
        assertTrue(phases.none { it.type == ClimbPhaseType.PREPARATION })
    }

    @Test
    fun `a long pause becomes a rest phase, a short one a static position`() {
        val pauses = listOf(PauseSegment(1000, 5200), PauseSegment(6000, 7000)) // 4200ms, 1000ms
        val phases = detectPhases(dummyFrames(0), computation(0, 8000, pauses = pauses), emptyList(), CONFIG)
        val rest = phases.first { it.startMs == 1000L }
        val static = phases.first { it.startMs == 6000L }
        assertEquals(ClimbPhaseType.REST, rest.type)
        assertEquals(ClimbPhaseType.STATIC_POSITION, static.type)
    }

    @Test
    fun `a possible fall event produces a fall phase that overrides an overlapping pause`() {
        val pauses = listOf(PauseSegment(2000, 3000))
        val events = listOf(event(ClimbEventType.POSSIBLE_FALL, 2200, 2800))
        val phases = detectPhases(dummyFrames(0), computation(0, 5000, pauses = pauses), events, CONFIG)
        val fallPhase = phases.first { it.type == ClimbPhaseType.FALL }
        assertEquals(2200L, fallPhase.startMs)
        assertEquals(2800L, fallPhase.endMs)
        // The pause's range is claimed by the higher-priority fall event, not double-booked.
        assertTrue(phases.none { it.type == ClimbPhaseType.STATIC_POSITION || it.type == ClimbPhaseType.REST })
    }

    @Test
    fun `a controlled finish produces a finish phase at the climb's end`() {
        val finish = FinishStabilizationEvent(9000, 9800)
        val phases = detectPhases(dummyFrames(0), computation(0, 10_000, finishStabilization = finish), emptyList(), CONFIG)
        val finishPhase = phases.first { it.type == ClimbPhaseType.FINISH }
        assertEquals(9000L, finishPhase.startMs)
        assertEquals(9800L, finishPhase.endMs)
    }

    @Test
    fun `no finish and a fall right at the end produces incomplete, never assuming success`() {
        val fall = FallCandidateEvent(9200, 2.0f)
        val events = listOf(event(ClimbEventType.POSSIBLE_FALL, 9200, 9500))
        val phases = detectPhases(dummyFrames(0), computation(0, 10_000, fallCandidates = listOf(fall)), events, CONFIG)
        assertTrue(phases.any { it.type == ClimbPhaseType.INCOMPLETE })
        assertTrue(phases.none { it.type == ClimbPhaseType.FINISH })
    }

    @Test
    fun `a fall well before the end does not produce incomplete`() {
        val fall = FallCandidateEvent(2000, 2.0f)
        val events = listOf(event(ClimbEventType.POSSIBLE_FALL, 2000, 2300))
        val phases = detectPhases(dummyFrames(0), computation(0, 10_000, fallCandidates = listOf(fall)), events, CONFIG)
        assertTrue(phases.none { it.type == ClimbPhaseType.INCOMPLETE })
    }

    @Test
    fun `phases fully and contiguously cover the climb window with no gaps or overlaps`() {
        val pauses = listOf(PauseSegment(1000, 2600))
        val events = listOf(event(ClimbEventType.LARGE_DYNAMIC_MOVE, 5000, 5100))
        val phases = detectPhases(dummyFrames(0), computation(0, 8000, pauses = pauses), events, CONFIG)
            .filter { it.startMs >= 0 && it.endMs <= 8000 }
            .sortedBy { it.startMs }

        assertEquals(0L, phases.first().startMs)
        assertEquals(8000L, phases.last().endMs)
        for (i in 1 until phases.size) {
            assertEquals("expected contiguous phases with no gap or overlap", phases[i - 1].endMs, phases[i].startMs)
        }
    }
}
