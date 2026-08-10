package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lm(type: PoseLandmarkType, xy: Pair<Float, Float>) = PoseLandmark(type, xy.first, xy.second, 0f, 1f, 1f)

/** Same standing default pose as FootworkMetricsTest: shoulders/hips/ankles roughly stacked,
 * body-height estimate (shoulder-center to ankle-center) is 0.7 normalized units. */
private val DEFAULT_LANDMARKS: Map<PoseLandmarkType, Pair<Float, Float>> = mapOf(
    PoseLandmarkType.LEFT_SHOULDER to (0.4f to 0.2f),
    PoseLandmarkType.RIGHT_SHOULDER to (0.6f to 0.2f),
    PoseLandmarkType.LEFT_WRIST to (0.4f to 0.5f),
    PoseLandmarkType.RIGHT_WRIST to (0.6f to 0.5f),
    PoseLandmarkType.LEFT_HIP to (0.45f to 0.5f),
    PoseLandmarkType.RIGHT_HIP to (0.55f to 0.5f),
    PoseLandmarkType.LEFT_KNEE to (0.45f to 0.7f),
    PoseLandmarkType.RIGHT_KNEE to (0.55f to 0.7f),
    PoseLandmarkType.LEFT_ANKLE to (0.45f to 0.9f),
    PoseLandmarkType.RIGHT_ANKLE to (0.55f to 0.9f),
    PoseLandmarkType.LEFT_FOOT_INDEX to (0.45f to 0.92f),
    PoseLandmarkType.RIGHT_FOOT_INDEX to (0.55f to 0.92f),
)

private fun frame(timestampMs: Long, overrides: Map<PoseLandmarkType, Pair<Float, Float>> = emptyMap()): PoseFrame {
    val merged = DEFAULT_LANDMARKS + overrides
    return PoseFrame(timestampMs, merged.map { (type, xy) -> lm(type, xy) }, averageConfidence = 1f, isReliable = true, bodyBoundingBox = null)
}

private val CONFIG = MetricsConfiguration()

class ExpandedMovementDetectorTest {

    // -- detectHighSteps ----------------------------------------------------------------------

    @Test
    fun `high step fires for a foot that settles well above hip height`() {
        val footAboveHips = PoseLandmarkType.LEFT_FOOT_INDEX to (0.45f to 0.3f)
        val frames = (0..3).map { i -> frame(i * 100L, mapOf(footAboveHips)) }
        val events = detectHighSteps(frames, CONFIG)
        assertEquals(1, events.size)
        assertEquals(Side.LEFT, events.first().side)
        assertTrue(events.first().hipRelativeHeight >= CONFIG.highStepHipRatio)
    }

    @Test
    fun `high step does not fire for feet at the default low stance`() {
        val frames = (0..3).map { i -> frame(i * 100L) }
        assertTrue(detectHighSteps(frames, CONFIG).isEmpty())
    }

    // -- detectStabilityLoss --------------------------------------------------------------------

    @Test
    fun `stability loss fires on a sudden frame-to-frame velocity jump`() {
        val velocities = listOf(
            TimedVelocity(0, 0f),
            TimedVelocity(100, 0.1f),
            TimedVelocity(200, 1.0f), // jump of 0.9, above the 0.8 threshold
        )
        val events = detectStabilityLoss(velocities, CONFIG)
        assertEquals(1, events.size)
        assertEquals(200L, events.first().timestampMs)
    }

    @Test
    fun `stability loss does not fire on a smooth acceleration`() {
        val velocities = listOf(
            TimedVelocity(0, 0f),
            TimedVelocity(100, 0.3f),
            TimedVelocity(200, 0.6f),
            TimedVelocity(300, 0.9f),
        )
        assertTrue(detectStabilityLoss(velocities, CONFIG).isEmpty())
    }

    // -- detectRecoveries -------------------------------------------------------------------------

    @Test
    fun `recovery fires once velocity settles below the still threshold for long enough`() {
        val loss = StabilityLossEvent(200, 0.9f)
        val velocities = mutableListOf(TimedVelocity(0, 0f), TimedVelocity(100, 0.1f), TimedVelocity(200, 1.0f))
        for (t in 300..1100 step 100) velocities += TimedVelocity(t.toLong(), 0.05f)
        val recoveries = detectRecoveries(velocities, listOf(loss), CONFIG)
        assertEquals(1, recoveries.size)
        assertEquals(200L, recoveries.first().stabilityLossTimestampMs)
        assertEquals(300L, recoveries.first().recoveredAtMs)
    }

    @Test
    fun `recovery does not fire if the still stretch afterward is too short`() {
        val loss = StabilityLossEvent(200, 0.9f)
        val velocities = listOf(
            TimedVelocity(0, 0f),
            TimedVelocity(100, 0.1f),
            TimedVelocity(200, 1.0f),
            TimedVelocity(300, 0.05f),
            TimedVelocity(400, 0.05f), // only 100ms below threshold, need 800ms
        )
        assertTrue(detectRecoveries(velocities, listOf(loss), CONFIG).isEmpty())
    }

    // -- detectFallCandidates ---------------------------------------------------------------------

    @Test
    fun `fall candidate fires on a large fast downward hip movement`() {
        val frames = listOf(
            frame(0),
            frame(100, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to 0.7f), PoseLandmarkType.RIGHT_HIP to (0.55f to 0.7f))),
        )
        val events = detectFallCandidates(frames, CONFIG)
        assertEquals(1, events.size)
        assertEquals(100L, events.first().timestampMs)
    }

    @Test
    fun `fall candidate does not fire on ordinary small downward movement`() {
        val frames = listOf(
            frame(0),
            frame(100, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to 0.52f), PoseLandmarkType.RIGHT_HIP to (0.55f to 0.52f))),
        )
        assertTrue(detectFallCandidates(frames, CONFIG).isEmpty())
    }

    // -- detectFinishStabilization ----------------------------------------------------------------

    @Test
    fun `finish stabilization fires for a long enough pause right at the climb's end`() {
        val pauses = listOf(PauseSegment(9000, 9900))
        val finish = detectFinishStabilization(pauses, climbEndMs = 10_000L, config = CONFIG)
        assertEquals(FinishStabilizationEvent(9000, 9900), finish)
    }

    @Test
    fun `finish stabilization does not fire for a pause far from the climb's end`() {
        val pauses = listOf(PauseSegment(1000, 1900))
        assertNull(detectFinishStabilization(pauses, climbEndMs = 10_000L, config = CONFIG))
    }

    @Test
    fun `finish stabilization does not fire for a too-short pause near the end`() {
        val pauses = listOf(PauseSegment(9500, 9900))
        assertNull(detectFinishStabilization(pauses, climbEndMs = 10_000L, config = CONFIG))
    }

    // -- detectMissedReachCandidates ----------------------------------------------------------------

    @Test
    fun `missed reach candidate fires for a fast wrist move shortly before a fall`() {
        val fall = FallCandidateEvent(2000, 2.0f)
        val frames = listOf(
            frame(1800, mapOf(PoseLandmarkType.LEFT_WRIST to (0.4f to 0.5f))),
            frame(1900, mapOf(PoseLandmarkType.LEFT_WRIST to (0.6f to 0.3f))),
        )
        val events = detectMissedReachCandidates(frames, listOf(fall), CONFIG)
        assertEquals(1, events.size)
        assertEquals(Side.LEFT, events.first().side)
        assertEquals(2000L, events.first().fallTimestampMs)
    }

    @Test
    fun `missed reach candidate does not fire without any fall candidates`() {
        val frames = listOf(
            frame(1800, mapOf(PoseLandmarkType.LEFT_WRIST to (0.4f to 0.5f))),
            frame(1900, mapOf(PoseLandmarkType.LEFT_WRIST to (0.6f to 0.3f))),
        )
        assertTrue(detectMissedReachCandidates(frames, emptyList(), CONFIG).isEmpty())
    }

    @Test
    fun `missed reach candidate does not fire for a slow hand move before a fall`() {
        val fall = FallCandidateEvent(2000, 2.0f)
        val frames = listOf(
            frame(1800, mapOf(PoseLandmarkType.LEFT_WRIST to (0.4f to 0.5f))),
            frame(1900, mapOf(PoseLandmarkType.LEFT_WRIST to (0.41f to 0.49f))),
        )
        assertTrue(detectMissedReachCandidates(frames, listOf(fall), CONFIG).isEmpty())
    }
}
