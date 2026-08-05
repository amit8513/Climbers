package com.example.climb.leaderboard.model

/**
 * The scoring engine's real input type — decoupled from [com.example.climb.data.ClimbEntity],
 * which today only logs one row per climb with no attempt history. Real data sources (mock or,
 * eventually, backend) map into this shape; the calculator never sees app-storage types.
 */
data class ClimbAttempt(
    val id: String,
    val userId: String,
    /** Stable problem/route identity — never derive uniqueness from a route name, which can collide. */
    val problemId: String,
    val sessionId: String,
    val attemptedAt: Long,
    /** Null for an ungraded/unknown climb — contributes no grade points anywhere. */
    val vGrade: VGrade?,
    val attemptNumber: Int,
    val completed: Boolean,
    val isFlash: Boolean,
    val videoId: String?,
)
