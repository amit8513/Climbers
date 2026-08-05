package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbAttempt
import com.example.climb.leaderboard.model.VGrade

data class VGradeScoreResult(
    val highestVGrade: VGrade?,
    /** Average of up to three hardest unique sends; null when there are none. */
    val topThreeAverageGrade: Double?,
    /** How many sends the average above actually used — may be fewer than three. */
    val topThreeAverageSendCount: Int,
    val hardestSendAttemptCount: Int,
    val uniqueSendsAtHighestGrade: Int,
    val mostRecentHardestSendAt: Long?,
)

fun computeVGradeScore(bestSends: Collection<ClimbAttempt>): VGradeScoreResult {
    val graded = bestSends.filter { it.vGrade != null }
    if (graded.isEmpty()) return VGradeScoreResult(null, null, 0, 0, 0, null)

    val highest = graded.maxOf { it.vGrade!! }
    val topThree = graded.sortedByDescending { it.vGrade!!.numericValue }.take(3)
    val topThreeAverage = topThree.map { it.vGrade!!.numericValue }.average()
    val atHighest = graded.filter { it.vGrade == highest }
    val hardestSendAttemptCount = atHighest.minOf { it.attemptNumber }
    val mostRecentHardestSendAt = atHighest.maxOf { it.attemptedAt }

    return VGradeScoreResult(
        highestVGrade = highest,
        topThreeAverageGrade = topThreeAverage,
        topThreeAverageSendCount = topThree.size,
        hardestSendAttemptCount = hardestSendAttemptCount,
        uniqueSendsAtHighestGrade = atHighest.size,
        mostRecentHardestSendAt = mostRecentHardestSendAt,
    )
}
