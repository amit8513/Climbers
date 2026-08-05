package com.example.climb.leaderboard

import com.example.climb.leaderboard.scoring.bestSendsByProblem
import com.example.climb.leaderboard.scoring.computeConsistencyScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyScoringTest {

    @Test
    fun `consistency rate uses unique attempted problems, not total individual attempts`() {
        // One problem attempted five times (and eventually sent) should not inflate the denominator.
        val attempts = listOf(
            attempt("p1", grade = 4, attemptNumber = 1, completed = false),
            attempt("p1", grade = 4, attemptNumber = 2, completed = false),
            attempt("p1", grade = 4, attemptNumber = 3, completed = false),
            attempt("p1", grade = 4, attemptNumber = 4, completed = false),
            attempt("p1", grade = 4, attemptNumber = 5, completed = true),
        )
        val score = computeConsistencyScore(attempts, bestSendsByProblem(attempts))
        assertEquals(1, score.uniqueProblemsAttempted)
        assertEquals(1, score.uniqueProblemsSent)
        assertEquals(1f, score.consistencyRate, 0.0001f)
    }

    @Test
    fun `consistency rate is sent over attempted unique problems`() {
        val attempts = (1..10).map { i -> attempt("p$i", grade = 4, attemptNumber = 1, completed = i <= 8) }
        val score = computeConsistencyScore(attempts, bestSendsByProblem(attempts))
        assertEquals(10, score.uniqueProblemsAttempted)
        assertEquals(8, score.uniqueProblemsSent)
        assertEquals(0.8f, score.consistencyRate, 0.0001f)
    }

    @Test
    fun `qualification requires at least five unique attempted problems`() {
        val fourProblems = (1..4).map { i -> attempt("p$i", grade = 4, attemptNumber = 1, completed = true) }
        val fiveProblems = (1..5).map { i -> attempt("p$i", grade = 4, attemptNumber = 1, completed = true) }
        assertFalse(computeConsistencyScore(fourProblems, bestSendsByProblem(fourProblems)).qualifies)
        assertTrue(computeConsistencyScore(fiveProblems, bestSendsByProblem(fiveProblems)).qualifies)
    }

    @Test
    fun `no attempts produces a zero rate, not a division error`() {
        val score = computeConsistencyScore(emptyList(), emptyMap())
        assertEquals(0f, score.consistencyRate, 0.0001f)
        assertFalse(score.qualifies)
    }
}
