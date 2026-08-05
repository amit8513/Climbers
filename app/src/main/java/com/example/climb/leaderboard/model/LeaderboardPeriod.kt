package com.example.climb.leaderboard.model

enum class PeriodStatus { ACTIVE, COMPLETE, CALCULATING }

/**
 * A leaderboard week: Monday 00:00 to the following Monday 00:00 in [timezone]. [id] is a stable
 * key (ISO week, e.g. "2026-W32") so re-deriving "this week" never produces a duplicate period.
 * A real backend would persist these as rows with real [status] transitions; here they're
 * recomputed on demand from calendar time (see [com.example.climb.leaderboard.period.LeaderboardPeriodProvider]).
 */
data class LeaderboardPeriod(
    val id: String,
    val startAt: Long,
    val endAt: Long,
    val timezone: String,
    val label: String,
    val status: PeriodStatus,
    val createdAt: Long,
)
