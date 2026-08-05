package com.example.climb.leaderboard.data

import com.example.climb.analysis.Visibility
import com.example.climb.leaderboard.model.ClimbAttempt
import com.example.climb.leaderboard.model.ClimbingSession
import com.example.climb.leaderboard.model.LeaderboardPeriod
import com.example.climb.leaderboard.model.LeaderboardPrivacySettings
import com.example.climb.leaderboard.model.VGrade
import kotlin.random.Random

/**
 * Development-only mock friends, kept behind [LeaderboardRepository] rather than hardcoded in the
 * UI. There's no backend sync of real friends' climb data yet (see LEADERBOARD.md), so this
 * roster stands in for it — deterministic per (user, period) so refetching never changes the
 * numbers, and covering every edge case the product spec calls out: rank movement in both
 * directions, a brand-new participant, private/friends-only/selected-friends video visibility,
 * and a participant below the Consistency qualification minimum.
 */
internal data class DemoProfile(
    val uid: String,
    val displayName: String,
    val baseUniqueProblems: Int,
    val baseHighestGrade: Int,
    val completionRate: Float,
    val flashRate: Float,
    val sessionsPerWeek: Int,
    val privacy: LeaderboardPrivacySettings,
    /** True for the one profile that should show as brand new this period — zero activity in
     * the previous comparable period, so it can't have a previous rank. */
    val newThisPeriod: Boolean = false,
)

internal fun demoProfiles(currentUid: String): List<DemoProfile> = listOf(
    DemoProfile(
        uid = "demo_alex", displayName = "Alex",
        baseUniqueProblems = 14, baseHighestGrade = 8, completionRate = 0.85f, flashRate = 0.3f, sessionsPerWeek = 5,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.FRIENDS_ONLY),
    ),
    DemoProfile(
        uid = "demo_maya", displayName = "Maya",
        baseUniqueProblems = 12, baseHighestGrade = 7, completionRate = 0.82f, flashRate = 0.25f, sessionsPerWeek = 4,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.SELECTED_FRIENDS, selectedViewerIds = setOf(currentUid)),
    ),
    DemoProfile(
        uid = "demo_jordan", displayName = "Jordan",
        baseUniqueProblems = 11, baseHighestGrade = 7, completionRate = 0.8f, flashRate = 0.2f, sessionsPerWeek = 5,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.PUBLIC),
    ),
    DemoProfile(
        uid = "demo_chris", displayName = "Chris",
        baseUniqueProblems = 9, baseHighestGrade = 6, completionRate = 0.7f, flashRate = 0.15f, sessionsPerWeek = 3,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.PRIVATE),
    ),
    DemoProfile(
        uid = "demo_sam", displayName = "Sam",
        baseUniqueProblems = 8, baseHighestGrade = 5, completionRate = 0.65f, flashRate = 0.1f, sessionsPerWeek = 3,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.FRIENDS_ONLY),
    ),
    DemoProfile(
        uid = "demo_taylor", displayName = "Taylor",
        baseUniqueProblems = 6, baseHighestGrade = 5, completionRate = 0.6f, flashRate = 0.1f, sessionsPerWeek = 2,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.FRIENDS_ONLY),
    ),
    DemoProfile(
        uid = "demo_riley", displayName = "Riley",
        baseUniqueProblems = 5, baseHighestGrade = 4, completionRate = 0.55f, flashRate = 0.05f, sessionsPerWeek = 2,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.PRIVATE),
        newThisPeriod = true,
    ),
    DemoProfile(
        uid = "demo_jamie", displayName = "Jamie",
        baseUniqueProblems = 3, baseHighestGrade = 3, completionRate = 0.5f, flashRate = 0f, sessionsPerWeek = 1,
        privacy = LeaderboardPrivacySettings(true, true, Visibility.FRIENDS_ONLY),
    ),
)

/** Stable per-(user, period) seed — same inputs always regenerate the same fixture data. */
internal fun seedFor(uid: String, periodId: String): Long = (uid + periodId).hashCode().toLong()

internal fun generateAttemptsAndSessions(
    profile: DemoProfile,
    period: LeaderboardPeriod,
    seed: Long,
    isPreviousPeriod: Boolean,
): Pair<List<ClimbAttempt>, List<ClimbingSession>> {
    if (profile.newThisPeriod && isPreviousPeriod) return emptyList<ClimbAttempt>() to emptyList()

    val random = Random(seed)
    val uniqueProblems = (profile.baseUniqueProblems + random.nextInt(-1, 2)).coerceAtLeast(1)
    val sessionCount = (profile.sessionsPerWeek + random.nextInt(-1, 2)).coerceAtLeast(1)
    val periodSpanMs = (period.endAt - period.startAt).coerceAtLeast(1L)
    val sessionStarts = (0 until sessionCount).map { period.startAt + random.nextLong(0, periodSpanMs) }.sorted()

    val attempts = mutableListOf<ClimbAttempt>()
    val sessions = mutableListOf<ClimbingSession>()
    var problemIndex = 0

    for ((sessionIndex, sessionStart) in sessionStarts.withIndex()) {
        val sessionId = "${profile.uid}_session_${period.id}_$sessionIndex"
        val problemsThisSession = (uniqueProblems / sessionCount) + if (sessionIndex < uniqueProblems % sessionCount) 1 else 0
        var sessionAttemptCount = 0
        var sessionCompletedCount = 0
        var cursor = sessionStart

        repeat(problemsThisSession) {
            if (problemIndex >= uniqueProblems) return@repeat
            val problemId = "${profile.uid}_problem_$problemIndex"
            problemIndex++

            val grade = (profile.baseHighestGrade - random.nextInt(0, 4)).coerceAtLeast(0)
            val willComplete = random.nextFloat() < profile.completionRate
            val isFlash = willComplete && random.nextFloat() < profile.flashRate
            val attemptCountForProblem = when {
                isFlash -> 1
                willComplete -> 1 + random.nextInt(0, 3)
                else -> 1 + random.nextInt(1, 4)
            }

            for (attemptNumber in 1..attemptCountForProblem) {
                val completed = willComplete && attemptNumber == attemptCountForProblem
                attempts += ClimbAttempt(
                    id = "${problemId}_attempt_$attemptNumber",
                    userId = profile.uid,
                    problemId = problemId,
                    sessionId = sessionId,
                    attemptedAt = cursor,
                    vGrade = VGrade(grade),
                    attemptNumber = attemptNumber,
                    completed = completed,
                    isFlash = completed && isFlash,
                    videoId = if (attemptNumber == attemptCountForProblem) "${problemId}_video" else null,
                )
                sessionAttemptCount++
                if (completed) sessionCompletedCount++
                cursor += 90_000L + random.nextLong(0, 120_000L)
            }
        }

        sessions += ClimbingSession(
            id = sessionId,
            userId = profile.uid,
            startedAt = sessionStart,
            endedAt = cursor,
            attemptCount = sessionAttemptCount,
            completedProblemCount = sessionCompletedCount,
            activityDurationMs = cursor - sessionStart,
        )
    }

    return attempts to sessions
}
