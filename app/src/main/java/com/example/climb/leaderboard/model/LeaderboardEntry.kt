package com.example.climb.leaderboard.model

/**
 * One row of a leaderboard, holding every category's metrics — a row needs its own category's
 * primary value plus supporting stats, and switching tabs re-sorts/re-ranks the same entries
 * rather than re-fetching, so all fields are always populated regardless of which category is
 * currently selected.
 */
data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,

    val rank: Int,
    val previousRank: Int?,
    val rankDelta: Int,
    val rankMovementType: RankMovementType,

    val overallScore: Int,
    /** Sum of attempt numbers across the sends actually counted toward [overallScore] (up to
     * five) — Overall's tie-break #5, "fewer total attempts across counted sends." */
    val totalAttemptsInScoredSends: Int,

    val highestVGrade: VGrade?,
    val topThreeAverageGrade: Double?,
    val topThreeAverageSendCount: Int,
    val hardestSendAttemptCount: Int,

    val consistencyRate: Float,
    val uniqueProblemsAttempted: Int,
    val uniqueProblemsSent: Int,
    val averageSentGrade: Double?,
    /** Every logged attempt in the period, regardless of problem or outcome — Consistency's
     * tie-break #4, "fewer total attempts." */
    val totalAttempts: Int,

    val activeDays: Int,
    val qualitySessionCount: Int,
    val currentStreak: Int,
    val averageSendsPerSession: Float,

    val weightedSendScore: Int,
    val flashCount: Int,
    /** Average attempt count per unique send — Sends' tie-break #5, "fewer attempts per send." */
    val averageAttemptsPerSend: Float,

    val sharedVideoCount: Int,
    val hasViewableVideo: Boolean,
    val hasPrivateVideo: Boolean,

    val isCurrentUser: Boolean,
    val isEligible: Boolean,
    val eligibilityReason: String?,

    /** Most recent qualifying-climb timestamp, used as the final tie-break in every category. */
    val mostRecentQualifyingAt: Long,
)

data class LeaderboardResult(
    val period: LeaderboardPeriod,
    val category: LeaderboardCategory,
    val generatedAt: Long,
    val entries: List<LeaderboardEntry>,
    val currentUserEntry: LeaderboardEntry?,
)
