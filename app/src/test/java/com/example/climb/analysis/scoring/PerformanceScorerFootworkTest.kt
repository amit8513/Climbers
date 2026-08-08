package com.example.climb.analysis.scoring

import com.example.climb.analysis.metrics.computeAnalysis
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lm(type: PoseLandmarkType, xy: Pair<Float, Float>) = PoseLandmark(type, xy.first, xy.second, 0f, 1f, 1f)

private val DEFAULT_LANDMARKS: Map<PoseLandmarkType, Pair<Float, Float>> = mapOf(
    PoseLandmarkType.LEFT_SHOULDER to (0.4f to 0.2f),
    PoseLandmarkType.RIGHT_SHOULDER to (0.6f to 0.2f),
    PoseLandmarkType.LEFT_ELBOW to (0.4f to 0.35f),
    PoseLandmarkType.RIGHT_ELBOW to (0.6f to 0.35f),
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

/**
 * Regression coverage for the specific gap this rework closed: before, none of the six category
 * scorers read any leg/foot-specific metric except foot-adjustment/slip counts folded into
 * Technique — Power in particular had zero foot/leg signal. These assert every category that
 * should now cite a footwork metric actually does, in the real [scorePerformance] output.
 */
class PerformanceScorerFootworkTest {

    private fun buildFrames(): List<PoseFrame> {
        // A short synthetic "climb": some hip movement so climb bounds aren't degenerate, a
        // knee bend/straighten so kneeRangeOfMotionDegrees > 0, and a long still stretch so
        // footStabilityScore has settled-window data to report.
        val frames = mutableListOf<PoseFrame>()
        for (i in 0..3) {
            val t = i * 150L
            frames += frame(t, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to (0.5f - i * 0.02f)), PoseLandmarkType.RIGHT_HIP to (0.55f to (0.5f - i * 0.02f))))
        }
        frames += frame(600, mapOf(PoseLandmarkType.LEFT_KNEE to (0.65f to 0.65f)))
        for (i in 0..7) {
            frames += frame(700 + i * 100L)
        }
        return frames
    }

    @Test
    fun `power score cites legDriveCandidateCount as a contributing metric`() {
        val frames = buildFrames()
        val result = scorePerformance(frames, computeAnalysis(frames))
        val power = result.categoryScores.first { it.category == PerformanceCategory.POWER }
        assertTrue(power.contributingMetrics.contains("legDriveCandidateCount"))
    }

    @Test
    fun `technique score cites footStabilityScore as a contributing metric`() {
        val frames = buildFrames()
        val result = scorePerformance(frames, computeAnalysis(frames))
        val technique = result.categoryScores.first { it.category == PerformanceCategory.TECHNIQUE }
        assertTrue(technique.contributingMetrics.contains("footStabilityScore"))
    }

    @Test
    fun `flexibility score cites kneeRangeOfMotionDegrees as a contributing metric`() {
        val frames = buildFrames()
        val result = scorePerformance(frames, computeAnalysis(frames))
        val flexibility = result.categoryScores.first { it.category == PerformanceCategory.OBSERVED_MOVEMENT_RANGE }
        assertTrue(flexibility.contributingMetrics.contains("kneeRangeOfMotionDegrees"))
    }

    @Test
    fun `balance score cites footWeightAsymmetry as a contributing metric`() {
        val frames = buildFrames()
        val result = scorePerformance(frames, computeAnalysis(frames))
        val balance = result.categoryScores.first { it.category == PerformanceCategory.BALANCE }
        assertTrue(balance.contributingMetrics.contains("footWeightAsymmetry"))
    }
}
