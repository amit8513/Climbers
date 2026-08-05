package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbAttempt
import kotlin.math.roundToInt

data class OverallScoreResult(
    val baseSendScore: Double,
    val consistencyBonus: Double,
    val sessionBonus: Double,
    val overallScore: Int,
    val totalAttemptsInScoredSends: Int,
)

/**
 * baseSendScore = sum of the five highest unique send scores. The clamp in
 * `consistencyRate.coerceIn(0f,1f) * 0.20` already guarantees the bonus never exceeds 20% of
 * baseSendScore, and `min(qualitySessionCount,5)*10` already guarantees the session bonus never
 * exceeds 50 — both caps fall out of the formula rather than needing a separate clamp.
 */
fun computeOverallScore(bestSends: Collection<ClimbAttempt>, consistencyRate: Float, qualitySessionCount: Int): OverallScoreResult {
    val scored = bestSends.mapNotNull { attempt ->
        attempt.vGrade?.let { grade -> Triple(sendPoints(grade, attempt.isFlash, attempt.attemptNumber), attempt.attemptNumber, attempt) }
    }
    val topFive = scored.sortedByDescending { it.first }.take(5)
    val baseSendScore = topFive.sumOf { it.first }
    val consistencyBonus = baseSendScore * consistencyRate.coerceIn(0f, 1f) * 0.20
    val sessionBonus = (minOf(qualitySessionCount, 5) * 10).toDouble()
    val overallScore = (baseSendScore + consistencyBonus + sessionBonus).roundToInt()
    val totalAttemptsInScoredSends = topFive.sumOf { it.second }
    return OverallScoreResult(baseSendScore, consistencyBonus, sessionBonus, overallScore, totalAttemptsInScoredSends)
}
