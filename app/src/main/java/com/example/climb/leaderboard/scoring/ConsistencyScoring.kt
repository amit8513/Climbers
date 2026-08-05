package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbAttempt

/** Minimum unique attempted problems required to qualify for the Consistency category. */
const val CONSISTENCY_MIN_UNIQUE_ATTEMPTED = 5

data class ConsistencyScoreResult(
    val consistencyRate: Float,
    val uniqueProblemsAttempted: Int,
    val uniqueProblemsSent: Int,
    val averageSentGrade: Double?,
    val totalAttempts: Int,
    val qualifies: Boolean,
)

/** Uses unique attempted problems, never total individual attempts — repeatedly trying one
 * problem must not distort the rate. */
fun computeConsistencyScore(attempts: List<ClimbAttempt>, bestSends: Map<String, ClimbAttempt>): ConsistencyScoreResult {
    val uniqueAttempted = attempts.map { it.problemId }.toSet().size
    val uniqueSent = bestSends.size
    val rate = if (uniqueAttempted == 0) 0f else uniqueSent.toFloat() / uniqueAttempted
    val gradedSends = bestSends.values.mapNotNull { it.vGrade?.numericValue }
    val averageSentGrade = if (gradedSends.isEmpty()) null else gradedSends.average()
    return ConsistencyScoreResult(
        consistencyRate = rate,
        uniqueProblemsAttempted = uniqueAttempted,
        uniqueProblemsSent = uniqueSent,
        averageSentGrade = averageSentGrade,
        totalAttempts = attempts.size,
        qualifies = uniqueAttempted >= CONSISTENCY_MIN_UNIQUE_ATTEMPTED,
    )
}
