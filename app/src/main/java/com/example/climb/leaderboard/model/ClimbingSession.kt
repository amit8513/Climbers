package com.example.climb.leaderboard.model

data class ClimbingSession(
    val id: String,
    val userId: String,
    val startedAt: Long,
    val endedAt: Long,
    val attemptCount: Int,
    val completedProblemCount: Int,
    /** Null when duration isn't known — the quality-session rule falls back to the attempt-count path. */
    val activityDurationMs: Long?,
)
