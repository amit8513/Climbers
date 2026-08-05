package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.VGrade
import com.example.climb.leaderboard.model.gradePoints

private const val FLASH_MULTIPLIER = 1.25
private const val SECOND_ATTEMPT_MULTIPLIER = 1.15

/**
 * sendPoints for one completed climb. Kept as a [Double] — internal calculations stay precise;
 * rounding only happens once, on a final total, never per-send.
 */
fun sendPoints(vGrade: VGrade, isFlash: Boolean, successfulAttemptNumber: Int): Double {
    val basePoints = vGrade.gradePoints().toDouble()
    return when {
        isFlash -> basePoints * FLASH_MULTIPLIER
        successfulAttemptNumber == 2 -> basePoints * SECOND_ATTEMPT_MULTIPLIER
        else -> basePoints
    }
}
