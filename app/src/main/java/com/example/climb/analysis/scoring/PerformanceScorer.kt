package com.example.climb.analysis.scoring

import com.example.climb.analysis.metrics.AnalysisComputation
import com.example.climb.analysis.metrics.MetricsConfiguration
import com.example.climb.analysis.metrics.computeHipVelocities
import com.example.climb.analysis.metrics.detectLargeDynamicMoves
import com.example.climb.analysis.metrics.smoothVelocities
import com.example.climb.pose.PoseFrame
import kotlin.math.roundToInt

/** Internally named per the product spec's own distinction — this reflects movement range
 * actually used during this climb, not a medical flexibility assessment. Always shown as
 * "Flexibility" in the UI via [displayName]. */
enum class PerformanceCategory(val displayName: String) {
    TECHNIQUE("Technique"),
    POWER("Power"),
    ENDURANCE("Endurance"),
    OBSERVED_MOVEMENT_RANGE("Flexibility"),
    BALANCE("Balance"),
    STRATEGY("Strategy"),
}

data class CategoryScore(
    val category: PerformanceCategory,
    val score: Int,
    val confidence: Float,
    val contributingMetrics: List<String>,
    val positiveFactors: List<String>,
    val negativeFactors: List<String>,
    val unavailableFactors: List<String>,
    val explanation: String,
)

/** Weights live here, not hardcoded inline, so they can be tuned or A/B'd without touching any
 * detector or UI code. Weights are expected to sum to 1.0 — [version] changes whenever that
 * expectation, or the formula itself, meaningfully changes, so old and new scores can be told
 * apart. */
data class ScoringConfig(
    val techniqueWeight: Float = 0.22f,
    val balanceWeight: Float = 0.18f,
    val strategyWeight: Float = 0.17f,
    val powerWeight: Float = 0.16f,
    val flexibilityWeight: Float = 0.14f,
    val enduranceWeight: Float = 0.13f,
    val version: Int = 1,
)

data class PerformanceResult(
    val overallScore: Int,
    val overallConfidence: Float,
    val categoryScores: List<CategoryScore>,
    val scoringConfig: ScoringConfig,
)

private fun clampScore(value: Float): Int = value.roundToInt().coerceIn(0, 100)

/**
 * Scores the six categories from metrics/events already computed by [com.example.climb.analysis.metrics.computeAnalysis] —
 * never invents a value, and never substitutes zero for something unavailable (an unavailable
 * factor is recorded in [CategoryScore.unavailableFactors] and lowers that category's confidence
 * instead). The overall score weights each category by its own confidence as well as its
 * [ScoringConfig] weight, so a category with nothing reliable to go on can't drag the total up
 * or down by as much as a well-supported one.
 */
fun scorePerformance(
    frames: List<PoseFrame>,
    computation: AnalysisComputation,
    scoringConfig: ScoringConfig = ScoringConfig(),
    metricsConfig: MetricsConfiguration = MetricsConfiguration(),
): PerformanceResult {
    val metrics = computation.metrics
    val baseConfidence = (metrics.reliableFramePercentage / 100f).coerceIn(0f, 1f)
    val dynamicMoves = detectLargeDynamicMoves(smoothVelocities(computeHipVelocities(frames)), metricsConfig)

    val categoryScores = listOf(
        scoreTechnique(computation, baseConfidence),
        scorePower(computation, dynamicMoves, baseConfidence),
        scoreEndurance(computation, baseConfidence),
        scoreFlexibility(computation, baseConfidence),
        scoreBalance(computation, baseConfidence),
        scoreStrategy(computation, baseConfidence),
    )

    val weights = mapOf(
        PerformanceCategory.TECHNIQUE to scoringConfig.techniqueWeight,
        PerformanceCategory.BALANCE to scoringConfig.balanceWeight,
        PerformanceCategory.STRATEGY to scoringConfig.strategyWeight,
        PerformanceCategory.POWER to scoringConfig.powerWeight,
        PerformanceCategory.OBSERVED_MOVEMENT_RANGE to scoringConfig.flexibilityWeight,
        PerformanceCategory.ENDURANCE to scoringConfig.enduranceWeight,
    )

    var weightedScoreSum = 0f
    var weightedConfidenceSum = 0f
    for (categoryScore in categoryScores) {
        val effectiveWeight = (weights[categoryScore.category] ?: 0f) * categoryScore.confidence
        weightedScoreSum += categoryScore.score * effectiveWeight
        weightedConfidenceSum += effectiveWeight
    }
    val overallScore = if (weightedConfidenceSum > 0f) (weightedScoreSum / weightedConfidenceSum).roundToInt().coerceIn(0, 100) else 0
    val overallConfidence = weightedConfidenceSum.coerceIn(0f, 1f)

    return PerformanceResult(overallScore, overallConfidence, categoryScores, scoringConfig)
}

private fun scoreTechnique(computation: AnalysisComputation, baseConfidence: Float): CategoryScore {
    val metrics = computation.metrics
    val positives = mutableListOf<String>()
    val negatives = mutableListOf<String>()

    if (metrics.straightArmPercentage >= 55f) {
        positives += "Straight-arm positioning for ${metrics.straightArmPercentage.roundToInt()}% of the climb"
    }
    if (metrics.possibleFootAdjustments <= 1) {
        positives += "Committed foot placements (${metrics.possibleFootAdjustments} possible adjustment${if (metrics.possibleFootAdjustments == 1) "" else "s"})"
    } else {
        negatives += "${metrics.possibleFootAdjustments} possible foot adjustments"
    }
    if (metrics.possibleFootSlips > 0) {
        negatives += "${metrics.possibleFootSlips} possible foot slip${if (metrics.possibleFootSlips == 1) "" else "s"}"
    }

    val unrecoveredLossCount = (computation.stabilityLossEvents.size - computation.recoveries.size).coerceAtLeast(0)
    if (unrecoveredLossCount > 0) {
        negatives += "$unrecoveredLossCount possible stability loss${if (unrecoveredLossCount == 1) "" else "es"} without a confirmed recovery"
    }

    val footAdjustmentPenalty = (metrics.possibleFootAdjustments * 8).coerceAtMost(40)
    val slipPenalty = (metrics.possibleFootSlips * 10).coerceAtMost(30)
    val stabilityPenalty = (unrecoveredLossCount * 12).coerceAtMost(30)
    val score = clampScore(60f + metrics.straightArmPercentage * 0.3f - footAdjustmentPenalty - slipPenalty - stabilityPenalty)

    return CategoryScore(
        category = PerformanceCategory.TECHNIQUE,
        score = score,
        confidence = baseConfidence,
        contributingMetrics = listOf("straightArmPercentage", "possibleFootAdjustments", "possibleFootSlips", "possibleStabilityLossCount"),
        positiveFactors = positives,
        negativeFactors = negatives,
        unavailableFactors = listOf("Grip quality and hold-specific technique aren't measurable from pose alone"),
        explanation = "Based on straight-arm time, foot-placement commitment, and possible slips or stability losses measured from pose tracking.",
    )
}

private fun scorePower(computation: AnalysisComputation, dynamicMoves: List<Long>, baseConfidence: Float): CategoryScore {
    val metrics = computation.metrics
    val positives = mutableListOf<String>()
    val negatives = mutableListOf<String>()

    if (dynamicMoves.isNotEmpty()) positives += "${dynamicMoves.size} large dynamic move${if (dynamicMoves.size == 1) "" else "s"} detected"
    if (metrics.totalLockoffMs > 0) positives += "${"%.1f".format(metrics.totalLockoffMs / 1000f)}s of sustained bent-arm lock-off"

    // A dynamic move immediately followed by an unrecovered stability loss or a fall candidate
    // is a poorly controlled catch, not a demonstration of power — this is what keeps raw speed
    // from being rewarded on its own.
    val recoveredLossTimestamps = computation.recoveries.map { it.stabilityLossTimestampMs }.toSet()
    val unrecoveredLossTimestamps = computation.stabilityLossEvents.map { it.timestampMs }.filter { it !in recoveredLossTimestamps }
    val poorCatchCount = dynamicMoves.count { moveMs ->
        unrecoveredLossTimestamps.any { it in moveMs..(moveMs + 1_000L) } ||
            computation.fallCandidates.any { it.timestampMs in moveMs..(moveMs + 1_000L) }
    }
    if (poorCatchCount > 0) negatives += "$poorCatchCount dynamic move${if (poorCatchCount == 1) "" else "s"} followed by a possible loss of control"

    val moveCountComponent = (dynamicMoves.size * 12).coerceAtMost(50)
    val lockoffComponent = (metrics.totalLockoffMs / 500L).toInt().coerceAtMost(20)
    val catchPenalty = (poorCatchCount * 15).coerceAtMost(40)
    val score = clampScore(40f + moveCountComponent + lockoffComponent - catchPenalty)

    return CategoryScore(
        category = PerformanceCategory.POWER,
        score = score,
        confidence = baseConfidence,
        contributingMetrics = listOf("largeDynamicMoveCount", "totalLockoffMs", "possibleStabilityLossCount", "possibleFallCandidateCount"),
        positiveFactors = positives,
        negativeFactors = negatives,
        unavailableFactors = listOf("Actual force or muscular output isn't measurable from pose alone — this reflects movement speed and control, not strength"),
        explanation = "Based on the number and control of large dynamic movements and sustained lock-off time measured from pose tracking. A fast move that ends in a possible loss of control counts against this score, not for it.",
    )
}

private fun scoreEndurance(computation: AnalysisComputation, baseConfidence: Float): CategoryScore {
    val metrics = computation.metrics
    val positives = mutableListOf<String>()
    val negatives = mutableListOf<String>()
    val unavailable = mutableListOf<String>()

    var confidence = baseConfidence
    val durationSeconds = metrics.totalDurationMs / 1000f
    if (durationSeconds < 15f) {
        confidence *= 0.4f
        unavailable += "Attempt is short enough that pacing changes over time aren't reliably measurable"
    }

    val midpoint = metrics.climbStartMs + (metrics.climbEndMs - metrics.climbStartMs) / 2
    val firstHalfPauseMs = computation.pauses.filter { it.startMs < midpoint }.sumOf { it.durationMs }
    val secondHalfPauseMs = computation.pauses.filter { it.startMs >= midpoint }.sumOf { it.durationMs }
    val firstHalfLossCount = computation.stabilityLossEvents.count { it.timestampMs < midpoint }
    val secondHalfLossCount = computation.stabilityLossEvents.count { it.timestampMs >= midpoint }

    var score = 70f
    when {
        firstHalfPauseMs > 0 && secondHalfPauseMs > firstHalfPauseMs * 1.3f -> {
            score -= 15f
            negatives += "Pausing increased in the second half of the climb (${"%.1f".format(firstHalfPauseMs / 1000f)}s to ${"%.1f".format(secondHalfPauseMs / 1000f)}s)"
        }
        secondHalfPauseMs <= firstHalfPauseMs -> {
            score += 10f
            positives += "Pacing held steady or improved through the second half of the climb"
        }
    }
    if (secondHalfLossCount > firstHalfLossCount) {
        score -= 15f
        negatives += "More possible stability losses in the second half ($firstHalfLossCount to $secondHalfLossCount)"
    }

    return CategoryScore(
        category = PerformanceCategory.ENDURANCE,
        score = clampScore(score),
        confidence = confidence.coerceIn(0f, 1f),
        contributingMetrics = listOf("pauseTimeMs", "possibleStabilityLossCount", "totalDurationMs"),
        positiveFactors = positives,
        negativeFactors = negatives,
        unavailableFactors = unavailable + "Cardiovascular or muscular fatigue isn't directly measurable from pose alone — this reflects pacing and control changes over the attempt only",
        explanation = "Compares pausing and stability between the first and second half of the climb — a rough pacing proxy, not a fitness measurement. Confidence is reduced for short attempts.",
    )
}

private fun scoreFlexibility(computation: AnalysisComputation, baseConfidence: Float): CategoryScore {
    val highSteps = computation.highSteps
    val positives = mutableListOf<String>()
    val negatives = mutableListOf<String>()

    val maxRatio = highSteps.maxOfOrNull { it.hipRelativeHeight } ?: 0f
    if (highSteps.isNotEmpty()) {
        positives += "${highSteps.size} high step${if (highSteps.size == 1) "" else "s"} detected, reaching up to ${(maxRatio * 100).roundToInt()}% of body height above the hips"
    } else {
        negatives += "No high steps (feet placed at or above hip height) detected in this climb"
    }

    val score = clampScore(45f + highSteps.size * 8f + maxRatio * 100f)

    return CategoryScore(
        category = PerformanceCategory.OBSERVED_MOVEMENT_RANGE,
        score = score,
        confidence = baseConfidence,
        contributingMetrics = listOf("highStepCount"),
        positiveFactors = positives,
        negativeFactors = negatives,
        unavailableFactors = listOf("This reflects movement range actually used during this climb, not a medical flexibility assessment — a climber may have more range than any single route required"),
        explanation = "Based on how high feet were placed relative to the hips during this climb, measured from pose tracking.",
    )
}

private fun scoreBalance(computation: AnalysisComputation, baseConfidence: Float): CategoryScore {
    val positives = mutableListOf<String>()
    val negatives = mutableListOf<String>()

    val lossCount = computation.stabilityLossEvents.size
    val recoveredCount = computation.recoveries.size
    val unrecoveredCount = (lossCount - recoveredCount).coerceAtLeast(0)

    var score = 80f
    score -= (lossCount * 8).coerceAtMost(35)
    score -= (unrecoveredCount * 10).coerceAtMost(25)

    when {
        lossCount == 0 -> positives += "No possible stability losses detected"
        unrecoveredCount == 0 -> positives += "Recovered control after every possible stability loss ($recoveredCount of $lossCount)"
    }
    if (unrecoveredCount > 0) negatives += "$unrecoveredCount possible stability loss${if (unrecoveredCount == 1) "" else "es"} without a confirmed recovery"

    if (computation.disengagedLegs.isNotEmpty()) {
        val count = computation.disengagedLegs.size
        negatives += "$count moment${if (count == 1) "" else "s"} with one leg extended and unweighted — this can also just be a flag for balance, not necessarily a technique issue"
        score -= (3 * count).coerceAtMost(9).toFloat()
    }

    return CategoryScore(
        category = PerformanceCategory.BALANCE,
        score = clampScore(score),
        confidence = baseConfidence,
        contributingMetrics = listOf("possibleStabilityLossCount", "possibleDisengagedLegSegments"),
        positiveFactors = positives,
        negativeFactors = negatives,
        unavailableFactors = listOf("True center-of-mass/wall-contact balance needs calibrated depth data this pipeline doesn't have — this is a 2D hip-center-stability proxy"),
        explanation = "Based on how often hip-center movement showed a sudden loss of control, and whether control was recovered afterward, measured from pose tracking.",
    )
}

private fun scoreStrategy(computation: AnalysisComputation, baseConfidence: Float): CategoryScore {
    val metrics = computation.metrics
    val positives = mutableListOf<String>()
    val negatives = mutableListOf<String>()

    var score = 60f
    if (metrics.possibleFootAdjustments <= 2) {
        positives += "Few foot repositionings, suggesting decisive placement choices"
    } else {
        negatives += "${metrics.possibleFootAdjustments} possible foot adjustments, which can indicate indecision about foot placement"
        score -= 10f
    }

    val pauseRatio = if (metrics.totalDurationMs > 0) metrics.pauseTimeMs.toFloat() / metrics.totalDurationMs else 0f
    when {
        pauseRatio in 0.15f..0.4f -> positives += "Pausing time was within a typical range for planning between moves"
        pauseRatio > 0.4f -> {
            negatives += "Paused for ${(pauseRatio * 100).roundToInt()}% of the climb, well above a typical planning pace"
            score -= 10f
        }
    }

    // Pose-only strategy confidence is capped low — real route strategy (sequence, hold choice)
    // needs route/hold context this pipeline doesn't have.
    val confidence = (baseConfidence * 0.5f).coerceAtMost(0.45f)

    return CategoryScore(
        category = PerformanceCategory.STRATEGY,
        score = clampScore(score),
        confidence = confidence,
        contributingMetrics = listOf("possibleFootAdjustments", "pauseTimeMs"),
        positiveFactors = positives,
        negativeFactors = negatives,
        unavailableFactors = listOf(
            "Whether the hold sequence was objectively optimal isn't knowable without route or hold data",
            "Route reading and decision quality can't be assessed from pose alone — this only reflects pacing and repositioning patterns",
        ),
        explanation = "A low-confidence proxy based only on pausing and foot-repositioning patterns — real climbing strategy needs route and hold context this analysis doesn't have.",
    )
}
