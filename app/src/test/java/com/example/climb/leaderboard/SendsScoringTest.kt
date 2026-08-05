package com.example.climb.leaderboard

import com.example.climb.leaderboard.scoring.computeSendsScore
import org.junit.Assert.assertEquals
import org.junit.Test

class SendsScoringTest {

    @Test
    fun `weighted send score sums best send points for every unique problem, not just five`() {
        val sends = (0..7).map { attempt("p$it", grade = it, attemptNumber = 1, completed = true) }
        val score = computeSendsScore(sends)
        // gradePoints for V0..V7 = 10,20,...,80 -> sum = 360
        assertEquals(360, score.weightedSendScore)
        assertEquals(8, score.uniqueSends)
    }

    @Test
    fun `flash count reflects how many best-sends were flashes`() {
        val sends = listOf(
            attempt("p1", grade = 4, attemptNumber = 1, completed = true, isFlash = true),
            attempt("p2", grade = 3, attemptNumber = 2, completed = true, isFlash = false),
        )
        val score = computeSendsScore(sends)
        assertEquals(1, score.flashCount)
    }

    @Test
    fun `average attempts per send is the mean attempt number across unique sends`() {
        val sends = listOf(
            attempt("p1", grade = 4, attemptNumber = 2, completed = true),
            attempt("p2", grade = 3, attemptNumber = 4, completed = true),
        )
        val score = computeSendsScore(sends)
        assertEquals(3f, score.averageAttemptsPerSend, 0.0001f)
    }
}
