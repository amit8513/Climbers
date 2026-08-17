package com.example.climb.leaderboard.data

import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.data.social.Friend
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
import com.example.climb.sharing.FriendClimbsRepository
import com.example.climb.sharing.SharedClimb
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * The only [LeaderboardRepository] implementation. Compares the signed-in user only against
 * their real accepted friends ([SocialRepository]), scored from their real synced climbs
 * ([FriendClimbsRepository] — [com.example.climb.sharing.ClimbSyncRepository] is what puts that
 * data there in the first place). A friend who hasn't shared any Friends-only/Public climbs for
 * a period still has nothing to rank on and shows up under [LeaderboardResult.unrankedFriends]
 * instead of in the ranked competition — that's real "no data," not a stand-in for missing sync.
 */
class LocalLeaderboardRepository(
    private val climbRepository: ClimbRepository,
    private val socialRepository: SocialRepository,
    private val friendClimbsRepository: FriendClimbsRepository,
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

        val friends = socialRepository.observeFriends(currentUid).first()

        val currentUserRaw = currentUserEntry(period)
        // mapNotNull, not map: a friend who has opted out of the leaderboard entirely (or turned
        // off stats sharing with this viewer) must be excluded outright, not merely hidden in the
        // UI — LeaderboardPrivacyFilter.filterForViewer returns null for exactly that case.
        val friendEntries = friends.mapNotNull { friendEntry(it, period, viewerUserId) }
        val (eligibleFriends, unrankedFriends) = friendEntries.partition { it.isEligible }
        val rankable = eligibleFriends + (if (currentUserRaw.isEligible) listOf(currentUserRaw) else emptyList())

        val previousCurrentUserRaw = currentUserEntry(previousPeriod)
        val previousFriendEntries = friends.mapNotNull { friendEntry(it, previousPeriod, viewerUserId) }
        val previousRankable = previousFriendEntries.filter { it.isEligible } +
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
            unrankedFriends = unrankedFriends,
        )
    }

    private suspend fun currentUserEntry(period: LeaderboardPeriod): LeaderboardEntry {
        val attempts = realUserAttempts(period)
        return calculateEntry(currentUid, currentDisplayName, null, attempts, emptyList(), zoneId, isCurrentUser = true)
    }

    /** Returns null when [friend] must not appear in this viewer's leaderboard at all — real,
     * per-friend [LeaderboardPrivacySettings] (participation, stats sharing, video visibility),
     * not a hardcoded default applied to everyone. */
    private suspend fun friendEntry(friend: Friend, period: LeaderboardPeriod, viewerUserId: String): LeaderboardEntry? {
        val attempts = friendAttempts(friend.uid, period)
        val raw = calculateEntry(friend.uid, friend.username, null, attempts, emptyList(), zoneId)
        val videoCount = attempts.count { it.videoId != null }
        val settings = socialRepository.getLeaderboardPrivacySettings(friend.uid)
        return LeaderboardPrivacyFilter.filterForViewer(raw, viewerUserId, settings, areFriends = true, totalOwnedVideoCount = videoCount)
    }

    /** The friend's climbs already passed `firestore.rules`' visibility check just to be
     * readable at all — this only maps what came back into the scoring engine's input shape. */
    private suspend fun friendAttempts(friendUid: String, period: LeaderboardPeriod): List<ClimbAttempt> =
        friendClimbsRepository.observeSharedClimbs(friendUid).first()
            .filter { it.createdAt in period.startAt until period.endAt }
            .map { it.toClimbAttempt() }

    private fun SharedClimb.toClimbAttempt(): ClimbAttempt = ClimbAttempt(
        id = id,
        userId = ownerUid,
        problemId = id,
        sessionId = "${id}_session",
        attemptedAt = createdAt,
        vGrade = vGrade?.let { VGrade(it) },
        attemptNumber = 1,
        completed = outcome == ClimbOutcome.SENT,
        isFlash = false,
        videoId = videoStoragePath,
    )

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
