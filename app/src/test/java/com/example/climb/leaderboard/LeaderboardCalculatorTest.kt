package com.example.climb.leaderboard

import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.RankMovementType
import com.example.climb.leaderboard.scoring.calculateEntry
import com.example.climb.leaderboard.scoring.rankEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class LeaderboardCalculatorTest {

    private val zone = ZoneId.of("UTC")

    private fun entryOf(userId: String, attempts: List<com.example.climb.leaderboard.model.ClimbAttempt>, sessions: List<com.example.climb.leaderboard.model.ClimbingSession> = emptyList()) =
        calculateEntry(userId, userId, null, attempts, sessions, zone)

    @Test
    fun `a user with no attempts and no sessions is not eligible`() {
        val entry = entryOf("solo", emptyList())
        assertFalse(entry.isEligible)
        assertEquals("No activity this week", entry.eligibilityReason)
        assertEquals(RankMovementType.UNRANKED, entry.rankMovementType)
        assertEquals(0, entry.rank)
    }

    @Test
    fun `overall ranking orders by overall score first`() {
        val strong = entryOf("a", listOf(attempt("p1", grade = 8, attemptNumber = 1, completed = true, userId = "a")))
        val weak = entryOf("b", listOf(attempt("p1", grade = 1, attemptNumber = 1, completed = true, userId = "b")))
        val ranked = rankEntries(listOf(weak, strong), LeaderboardCategory.OVERALL, emptyMap())
        assertEquals("a", ranked[0].userId)
        assertEquals(1, ranked[0].rank)
        assertEquals("b", ranked[1].userId)
        assertEquals(2, ranked[1].rank)
    }

    @Test
    fun `v grade ranking breaks a tie in highest grade using fewer attempts on the hardest send`() {
        val fewerAttempts = entryOf("a", listOf(attempt("p1", grade = 6, attemptNumber = 1, completed = true, userId = "a")))
        val moreAttempts = entryOf("b", listOf(attempt("p1", grade = 6, attemptNumber = 4, completed = true, userId = "b")))
        val ranked = rankEntries(listOf(moreAttempts, fewerAttempts), LeaderboardCategory.V_GRADE, emptyMap())
        assertEquals("a", ranked[0].userId)
    }

    @Test
    fun `consistency ranking orders by consistency rate first`() {
        val consistent = entryOf(
            "a",
            (1..5).map { i -> attempt("p$i", grade = 4, attemptNumber = 1, completed = true, userId = "a") },
        )
        val inconsistent = entryOf(
            "b",
            (1..5).map { i -> attempt("p$i", grade = 4, attemptNumber = 1, completed = i == 1, userId = "b") },
        )
        val ranked = rankEntries(listOf(inconsistent, consistent), LeaderboardCategory.CONSISTENCY, emptyMap())
        assertEquals("a", ranked[0].userId)
    }

    @Test
    fun `sessions ranking orders by active days before quality session count`() {
        val moreDays = entryOf(
            "a",
            emptyList(),
            listOf(
                session("s1", 0L, 60_000L, attemptCount = 3, completedProblemCount = 1, userId = "a"),
                session("s2", 86_400_000L, 86_460_000L, attemptCount = 3, completedProblemCount = 1, userId = "a"),
            ),
        )
        val moreSessionsSameDay = entryOf(
            "b",
            emptyList(),
            listOf(
                session("s1", 0L, 60_000L, attemptCount = 3, completedProblemCount = 1, userId = "b"),
                session("s2", 3_600_000L, 3_660_000L, attemptCount = 3, completedProblemCount = 1, userId = "b"),
                session("s3", 7_200_000L, 7_260_000L, attemptCount = 3, completedProblemCount = 1, userId = "b"),
            ),
        )
        val ranked = rankEntries(listOf(moreSessionsSameDay, moreDays), LeaderboardCategory.SESSIONS, emptyMap())
        assertEquals("a", ranked[0].userId)
    }

    @Test
    fun `sends ranking orders by weighted send score`() {
        val higherScore = entryOf("a", listOf(attempt("p1", grade = 7, attemptNumber = 1, completed = true, userId = "a")))
        val lowerScore = entryOf("b", listOf(attempt("p1", grade = 2, attemptNumber = 1, completed = true, userId = "b")))
        val ranked = rankEntries(listOf(lowerScore, higherScore), LeaderboardCategory.SENDS, emptyMap())
        assertEquals("a", ranked[0].userId)
    }

    @Test
    fun `rank movement is computed relative to the previous period's ranks`() {
        val a = entryOf("a", listOf(attempt("p1", grade = 8, attemptNumber = 1, completed = true, userId = "a")))
        val b = entryOf("b", listOf(attempt("p1", grade = 6, attemptNumber = 1, completed = true, userId = "b")))
        val c = entryOf("c", listOf(attempt("p1", grade = 4, attemptNumber = 1, completed = true, userId = "c")))
        // Previously: a=2, b=1, c absent -> now a=1 (up 1), b=2 (down 1), c=new.
        val previousRanks = mapOf("a" to 2, "b" to 1)
        val ranked = rankEntries(listOf(a, b, c), LeaderboardCategory.OVERALL, previousRanks)

        val rankedA = ranked.first { it.userId == "a" }
        val rankedB = ranked.first { it.userId == "b" }
        val rankedC = ranked.first { it.userId == "c" }

        assertEquals(RankMovementType.UP, rankedA.rankMovementType)
        assertEquals(1, rankedA.rankDelta)
        assertEquals(RankMovementType.DOWN, rankedB.rankMovementType)
        assertEquals(1, rankedB.rankDelta)
        assertEquals(RankMovementType.NEW, rankedC.rankMovementType)
        assertNull(rankedC.previousRank)
    }

    @Test
    fun `unchanged rank has zero delta`() {
        val a = entryOf("a", listOf(attempt("p1", grade = 8, attemptNumber = 1, completed = true, userId = "a")))
        val ranked = rankEntries(listOf(a), LeaderboardCategory.OVERALL, mapOf("a" to 1))
        val rankedA = ranked.first()
        assertEquals(RankMovementType.UNCHANGED, rankedA.rankMovementType)
        assertEquals(0, rankedA.rankDelta)
    }
}
