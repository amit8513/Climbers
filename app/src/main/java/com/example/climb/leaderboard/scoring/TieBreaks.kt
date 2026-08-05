package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry

/** One best-first comparator per category, encoding each category's numbered tie-break list
 * exactly. Nullable grades sort as lowest (-1) so an ungraded/no-send entry never outranks a
 * graded one on a tie. */
fun comparatorFor(category: LeaderboardCategory): Comparator<LeaderboardEntry> = when (category) {
    LeaderboardCategory.OVERALL -> compareByDescending<LeaderboardEntry> { it.overallScore }
        .thenByDescending { it.highestVGrade?.numericValue ?: -1 }
        .thenByDescending { it.consistencyRate }
        .thenByDescending { it.flashCount }
        .thenBy { it.totalAttemptsInScoredSends }
        .thenByDescending { it.mostRecentQualifyingAt }

    LeaderboardCategory.V_GRADE -> compareByDescending<LeaderboardEntry> { it.highestVGrade?.numericValue ?: -1 }
        .thenByDescending { it.topThreeAverageGrade ?: -1.0 }
        .thenBy { if (it.hardestSendAttemptCount == 0) Int.MAX_VALUE else it.hardestSendAttemptCount }
        .thenByDescending { it.flashCount }
        .thenByDescending { it.uniqueProblemsSent }
        .thenByDescending { it.mostRecentQualifyingAt }

    LeaderboardCategory.CONSISTENCY -> compareByDescending<LeaderboardEntry> { it.consistencyRate }
        .thenByDescending { it.averageSentGrade ?: -1.0 }
        .thenByDescending { it.uniqueProblemsSent }
        .thenBy { it.totalAttempts }
        .thenByDescending { it.highestVGrade?.numericValue ?: -1 }
        .thenByDescending { it.mostRecentQualifyingAt }

    LeaderboardCategory.SESSIONS -> compareByDescending<LeaderboardEntry> { it.activeDays }
        .thenByDescending { it.qualitySessionCount }
        .thenByDescending { it.uniqueProblemsSent }
        .thenByDescending { it.averageSendsPerSession }
        .thenByDescending { it.averageSentGrade ?: -1.0 }
        .thenByDescending { it.mostRecentQualifyingAt }

    LeaderboardCategory.SENDS -> compareByDescending<LeaderboardEntry> { it.weightedSendScore }
        .thenByDescending { it.uniqueProblemsSent }
        .thenByDescending { it.highestVGrade?.numericValue ?: -1 }
        .thenByDescending { it.flashCount }
        .thenBy { if (it.averageAttemptsPerSend <= 0f) Float.MAX_VALUE else it.averageAttemptsPerSend }
        .thenByDescending { it.mostRecentQualifyingAt }
}

/** The value shown as this category's primary number/grade — used by the podium and rows. */
fun primaryValueFor(category: LeaderboardCategory, entry: LeaderboardEntry): String = when (category) {
    LeaderboardCategory.OVERALL -> "${entry.overallScore}"
    LeaderboardCategory.V_GRADE -> entry.highestVGrade?.displayName ?: "—"
    LeaderboardCategory.CONSISTENCY -> "${(entry.consistencyRate * 100).let { Math.round(it) }}%"
    LeaderboardCategory.SESSIONS -> "${entry.activeDays}"
    LeaderboardCategory.SENDS -> "${entry.weightedSendScore}"
}
