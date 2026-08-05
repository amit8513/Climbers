package com.example.climb.leaderboard

import com.example.climb.leaderboard.model.VGrade
import com.example.climb.leaderboard.scoring.computeVGradeScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VGradeScoringTest {

    @Test
    fun `highest grade is the max among unique sends`() {
        val sends = listOf(
            attempt("p1", grade = 3, attemptNumber = 1, completed = true),
            attempt("p2", grade = 7, attemptNumber = 1, completed = true),
            attempt("p3", grade = 5, attemptNumber = 1, completed = true),
        )
        val score = computeVGradeScore(sends)
        assertEquals(VGrade(7), score.highestVGrade)
    }

    @Test
    fun `top-three average uses up to three hardest sends and records the count used`() {
        val sends = listOf(
            attempt("p1", grade = 8, attemptNumber = 1, completed = true),
            attempt("p2", grade = 6, attemptNumber = 1, completed = true),
            attempt("p3", grade = 4, attemptNumber = 1, completed = true),
            attempt("p4", grade = 2, attemptNumber = 1, completed = true),
        )
        val score = computeVGradeScore(sends)
        assertEquals((8 + 6 + 4) / 3.0, score.topThreeAverageGrade!!, 0.0001)
        assertEquals(3, score.topThreeAverageSendCount)
    }

    @Test
    fun `top-three average falls back to fewer sends when fewer than three exist`() {
        val sends = listOf(attempt("p1", grade = 5, attemptNumber = 1, completed = true))
        val score = computeVGradeScore(sends)
        assertEquals(5.0, score.topThreeAverageGrade!!, 0.0001)
        assertEquals(1, score.topThreeAverageSendCount)
    }

    @Test
    fun `no sends produces null grade fields, not zero`() {
        val score = computeVGradeScore(emptyList())
        assertNull(score.highestVGrade)
        assertNull(score.topThreeAverageGrade)
    }

    @Test
    fun `hardest send attempt count picks fewest attempts among ties at the highest grade`() {
        val sends = listOf(
            attempt("p1", grade = 6, attemptNumber = 4, completed = true),
            attempt("p2", grade = 6, attemptNumber = 1, completed = true),
        )
        val score = computeVGradeScore(sends)
        assertEquals(1, score.hardestSendAttemptCount)
        assertEquals(2, score.uniqueSendsAtHighestGrade)
    }
}
