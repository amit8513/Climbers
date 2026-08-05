package com.example.climb.leaderboard.data

import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.leaderboard.model.ClimbAttempt
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.model.LeaderboardPeriod
import com.example.climb.leaderboard.model.LeaderboardResult
import com.example.climb.leaderboard.model.PeriodStatus
import com.example.climb.leaderboard.model.VGrade
import com.example.climb.leaderboard.privacy.LeaderboardPrivacyFilter
import com.example.climb.leaderboard.scoring.calculateEntry
import com.example.climb.leaderboard.scoring.rankEntries
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The only [LeaderboardRepository] implementation today — there is no backend that owns real
 * friends' climb data yet. Friends are realistic mock fixtures ([demoProfiles]); the signed-in
 * user's own row is computed from their real local climbs via [ClimbRepository]. Both paths run
 * through the same [calculateEntry]/[rankEntries]/[LeaderboardPrivacyFilter] pipeline, so "you"
 * are ranked for real, not overlaid decoratively. See LEADERBOARD.md for what must move
 * server-side once real cross-friend sync exists.
 */
class MockLeaderboardRepository(
    private val climbRepository: ClimbRepository,
    private val currentUid: String,
    private val currentDisplayName: String,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : LeaderboardRepository {

    private val cache = LeaderboardCache()

    override suspend fun getLeaderboard(viewerUserId: String, category: LeaderboardCategory, period: LeaderboardPeriod): LeaderboardResult {
        cache.get(category, period.id)?.let { (result, _) -> return result }
        return refreshLeaderboard(viewerUserId, category, period)
    }

    override suspend fun refreshLeaderboard(viewerUserId: String, category: LeaderboardCategory, period: LeaderboardPeriod): LeaderboardResult {
        val result = buildResult(viewerUserId, category, period)
        cache.put(category, period.id, result, System.currentTimeMillis())
        return result
    }

    override fun lastUpdatedAt(category: LeaderboardCategory, period: LeaderboardPeriod): Long? = cache.get(category, period.id)?.second

    private suspend fun buildResult(viewerUserId: String, category: LeaderboardCategory, period: LeaderboardPeriod): LeaderboardResult {
        val periodSpanMs = (period.endAt - period.startAt).coerceAtLeast(1L)
        val previousPeriod = period.copy(
            id = "${period.id}_prev",
            startAt = period.startAt - periodSpanMs,
            endAt = period.startAt,
            label = "Previous",
            status = PeriodStatus.COMPLETE,
        )

        val currentUserRaw = currentUserEntry(period)
        val rankable = demoEntries(period, viewerUserId, isPreviousPeriod = false) +
            (if (currentUserRaw.isEligible) listOf(currentUserRaw) else emptyList())

        val previousCurrentUserRaw = currentUserEntry(previousPeriod)
        val previousRankable = demoEntries(previousPeriod, viewerUserId, isPreviousPeriod = true) +
            (if (previousCurrentUserRaw.isEligible) listOf(previousCurrentUserRaw) else emptyList())
        val previousRanks = rankEntries(previousRankable, category, emptyMap()).associate { it.userId to it.rank }

        val ranked = rankEntries(rankable, category, previousRanks)
        val currentUserEntry = ranked.find { it.userId == currentUid } ?: currentUserRaw

        return LeaderboardResult(
            period = period,
            category = category,
            generatedAt = System.currentTimeMillis(),
            entries = ranked,
            currentUserEntry = currentUserEntry,
        )
    }

    private fun demoEntries(period: LeaderboardPeriod, viewerUserId: String, isPreviousPeriod: Boolean): List<LeaderboardEntry> =
        demoProfiles(currentUid).mapNotNull { profile ->
            val seed = seedFor(profile.uid, period.id)
            val (attempts, sessions) = generateAttemptsAndSessions(profile, period, seed, isPreviousPeriod)
            val rawEntry = calculateEntry(profile.uid, profile.displayName, null, attempts, sessions, zoneId)
            if (!rawEntry.isEligible) return@mapNotNull null
            val ownedVideoCount = attempts.count { it.videoId != null }
            LeaderboardPrivacyFilter.filterForViewer(rawEntry, viewerUserId, profile.privacy, areFriends = true, totalOwnedVideoCount = ownedVideoCount)
        }

    private suspend fun currentUserEntry(period: LeaderboardPeriod): LeaderboardEntry {
        val attempts = realUserAttempts(period)
        return calculateEntry(currentUid, currentDisplayName, null, attempts, emptyList(), zoneId, isCurrentUser = true)
    }

    /**
     * Best-effort mapping from the real local climb log into the scoring engine's input shape.
     * [com.example.climb.data.ClimbEntity] has no attempt history or shared problem identity yet
     * (see LEADERBOARD.md), so each logged climb is treated as its own unique "problem" with a
     * single attempt — real once climb tracking grows attempt/problem identity.
     */
    private suspend fun realUserAttempts(period: LeaderboardPeriod): List<ClimbAttempt> =
        climbRepository.observeAll(currentUid).first()
            .filter { it.createdAt in period.startAt until period.endAt }
            .map { climb ->
                ClimbAttempt(
                    id = "climb_${climb.id}",
                    userId = currentUid,
                    problemId = "climb_${climb.id}",
                    sessionId = "climb_${climb.id}_session",
                    attemptedAt = climb.createdAt,
                    vGrade = climb.vGrade?.let { VGrade(it) },
                    attemptNumber = 1,
                    completed = climb.outcome == ClimbOutcome.SENT,
                    isFlash = false,
                    videoId = climb.videoPath,
                )
            }
}
