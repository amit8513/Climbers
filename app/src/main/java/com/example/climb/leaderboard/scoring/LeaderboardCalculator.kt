package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbAttempt
import com.example.climb.leaderboard.model.ClimbingSession
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.model.RankMovementType
import java.time.ZoneId

/**
 * Computes every category's metrics for one user in one period, in a single pass — a row needs
 * its own category's stats plus the others (podium/rows always show 2-3 supporting numbers), and
 * switching tabs just re-sorts already-computed entries rather than recalculating.
 */
fun calculateEntry(
    userId: String,
    displayName: String,
    avatarUrl: String?,
    attempts: List<ClimbAttempt>,
    sessions: List<ClimbingSession>,
    zoneId: ZoneId,
    sharedVideoCount: Int = 0,
    hasViewableVideo: Boolean = false,
    hasPrivateVideo: Boolean = false,
    isCurrentUser: Boolean = false,
): LeaderboardEntry {
    val bestSends = bestSendsByProblem(attempts)
    val consistency = computeConsistencyScore(attempts, bestSends)
    val session = computeSessionScore(sessions, bestSends.size, zoneId)
    val overall = computeOverallScore(bestSends.values, consistency.consistencyRate, session.qualitySessionCount)
    val vGrade = computeVGradeScore(bestSends.values)
    val sends = computeSendsScore(bestSends.values)

    val mostRecentQualifyingAt = listOfNotNull(
        vGrade.mostRecentHardestSendAt,
        sends.mostRecentSendAt,
        session.mostRecentQualitySessionAt,
        attempts.maxOfOrNull { it.attemptedAt },
    ).maxOrNull() ?: 0L

    val hasActivity = attempts.isNotEmpty() || sessions.isNotEmpty()

    return LeaderboardEntry(
        userId = userId,
        displayName = displayName,
        avatarUrl = avatarUrl,
        rank = 0,
        previousRank = null,
        rankDelta = 0,
        rankMovementType = RankMovementType.UNRANKED,
        overallScore = overall.overallScore,
        totalAttemptsInScoredSends = overall.totalAttemptsInScoredSends,
        highestVGrade = vGrade.highestVGrade,
        topThreeAverageGrade = vGrade.topThreeAverageGrade,
        topThreeAverageSendCount = vGrade.topThreeAverageSendCount,
        hardestSendAttemptCount = vGrade.hardestSendAttemptCount,
        consistencyRate = consistency.consistencyRate,
        uniqueProblemsAttempted = consistency.uniqueProblemsAttempted,
        uniqueProblemsSent = consistency.uniqueProblemsSent,
        averageSentGrade = consistency.averageSentGrade,
        totalAttempts = consistency.totalAttempts,
        activeDays = session.activeDays,
        qualitySessionCount = session.qualitySessionCount,
        currentStreak = session.currentStreak,
        averageSendsPerSession = session.averageSendsPerSession,
        weightedSendScore = sends.weightedSendScore,
        flashCount = sends.flashCount,
        averageAttemptsPerSend = sends.averageAttemptsPerSend,
        sharedVideoCount = sharedVideoCount,
        hasViewableVideo = hasViewableVideo,
        hasPrivateVideo = hasPrivateVideo,
        isCurrentUser = isCurrentUser,
        isEligible = hasActivity,
        eligibilityReason = if (hasActivity) null else "No activity this week",
        mostRecentQualifyingAt = mostRecentQualifyingAt,
    )
}

/**
 * Sorts [entries] with [category]'s comparator and assigns rank/previousRank/rankDelta/movement
 * type by looking each user up in [previousRanks] (userId -> rank in the prior comparable
 * period, already restricted to whoever was eligible+visible then).
 */
fun rankEntries(entries: List<LeaderboardEntry>, category: LeaderboardCategory, previousRanks: Map<String, Int>): List<LeaderboardEntry> {
    val sorted = entries.sortedWith(comparatorFor(category))
    return sorted.mapIndexed { index, entry ->
        val rank = index + 1
        val previousRank = previousRanks[entry.userId]
        val (delta, movement) = when {
            previousRank == null -> 0 to RankMovementType.NEW
            previousRank == rank -> 0 to RankMovementType.UNCHANGED
            previousRank > rank -> (previousRank - rank) to RankMovementType.UP
            else -> (rank - previousRank) to RankMovementType.DOWN
        }
        entry.copy(rank = rank, previousRank = previousRank, rankDelta = delta, rankMovementType = movement)
    }
}
