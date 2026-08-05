package com.example.climb.leaderboard.scoring

import com.example.climb.leaderboard.model.ClimbAttempt

/**
 * Orders completed attempts on the SAME problem from best to worst: flash beats a second-attempt
 * send beats any other normal send; within a tier, fewer attempts is better; final tie-break is
 * an earlier successful result. [selectBestResult] picks the minimum under this ordering.
 */
private val bestResultComparator: Comparator<ClimbAttempt> = compareBy(
    { if (it.isFlash) 0 else 1 },
    { if (!it.isFlash && it.attemptNumber == 2) 0 else 1 },
    { it.attemptNumber },
    { it.attemptedAt },
)

fun selectBestResult(attemptsOnSameProblem: List<ClimbAttempt>): ClimbAttempt? =
    attemptsOnSameProblem.filter { it.completed }.minWithOrNull(bestResultComparator)

/**
 * Each problem counts once per user per period: groups by [ClimbAttempt.problemId] and keeps only
 * the single best completed attempt per problem — this is what prevents repeated sends of the
 * same problem from repeatedly adding points.
 */
fun bestSendsByProblem(attempts: List<ClimbAttempt>): Map<String, ClimbAttempt> =
    attempts.groupBy { it.problemId }
        .mapNotNull { (problemId, group) -> selectBestResult(group)?.let { problemId to it } }
        .toMap()
