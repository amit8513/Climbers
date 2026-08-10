package com.example.climb.analysis.scoring

import com.example.climb.analysis.metrics.AnalysisComputation
import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.metrics.DisengagedLegSegment
import com.example.climb.analysis.metrics.FallCandidateEvent
import com.example.climb.analysis.metrics.HighStepEvent
import com.example.climb.analysis.metrics.PauseSegment
import com.example.climb.analysis.metrics.RecoveryEvent
import com.example.climb.analysis.metrics.Side
import com.example.climb.analysis.metrics.StabilityLossEvent
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lm(type: PoseLandmarkType, xy: Pair<Float, Float>) = PoseLandmark(type, xy.first, xy.second, 0f, 1f, 1f)

private val DEFAULT_LANDMARKS: Map<PoseLandmarkType, Pair<Float, Float>> = mapOf(
    PoseLandmarkType.LEFT_SHOULDER to (0.4f to 0.2f),
    PoseLandmarkType.RIGHT_SHOULDER to (0.6f to 0.2f),
    PoseLandmarkType.LEFT_HIP to (0.45f to 0.5f),
    PoseLandmarkType.RIGHT_HIP to (0.55f to 0.5f),
    PoseLandmarkType.LEFT_ANKLE to (0.45f to 0.9f),
    PoseLandmarkType.RIGHT_ANKLE to (0.55f to 0.9f),
)

private fun frame(timestampMs: Long, overrides: Map<PoseLandmarkType, Pair<Float, Float>> = emptyMap(), reliable: Boolean = true): PoseFrame {
    val merged = DEFAULT_LANDMARKS + overrides
    return PoseFrame(timestampMs, merged.map { (type, xy) -> lm(type, xy) }, averageConfidence = if (reliable) 1f else 0f, isReliable = reliable, bodyBoundingBox = null)
}

/** A handful of still frames - no dynamic move, no pauses long enough to matter on their own. */
private fun stillFrames(count: Int = 6, stepMs: Long = 100L) = (0 until count).map { i -> frame(i * stepMs) }

private fun metrics(
    climbStartMs: Long = 0,
    climbEndMs: Long = 500,
    reliableFramePercentage: Float = 100f,
    straightArmPercentage: Float = 0f,
    possibleFootAdjustments: Int = 0,
    possibleFootSlips: Int = 0,
    totalLockoffMs: Long = 0,
    legDriveCandidateCount: Int = 0,
    kneeRangeOfMotionDegrees: Float = 0f,
    footStabilityScore: Int = 0,
    footWeightAsymmetry: Float = 0f,
    pauseTimeMs: Long = 0,
) = ClimbMetrics(
    totalDurationMs = climbEndMs - climbStartMs,
    activeMovementMs = climbEndMs - climbStartMs - pauseTimeMs,
    pauseTimeMs = pauseTimeMs,
    pauseCount = 0,
    longestPauseMs = 0,
    leftLockoffMs = 0,
    rightLockoffMs = 0,
    totalLockoffMs = totalLockoffMs,
    longestLockoffMs = 0,
    possibleFootAdjustments = possibleFootAdjustments,
    possibleFootSlips = possibleFootSlips,
    possibleDisengagedLegSegments = 0,
    straightArmPercentage = straightArmPercentage,
    estimatedMovementEfficiency = 0,
    reliableFramePercentage = reliableFramePercentage,
    climbStartMs = climbStartMs,
    climbEndMs = climbEndMs,
    highStepCount = 0,
    possibleStabilityLossCount = 0,
    possibleFallCandidateCount = 0,
    hasFinishStabilization = false,
    possibleMissedReachCount = 0,
    legDriveCandidateCount = legDriveCandidateCount,
    kneeRangeOfMotionDegrees = kneeRangeOfMotionDegrees,
    footStabilityScore = footStabilityScore,
    totalFootTravelNormalized = 0f,
    footWeightAsymmetry = footWeightAsymmetry,
)

private fun computation(
    metrics: ClimbMetrics = metrics(),
    pauses: List<PauseSegment> = emptyList(),
    highSteps: List<HighStepEvent> = emptyList(),
    stabilityLossEvents: List<StabilityLossEvent> = emptyList(),
    recoveries: List<RecoveryEvent> = emptyList(),
    fallCandidates: List<FallCandidateEvent> = emptyList(),
    disengagedLegs: List<DisengagedLegSegment> = emptyList(),
) = AnalysisComputation(
    metrics = metrics,
    pauses = pauses,
    lockoffs = emptyList(),
    footAdjustments = emptyList(),
    footSlips = emptyList(),
    disengagedLegs = disengagedLegs,
    highSteps = highSteps,
    stabilityLossEvents = stabilityLossEvents,
    recoveries = recoveries,
    fallCandidates = fallCandidates,
    finishStabilization = null,
    missedReachCandidates = emptyList(),
)

private fun PerformanceResult.category(cat: PerformanceCategory) = categoryScores.first { it.category == cat }

class PerformanceScorerTest {

    @Test
    fun `overall score and confidence always stay within their valid ranges`() {
        val scenarios = listOf(
            computation(),
            computation(metrics = metrics(straightArmPercentage = 90f, possibleFootAdjustments = 10, possibleFootSlips = 5)),
            computation(stabilityLossEvents = listOf(StabilityLossEvent(100, 0.9f), StabilityLossEvent(200, 0.9f))),
        )
        for (comp in scenarios) {
            val result = scorePerformance(stillFrames(), comp)
            assertTrue("overall score out of range: ${result.overallScore}", result.overallScore in 0..100)
            assertTrue("overall confidence out of range: ${result.overallConfidence}", result.overallConfidence in 0f..1f)
            for (categoryScore in result.categoryScores) {
                assertTrue("${categoryScore.category} score out of range: ${categoryScore.score}", categoryScore.score in 0..100)
            }
        }
    }

    @Test
    fun `fully unreliable tracking drives overall score and confidence to zero`() {
        val comp = computation(metrics = metrics(reliableFramePercentage = 0f))
        val result = scorePerformance(stillFrames(), comp)
        assertEquals(0, result.overallScore)
        assertEquals(0f, result.overallConfidence)
    }

    @Test
    fun `technique score rewards straight-arm time and steady feet, penalizes adjustments and slips`() {
        val good = scorePerformance(stillFrames(), computation(metrics = metrics(straightArmPercentage = 80f, footStabilityScore = 90))).category(PerformanceCategory.TECHNIQUE)
        val bad = scorePerformance(stillFrames(), computation(metrics = metrics(straightArmPercentage = 10f, possibleFootAdjustments = 6, possibleFootSlips = 3, footStabilityScore = 20))).category(PerformanceCategory.TECHNIQUE)
        assertTrue("expected the clean climb to score higher on technique (good=${good.score}, bad=${bad.score})", good.score > bad.score)
        assertTrue(good.unavailableFactors.any { it.contains("Grip", ignoreCase = true) })
    }

    @Test
    fun `power score penalizes a dynamic move immediately followed by an unrecovered stability loss`() {
        // A clean upward hip spike at t=100ms - a genuine large dynamic move by the detector's own threshold.
        val frames = listOf(frame(0), frame(100, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to 0.3f), PoseLandmarkType.RIGHT_HIP to (0.55f to 0.3f)))) +
            (200..500L step 100).map { frame(it, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to 0.3f), PoseLandmarkType.RIGHT_HIP to (0.55f to 0.3f))) }

        val cleanCatch = scorePerformance(frames, computation()).category(PerformanceCategory.POWER)
        val poorCatch = scorePerformance(frames, computation(stabilityLossEvents = listOf(StabilityLossEvent(300, 0.9f)))).category(PerformanceCategory.POWER)

        assertTrue(
            "expected an unrecovered stability loss right after a dynamic move to reduce the power score (clean=${cleanCatch.score}, poor=${poorCatch.score})",
            poorCatch.score < cleanCatch.score,
        )
    }

    @Test
    fun `power score gives no credit for a dynamic move that is immediately recovered`() {
        val frames = listOf(frame(0), frame(100, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to 0.3f), PoseLandmarkType.RIGHT_HIP to (0.55f to 0.3f)))) +
            (200..500L step 100).map { frame(it, mapOf(PoseLandmarkType.LEFT_HIP to (0.45f to 0.3f), PoseLandmarkType.RIGHT_HIP to (0.55f to 0.3f))) }

        val loss = StabilityLossEvent(300, 0.9f)
        val recovered = scorePerformance(frames, computation(stabilityLossEvents = listOf(loss), recoveries = listOf(RecoveryEvent(300, 400, 100)))).category(PerformanceCategory.POWER)
        val unrecovered = scorePerformance(frames, computation(stabilityLossEvents = listOf(loss))).category(PerformanceCategory.POWER)

        assertTrue(
            "expected a confirmed recovery to score no worse than an unrecovered loss (recovered=${recovered.score}, unrecovered=${unrecovered.score})",
            recovered.score >= unrecovered.score,
        )
    }

    @Test
    fun `endurance confidence is reduced for a short attempt`() {
        val short = scorePerformance(stillFrames(), computation(metrics = metrics(climbStartMs = 0, climbEndMs = 5_000))).category(PerformanceCategory.ENDURANCE)
        val long = scorePerformance(stillFrames(), computation(metrics = metrics(climbStartMs = 0, climbEndMs = 30_000))).category(PerformanceCategory.ENDURANCE)
        assertTrue("expected a <15s attempt to have lower endurance confidence than a 30s one (short=${short.confidence}, long=${long.confidence})", short.confidence < long.confidence)
        assertTrue(short.unavailableFactors.isNotEmpty())
    }

    @Test
    fun `endurance score penalizes pausing that grows worse in the second half of the climb`() {
        val steady = computation(
            metrics = metrics(climbStartMs = 0, climbEndMs = 20_000),
            pauses = listOf(PauseSegment(1_000, 2_000), PauseSegment(11_000, 12_000)),
        )
        val fading = computation(
            metrics = metrics(climbStartMs = 0, climbEndMs = 20_000),
            pauses = listOf(PauseSegment(1_000, 1_500), PauseSegment(11_000, 15_000)),
        )
        val steadyScore = scorePerformance(stillFrames(), steady).category(PerformanceCategory.ENDURANCE)
        val fadingScore = scorePerformance(stillFrames(), fading).category(PerformanceCategory.ENDURANCE)
        assertTrue(
            "expected pacing that gets worse in the second half to score lower (steady=${steadyScore.score}, fading=${fadingScore.score})",
            fadingScore.score < steadyScore.score,
        )
    }

    @Test
    fun `flexibility score increases with knee range of motion and high steps`() {
        val low = scorePerformance(stillFrames(), computation()).category(PerformanceCategory.OBSERVED_MOVEMENT_RANGE)
        val high = scorePerformance(
            stillFrames(),
            computation(
                metrics = metrics(kneeRangeOfMotionDegrees = 90f),
                highSteps = listOf(HighStepEvent(Side.LEFT, 100, 0.2f)),
            ),
        ).category(PerformanceCategory.OBSERVED_MOVEMENT_RANGE)
        assertTrue("expected knee ROM and a high step to raise the flexibility score (low=${low.score}, high=${high.score})", high.score > low.score)
        assertTrue(high.unavailableFactors.any { it.contains("medical", ignoreCase = true) })
    }

    @Test
    fun `balance score penalizes unrecovered stability loss and heavy foot asymmetry`() {
        val clean = scorePerformance(stillFrames(), computation()).category(PerformanceCategory.BALANCE)
        val shaky = scorePerformance(
            stillFrames(),
            computation(
                metrics = metrics(footWeightAsymmetry = 0.95f),
                stabilityLossEvents = listOf(StabilityLossEvent(100, 0.9f), StabilityLossEvent(200, 0.9f)),
                disengagedLegs = listOf(DisengagedLegSegment(Side.LEFT, 100, 1_500)),
            ),
        ).category(PerformanceCategory.BALANCE)
        assertTrue("expected instability and asymmetry to reduce the balance score (clean=${clean.score}, shaky=${shaky.score})", shaky.score < clean.score)
        assertTrue(clean.unavailableFactors.any { it.contains("depth", ignoreCase = true) })
    }

    @Test
    fun `strategy confidence is always capped low, even with perfectly reliable tracking`() {
        val result = scorePerformance(stillFrames(), computation(metrics = metrics(reliableFramePercentage = 100f))).category(PerformanceCategory.STRATEGY)
        assertTrue("expected strategy confidence to stay capped low regardless of tracking quality, got ${result.confidence}", result.confidence <= 0.45f)
        assertTrue(result.unavailableFactors.isNotEmpty())
    }

    @Test
    fun `strategy score rewards decisive foot placement and a typical planning pace`() {
        val decisive = scorePerformance(
            stillFrames(),
            computation(metrics = metrics(climbEndMs = 10_000, possibleFootAdjustments = 1, pauseTimeMs = 2_000)),
        ).category(PerformanceCategory.STRATEGY)
        val indecisive = scorePerformance(
            stillFrames(),
            computation(metrics = metrics(climbEndMs = 10_000, possibleFootAdjustments = 8, pauseTimeMs = 6_000)),
        ).category(PerformanceCategory.STRATEGY)
        assertTrue("expected decisive footwork and typical pacing to score higher (decisive=${decisive.score}, indecisive=${indecisive.score})", decisive.score > indecisive.score)
    }
}
