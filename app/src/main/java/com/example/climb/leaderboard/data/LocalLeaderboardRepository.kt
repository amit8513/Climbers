package com.example.climb.leaderboard.data

import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.data.social.SocialRepository
import com.example.climb.leaderboard.model.ClimbAttempt
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.model.LeaderboardPeriod
import com.example.climb.leaderboard.model.LeaderboardPrivacySettings
import com.example.climb.leaderboard.model.LeaderboardResult
import com.example.climb.leaderboard.model.PeriodStatus
import com.example.climb.leaderboard.model.VGrade
import com.example.climb.leaderboard.privacy.LeaderboardPrivacyFilter
import com.example.climb.leaderboard.scoring.calculateEntry
import com.example.climb.leaderboard.scoring.rankEntries
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The only [LeaderboardRepository] implementation. Compares the signed-in user only against
 * their real accepted friends ([SocialRepository]) — no mock/demo roster. There is, however, no
 * backend yet that syncs a friend's climb data to where this device can read it (Firebase only
 * stores the friend graph, not climbs/sessions/videos — see LEADERBOARD.md), so every real friend
 * currently has zero visible activity from this device's point of view and shows up under
 * [LeaderboardResult.unrankedFriends] rather than in the ranked competition. The signed-in user's
 * own row is real, computed from their actual local climbs, through the exact same scoring
 * pipeline a real friend's data would go through once sync exists.
 */
class LocalLeaderboardRepository(
    private val climbRepository: ClimbRepository,
    private val socialRepository: SocialRepository,
    private val currentUid: String,
    private val currentDisplayName: String,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : LeaderboardRepository {

    private val cache = LeaderboardCache()

    /** Placeholder until real per-friend leaderboard privacy settings are stored anywhere —
     * defaults to "on" for every accepted friend, same as a brand new user would see today. */
    private val defaultFriendPrivacy = LeaderboardPrivacySettings(
        participateInLeaderboard = true,
        allowFriendsToViewStats = true,
        defaultVideoVisibility = Visibility.FRIENDS_ONLY,
    )

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

        val friends = socialRepository.observeFriends(currentUid).first()

        val currentUserRaw = currentUserEntry(period)
        val friendEntries = friends.map { friend ->
            calculateEntry(friend.uid, friend.username, null, emptyList(), emptyList(), zoneId).let { raw ->
                LeaderboardPrivacyFilter.filterForViewer(raw, viewerUserId, defaultFriendPrivacy, areFriends = true, totalOwnedVideoCount = 0) ?: raw
            }
        }
        val (eligibleFriends, unrankedFriends) = friendEntries.partition { it.isEligible }
        val rankable = eligibleFriends + (if (currentUserRaw.isEligible) listOf(currentUserRaw) else emptyList())

        val previousCurrentUserRaw = currentUserEntry(previousPeriod)
        val previousRankable = (if (previousCurrentUserRaw.isEligible) listOf(previousCurrentUserRaw) else emptyList())
        val previousRanks = rankEntries(previousRankable, category, emptyMap()).associate { it.userId to it.rank }

        val ranked = rankEntries(rankable, category, previousRanks)
        val currentUserEntry = ranked.find { it.userId == currentUid } ?: currentUserRaw

        return LeaderboardResult(
            period = period,
            category = category,
            generatedAt = System.currentTimeMillis(),
            entries = ranked,
            currentUserEntry = currentUserEntry,
            unrankedFriends = unrankedFriends,
        )
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
