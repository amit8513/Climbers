package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbingSession
import java.time.Instant
import java.time.ZoneId

const val QUALITY_SESSION_MIN_ATTEMPTS = 3
const val QUALITY_SESSION_MIN_COMPLETED = 1
const val QUALITY_SESSION_MIN_DURATION_MS = 20L * 60L * 1000L

/** A quality session needs either enough logged attempts with at least one completion, or (when
 * duration is tracked) at least 20 minutes of activity — repeatedly opening/closing the app
 * without climbing satisfies neither. */
fun isQualitySession(session: ClimbingSession): Boolean =
    (session.attemptCount >= QUALITY_SESSION_MIN_ATTEMPTS && session.completedProblemCount >= QUALITY_SESSION_MIN_COMPLETED) ||
        (session.activityDurationMs != null && session.activityDurationMs >= QUALITY_SESSION_MIN_DURATION_MS)

data class SessionScoreResult(
    val activeDays: Int,
    val qualitySessionCount: Int,
    val currentStreak: Int,
    val averageSendsPerSession: Float,
    val mostRecentQualitySessionAt: Long?,
)

fun computeSessionScore(sessions: List<ClimbingSession>, uniqueSends: Int, zoneId: ZoneId): SessionScoreResult {
    val activeDates = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(zoneId).toLocalDate() }.toSortedSet()

    var streak = 0
    if (activeDates.isNotEmpty()) {
        val descending = activeDates.sortedDescending()
        streak = 1
        for (i in 1 until descending.size) {
            if (descending[i - 1].minusDays(1) == descending[i]) streak++ else break
        }
    }

    return SessionScoreResult(
        activeDays = activeDates.size,
        qualitySessionCount = sessions.count { isQualitySession(it) },
        currentStreak = streak,
        averageSendsPerSession = if (sessions.isEmpty()) 0f else uniqueSends.toFloat() / sessions.size,
        mostRecentQualitySessionAt = sessions.filter { isQualitySession(it) }.maxOfOrNull { it.endedAt },
    )
}
