package com.example.climb.coaching

enum class CoachingSource { DETERMINISTIC, AI_REWORDED }

data class CoachingTip(
    val id: String,
    val category: String,
    val title: String,
    val explanation: String,
    /** Null for tips (mainly positive observations) that aren't tied to a specific practice drill. */
    val drill: String?,
    /** Null when the tip isn't about one specific moment (e.g. an overall pause-ratio observation). */
    val timestampMs: Long?,
    val confidence: Float,
    /** 0 = positive observation, 1 = top-priority improvement, 2 = secondary improvement. */
    val priority: Int,
    val evidence: String,
    val source: CoachingSource,
)

/** Placeholder for real cross-session history (previous attempts on this route/grade) — not
 * wired to real data yet, so the engine currently only ever receives null. Kept as a real
 * parameter rather than removed so the "improvement indicator" rule has somewhere to plug in
 * later without changing the engine's signature. */
data class UserClimbingHistory(val previousAttemptCount: Int = 0)
