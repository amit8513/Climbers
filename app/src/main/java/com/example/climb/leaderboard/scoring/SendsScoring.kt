package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbAttempt
import kotlin.math.roundToInt

data class SendsScoreResult(
    val weightedSendScore: Int,
    val uniqueSends: Int,
    val flashCount: Int,
    val averageAttemptsPerSend: Float,
    val mostRecentSendAt: Long?,
)

/** weightedSendScore = sum of best sendPoints for every unique completed problem — unlike
 * Overall, not limited to the top five. */
fun computeSendsScore(bestSends: Collection<ClimbAttempt>): SendsScoreResult {
    val scores = bestSends.mapNotNull { attempt -> attempt.vGrade?.let { sendPoints(it, attempt.isFlash, attempt.attemptNumber) } }
    return SendsScoreResult(
        weightedSendScore = scores.sum().roundToInt(),
        uniqueSends = bestSends.size,
        flashCount = bestSends.count { it.isFlash },
        averageAttemptsPerSend = if (bestSends.isEmpty()) 0f else bestSends.map { it.attemptNumber }.average().toFloat(),
        mostRecentSendAt = bestSends.maxOfOrNull { it.attemptedAt },
    )
}
