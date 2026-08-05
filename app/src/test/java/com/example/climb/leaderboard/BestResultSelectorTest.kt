package com.example.climb.leaderboard

import com.example.climb.leaderboard.scoring.bestSendsByProblem
import com.example.climb.leaderboard.scoring.selectBestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BestResultSelectorTest {

    @Test
    fun `repeated sends of the same problem count only once`() {
        val attempts = listOf(
            attempt("p1", grade = 4, attemptNumber = 1, completed = true, attemptedAt = 1_000),
            attempt("p1", grade = 4, attemptNumber = 1, completed = true, attemptedAt = 2_000),
            attempt("p1", grade = 4, attemptNumber = 1, completed = true, attemptedAt = 3_000),
        )
        val bestSends = bestSendsByProblem(attempts)
        assertEquals(1, bestSends.size)
    }

    @Test
    fun `flash beats a second-attempt send`() {
        val flash = attempt("p1", grade = 4, attemptNumber = 1, completed = true, isFlash = true, attemptedAt = 1_000)
        val secondAttempt = attempt("p1", grade = 4, attemptNumber = 2, completed = true, attemptedAt = 500)
        val best = selectBestResult(listOf(flash, secondAttempt))
        assertEquals(flash, best)
    }

    @Test
    fun `second-attempt send beats a normal send`() {
        val secondAttempt = attempt("p1", grade = 4, attemptNumber = 2, completed = true, attemptedAt = 1_000)
        val normal = attempt("p1", grade = 4, attemptNumber = 5, completed = true, attemptedAt = 500)
        val best = selectBestResult(listOf(secondAttempt, normal))
        assertEquals(secondAttempt, best)
    }

    @Test
    fun `among equal-tier sends fewer attempts wins`() {
        val threeAttempts = attempt("p1", grade = 4, attemptNumber = 3, completed = true, attemptedAt = 1_000)
        val fiveAttempts = attempt("p1", grade = 4, attemptNumber = 5, completed = true, attemptedAt = 500)
        val best = selectBestResult(listOf(fiveAttempts, threeAttempts))
        assertEquals(threeAttempts, best)
    }

    @Test
    fun `final tie-break is the earlier successful result`() {
        val earlier = attempt("p1", grade = 4, attemptNumber = 3, completed = true, attemptedAt = 500)
        val later = attempt("p1", grade = 4, attemptNumber = 3, completed = true, attemptedAt = 1_000)
        val best = selectBestResult(listOf(later, earlier))
        assertEquals(earlier, best)
    }

    @Test
    fun `a problem with no completed attempt contributes no send`() {
        val attempts = listOf(
            attempt("p1", grade = 4, attemptNumber = 1, completed = false, attemptedAt = 1_000),
            attempt("p1", grade = 4, attemptNumber = 2, completed = false, attemptedAt = 2_000),
        )
        assertNull(selectBestResult(attempts))
        assertEquals(0, bestSendsByProblem(attempts).size)
    }
}
