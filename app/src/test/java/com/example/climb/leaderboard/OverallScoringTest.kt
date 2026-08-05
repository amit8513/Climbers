package com.example.climb.leaderboard

import com.example.climb.leaderboard.model.VGrade
import com.example.climb.leaderboard.scoring.computeOverallScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverallScoringTest {

    private fun send(problemId: String, grade: Int) = attempt(problemId, grade = grade, attemptNumber = 1, completed = true)

    @Test
    fun `only the five highest unique send scores count toward the base score`() {
        // Grades 0..5 -> points 10..60; the five highest (20,30,40,50,60) should sum to 200, dropping the V0 send.
        val sends = (0..5).map { send("p$it", it) }
        val result = computeOverallScore(sends, consistencyRate = 0f, qualitySessionCount = 0)
        assertEquals(200.0, result.baseSendScore, 0.0001)
    }

    @Test
    fun `consistency bonus is 20 percent of base score at full consistency`() {
        val sends = listOf(send("p1", 4)) // 50 points
        val result = computeOverallScore(sends, consistencyRate = 1f, qualitySessionCount = 0)
        assertEquals(10.0, result.consistencyBonus, 0.0001) // 50 * 1.0 * 0.20
    }

    @Test
    fun `consistency bonus never exceeds 20 percent of base score even if the rate is clamped above 1`() {
        val sends = listOf(send("p1", 4))
        val result = computeOverallScore(sends, consistencyRate = 3f, qualitySessionCount = 0)
        assertEquals(10.0, result.consistencyBonus, 0.0001)
    }

    @Test
    fun `session bonus is 10 points per quality session`() {
        val result = computeOverallScore(emptyList(), consistencyRate = 0f, qualitySessionCount = 3)
        assertEquals(30.0, result.sessionBonus, 0.0001)
    }

    @Test
    fun `session bonus is capped at 50 points regardless of how many quality sessions`() {
        val result = computeOverallScore(emptyList(), consistencyRate = 0f, qualitySessionCount = 12)
        assertEquals(50.0, result.sessionBonus, 0.0001)
        assertTrue(result.sessionBonus <= 50.0)
    }

    @Test
    fun `overall score sums base send score, consistency bonus and session bonus, rounded once`() {
        val sends = listOf(send("p1", 4)) // 50 points
        val result = computeOverallScore(sends, consistencyRate = 1f, qualitySessionCount = 5)
        // 50 base + 10 consistency (20% of 50) + 50 session (5 * 10) = 110
        assertEquals(110, result.overallScore)
    }
}
