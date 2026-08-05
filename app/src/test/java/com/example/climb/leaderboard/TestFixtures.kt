package com.example.climb.leaderboard

import com.example.climb.leaderboard.model.ClimbAttempt
import com.example.climb.leaderboard.model.ClimbingSession
import com.example.climb.leaderboard.model.VGrade

fun attempt(
    problemId: String,
    grade: Int?,
    attemptNumber: Int,
    completed: Boolean,
    isFlash: Boolean = false,
    attemptedAt: Long = 0L,
    userId: String = "user1",
    sessionId: String = "session1",
    videoId: String? = null,
): ClimbAttempt = ClimbAttempt(
    id = "${problemId}_a$attemptNumber",
    userId = userId,
    problemId = problemId,
    sessionId = sessionId,
    attemptedAt = attemptedAt,
    vGrade = grade?.let { VGrade(it) },
    attemptNumber = attemptNumber,
    completed = completed,
    isFlash = isFlash,
    videoId = videoId,
)

fun session(
    id: String,
    startedAt: Long,
    endedAt: Long,
    attemptCount: Int,
    completedProblemCount: Int,
    activityDurationMs: Long? = null,
    userId: String = "user1",
): ClimbingSession = ClimbingSession(
    id = id,
    userId = userId,
    startedAt = startedAt,
    endedAt = endedAt,
    attemptCount = attemptCount,
    completedProblemCount = completedProblemCount,
    activityDurationMs = activityDurationMs ?: (endedAt - startedAt),
)
