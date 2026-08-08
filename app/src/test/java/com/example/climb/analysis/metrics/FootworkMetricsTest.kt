package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lm(type: PoseLandmarkType, xy: Pair<Float, Float>) = PoseLandmark(type, xy.first, xy.second, 0f, 1f, 1f)

/** A standing, roughly straight-legged default pose: shoulders/hips/knees/ankles/feet stacked
 * vertically, hip-knee-ankle colinear per side (180° knee angle) so tests only need to override
 * whichever landmark they're actually exercising. */
private val DEFAULT_LANDMARKS: Map<PoseLandmarkType, Pair<Float, Float>> = mapOf(
    PoseLandmarkType.LEFT_SHOULDER to (0.4f to 0.2f),
    PoseLandmarkType.RIGHT_SHOULDER to (0.6f to 0.2f),
    PoseLandmarkType.LEFT_HIP to (0.45f to 0.5f),
    PoseLandmarkType.RIGHT_HIP to (0.55f to 0.5f),
    PoseLandmarkType.LEFT_KNEE to (0.45f to 0.7f),
    PoseLandmarkType.RIGHT_KNEE to (0.55f to 0.7f),
    PoseLandmarkType.LEFT_ANKLE to (0.45f to 0.9f),
    PoseLandmarkType.RIGHT_ANKLE to (0.55f to 0.9f),
    PoseLandmarkType.LEFT_FOOT_INDEX to (0.45f to 0.92f),
    PoseLandmarkType.RIGHT_FOOT_INDEX to (0.55f to 0.92f),
)

private fun frame(
    timestampMs: Long,
    overrides: Map<PoseLandmarkType, Pair<Float, Float>> = emptyMap(),
    reliable: Boolean = true,
): PoseFrame {
    val merged = DEFAULT_LANDMARKS + overrides
    return PoseFrame(
        timestampMs = timestampMs,
        landmarks = merged.map { (type, xy) -> lm(type, xy) },
        averageConfidence = if (reliable) 1f else 0f,
        isReliable = reliable,
        bodyBoundingBox = null,
    )
}

class FootworkMetricsTest {

    @Test
    fun `total foot travel reflects real foot movement`() {
        val frames = listOf(
            frame(0),
            frame(100, mapOf(PoseLandmarkType.LEFT_FOOT_INDEX to (0.60f to 0.92f))),
            frame(200, mapOf(PoseLandmarkType.LEFT_FOOT_INDEX to (0.60f to 0.92f))),
        )
        val travel = totalFootTravelNormalized(frames)
        assertTrue("expected meaningful foot travel from the left-foot move, got $travel", travel > 0.1f)
    }

    @Test
    fun `foot weight asymmetry is low when both feet move equally`() {
        val frames = listOf(
            frame(0),
            frame(
                100,
                mapOf(
                    PoseLandmarkType.LEFT_FOOT_INDEX to (0.55f to 0.92f),
                    PoseLandmarkType.RIGHT_FOOT_INDEX to (0.45f to 0.92f),
                ),
            ),
        )
        val asymmetry = footWeightAsymmetry(frames)
        assertTrue("expected low asymmetry when both feet moved equally, got $asymmetry", asymmetry < 0.2f)
    }

    @Test
    fun `foot weight asymmetry is high when only one foot moves`() {
        val frames = listOf(
            frame(0),
            frame(100, mapOf(PoseLandmarkType.LEFT_FOOT_INDEX to (0.60f to 0.92f))),
        )
        val asymmetry = footWeightAsymmetry(frames)
        assertTrue("expected high asymmetry when only the left foot moved, got $asymmetry", asymmetry > 0.9f)
    }

    @Test
    fun `foot stability score is zero with no settled-foot data`() {
        val frames = (0..3).map { i -> frame(i * 100L, mapOf(PoseLandmarkType.LEFT_FOOT_INDEX to ((0.45f + i * 0.05f) to 0.92f))) }
        assertEquals(0, footStabilityScore(frames, MetricsConfiguration()))
    }

    @Test
    fun `foot stability score is high for a foot that settles and barely moves`() {
        val frames = (0..7).map { i -> frame(i * 100L) }
        val score = footStabilityScore(frames, MetricsConfiguration())
        assertTrue("expected a high stability score for a foot that never moved, got $score", score >= 90)
    }

    @Test
    fun `knee range of motion reflects the angle swing actually observed`() {
        val frames = listOf(
            frame(0),
            frame(100, mapOf(PoseLandmarkType.LEFT_KNEE to (0.65f to 0.65f))),
            frame(200),
        )
        val rom = kneeRangeOfMotionDegrees(frames)
        assertTrue("expected a meaningfully large knee ROM from straight to bent, got $rom", rom > 20f)
    }

    @Test
    fun `leg drive candidate fires when a knee extends rapidly just before a dynamic move`() {
        val frames = listOf(
            frame(400, mapOf(PoseLandmarkType.LEFT_KNEE to (0.65f to 0.65f))),
            frame(500), // knee straightens back out over 100ms -> fast extension
        )
        val candidates = detectLegDriveCandidates(frames, listOf(600L), MetricsConfiguration())
        assertTrue("expected a left leg-drive candidate before the dynamic move", candidates.any { it.side == Side.LEFT })
    }

    @Test
    fun `leg drive candidate does not fire without a dynamic move to attribute it to`() {
        val frames = listOf(
            frame(400, mapOf(PoseLandmarkType.LEFT_KNEE to (0.65f to 0.65f))),
            frame(500),
        )
        assertTrue(detectLegDriveCandidates(frames, emptyList(), MetricsConfiguration()).isEmpty())
    }

    @Test
    fun `computeAnalysis populates footwork metrics without crashing on a short clip`() {
        val frames = (0..10).map { i -> frame(i * 100L) }
        val computation = computeAnalysis(frames)
        assertTrue(computation.metrics.kneeRangeOfMotionDegrees >= 0f)
        assertTrue(computation.metrics.footStabilityScore in 0..100)
        assertTrue(computation.metrics.totalFootTravelNormalized >= 0f)
        assertTrue(computation.metrics.footWeightAsymmetry in 0f..1f)
        assertTrue(computation.metrics.legDriveCandidateCount >= 0)
    }
}
