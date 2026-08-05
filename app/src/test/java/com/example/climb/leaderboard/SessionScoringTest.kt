package com.example.climb.leaderboard

import com.example.climb.leaderboard.scoring.computeSessionScore
import com.example.climb.leaderboard.scoring.isQualitySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SessionScoringTest {

    private val zone = ZoneId.of("UTC")
    private fun dayStart(daysFromEpoch: Long): Long = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, zone).plusDays(daysFromEpoch).toInstant().toEpochMilli()

    @Test
    fun `a session qualifies with three attempts and one completion`() {
        val qualifying = session("s1", dayStart(0), dayStart(0) + 60_000, attemptCount = 3, completedProblemCount = 1)
        assertTrue(isQualitySession(qualifying))
    }

    @Test
    fun `a session with attempts but no completion does not qualify on the attempt path`() {
        val notQualifying = session("s1", dayStart(0), dayStart(0) + 60_000, attemptCount = 5, completedProblemCount = 0, activityDurationMs = 60_000)
        assertFalse(isQualitySession(notQualifying))
    }

    @Test
    fun `a session qualifies via 20 minutes of activity even with few attempts`() {
        val qualifying = session("s1", dayStart(0), dayStart(0) + 25 * 60_000, attemptCount = 1, completedProblemCount = 0, activityDurationMs = 25 * 60_000L)
        assertTrue(isQualitySession(qualifying))
    }

    @Test
    fun `opening the app briefly with no real activity does not qualify`() {
        val notQualifying = session("s1", dayStart(0), dayStart(0) + 5_000, attemptCount = 0, completedProblemCount = 0, activityDurationMs = 5_000)
        assertFalse(isQualitySession(notQualifying))
    }

    @Test
    fun `active days counts distinct calendar days, not session count`() {
        val sessions = listOf(
            session("s1", dayStart(0), dayStart(0) + 60_000, attemptCount = 3, completedProblemCount = 1),
            session("s2", dayStart(0) + 2 * 3_600_000, dayStart(0) + 2 * 3_600_000 + 60_000, attemptCount = 3, completedProblemCount = 1),
            session("s3", dayStart(2), dayStart(2) + 60_000, attemptCount = 3, completedProblemCount = 1),
        )
        val score = computeSessionScore(sessions, uniqueSends = 4, zoneId = zone)
        assertEquals(2, score.activeDays)
    }

    @Test
    fun `current streak counts consecutive active days ending at the most recent`() {
        val sessions = listOf(
            session("s1", dayStart(0), dayStart(0) + 60_000, attemptCount = 3, completedProblemCount = 1),
            session("s2", dayStart(1), dayStart(1) + 60_000, attemptCount = 3, completedProblemCount = 1),
            session("s3", dayStart(2), dayStart(2) + 60_000, attemptCount = 3, completedProblemCount = 1),
        )
        val score = computeSessionScore(sessions, uniqueSends = 6, zoneId = zone)
        assertEquals(3, score.currentStreak)
    }

    @Test
    fun `a gap in days breaks the streak`() {
        val sessions = listOf(
            session("s1", dayStart(0), dayStart(0) + 60_000, attemptCount = 3, completedProblemCount = 1),
            session("s2", dayStart(3), dayStart(3) + 60_000, attemptCount = 3, completedProblemCount = 1),
        )
        val score = computeSessionScore(sessions, uniqueSends = 2, zoneId = zone)
        assertEquals(1, score.currentStreak)
    }
}
