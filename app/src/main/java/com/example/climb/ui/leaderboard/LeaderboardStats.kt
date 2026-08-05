package com.example.climb.ui.leaderboard

import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.scoring.CONSISTENCY_MIN_UNIQUE_ATTEMPTED
import java.util.Locale
import kotlin.math.roundToInt

/** The category's headline number/grade — shown large on the podium and at the start of a row. */
fun primaryValue(category: LeaderboardCategory, entry: LeaderboardEntry): String = when (category) {
    LeaderboardCategory.OVERALL -> "${entry.overallScore}"
    LeaderboardCategory.V_GRADE -> entry.highestVGrade?.displayName ?: "—"
    LeaderboardCategory.CONSISTENCY -> if (hasEnoughConsistencyData(entry)) "${consistencyPercent(entry)}%" else "—"
    LeaderboardCategory.SESSIONS -> "${entry.activeDays}"
    LeaderboardCategory.SENDS -> "${entry.weightedSendScore}"
}

fun hasEnoughConsistencyData(entry: LeaderboardEntry): Boolean = entry.uniqueProblemsAttempted >= CONSISTENCY_MIN_UNIQUE_ATTEMPTED

fun consistencyPercent(entry: LeaderboardEntry): Int = (entry.consistencyRate * 100).roundToInt()

private fun formatGrade(value: Double?): String = value?.let { String.format(Locale.US, "V%.1f", it) } ?: "—"

/** Two lines for the podium (top-3), matching the spec's "two supporting statistics" per entry. */
fun podiumSupportingLines(category: LeaderboardCategory, entry: LeaderboardEntry): List<String> = when (category) {
    LeaderboardCategory.OVERALL -> listOf(
        entry.highestVGrade?.displayName ?: "No sends yet",
        "${consistencyPercent(entry)}% consistency",
    )
    LeaderboardCategory.V_GRADE -> listOf(
        "Top 3 avg: ${formatGrade(entry.topThreeAverageGrade)}",
        "${entry.hardestSendAttemptCount.takeIf { it > 0 } ?: 1} attempt${if (entry.hardestSendAttemptCount == 1) "" else "s"}",
    )
    LeaderboardCategory.CONSISTENCY -> if (hasEnoughConsistencyData(entry)) listOf(
        "${entry.uniqueProblemsSent} of ${entry.uniqueProblemsAttempted} sent",
        "Average: ${formatGrade(entry.averageSentGrade)}",
    ) else listOf("Not enough data")
    LeaderboardCategory.SESSIONS -> listOf(
        "${entry.qualitySessionCount} quality sessions",
        "${entry.currentStreak}-day streak",
    )
    LeaderboardCategory.SENDS -> listOf(
        "${entry.uniqueProblemsSent} unique sends",
        entry.highestVGrade?.displayName ?: "No sends yet",
    )
}

/** Fuller stat set for ranked rows (rank 4+), matching each category's "Ranked-row display" list. */
fun rowSupportingLines(category: LeaderboardCategory, entry: LeaderboardEntry): List<String> = when (category) {
    LeaderboardCategory.OVERALL -> listOf(
        entry.highestVGrade?.displayName ?: "No sends yet",
        "${consistencyPercent(entry)}% consistency",
        "${entry.qualitySessionCount} quality sessions",
    )
    LeaderboardCategory.V_GRADE -> listOf(
        "Top 3 avg: ${formatGrade(entry.topThreeAverageGrade)}",
        "${entry.hardestSendAttemptCount.takeIf { it > 0 } ?: 1} attempt${if (entry.hardestSendAttemptCount == 1) "" else "s"}",
    ) + if (entry.flashCount > 0) listOf("Flash") else emptyList()
    LeaderboardCategory.CONSISTENCY -> if (hasEnoughConsistencyData(entry)) listOf(
        "${entry.uniqueProblemsSent} of ${entry.uniqueProblemsAttempted} sent",
        "Average: ${formatGrade(entry.averageSentGrade)}",
    ) else listOf("Not enough qualifying climbs yet")
    LeaderboardCategory.SESSIONS -> listOf(
        "${entry.qualitySessionCount} quality sessions",
        "${entry.currentStreak}-day streak",
        "${String.format(Locale.US, "%.1f", entry.averageSendsPerSession)} sends/session",
    )
    LeaderboardCategory.SENDS -> listOf(
        "${entry.uniqueProblemsSent} unique sends",
        entry.highestVGrade?.displayName ?: "No sends yet",
        "${entry.flashCount} flash${if (entry.flashCount == 1) "" else "es"}",
    )
}

fun rankMovementLabel(entry: LeaderboardEntry): String = when (entry.rankMovementType) {
    com.example.climb.leaderboard.model.RankMovementType.UP -> "Up ${entry.rankDelta}"
    com.example.climb.leaderboard.model.RankMovementType.DOWN -> "Down ${entry.rankDelta}"
    com.example.climb.leaderboard.model.RankMovementType.UNCHANGED -> "No change"
    com.example.climb.leaderboard.model.RankMovementType.NEW -> "New this week"
    com.example.climb.leaderboard.model.RankMovementType.UNRANKED -> "Unranked"
}

/** Accessible description combining name, placement and primary stat, per the spec's examples
 * ("Alex, first place, 1,245 overall points."). */
fun podiumAccessibilityLabel(category: LeaderboardCategory, entry: LeaderboardEntry, place: Int): String {
    val placeText = when (place) { 1 -> "first place"; 2 -> "second place"; else -> "third place" }
    return "${entry.displayName}, $placeText, ${primaryValue(category, entry)} ${category.tabTitle.lowercase(Locale.US)}."
}

fun rankMovementAccessibilityLabel(entry: LeaderboardEntry): String = when (entry.rankMovementType) {
    com.example.climb.leaderboard.model.RankMovementType.UP -> "${entry.displayName} moved up ${entry.rankDelta} place${if (entry.rankDelta == 1) "" else "s"}."
    com.example.climb.leaderboard.model.RankMovementType.DOWN -> "${entry.displayName} moved down ${entry.rankDelta} place${if (entry.rankDelta == 1) "" else "s"}."
    com.example.climb.leaderboard.model.RankMovementType.UNCHANGED -> "${entry.displayName}'s rank is unchanged."
    com.example.climb.leaderboard.model.RankMovementType.NEW -> "${entry.displayName} is new to the leaderboard this week."
    com.example.climb.leaderboard.model.RankMovementType.UNRANKED -> "${entry.displayName} is not currently ranked."
}
