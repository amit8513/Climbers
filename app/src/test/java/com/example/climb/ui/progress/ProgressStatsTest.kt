package com.example.climb.ui.progress

import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun climb(vGrade: Int?, outcome: ClimbOutcome) = ClimbEntity(
    userId = "u1", videoPath = "path", createdAt = 0L, durationMs = 0L,
    vGrade = vGrade, routeColor = RouteColor.RED, outcome = outcome, notes = "",
)

class ProgressStatsTest {

    @Test
    fun `averages only sent climbs, ignoring fells and projects`() {
        val climbs = listOf(
            climb(vGrade = 4, outcome = ClimbOutcome.SENT),
            climb(vGrade = 6, outcome = ClimbOutcome.SENT),
            climb(vGrade = 10, outcome = ClimbOutcome.FELL),
            climb(vGrade = 1, outcome = ClimbOutcome.PROJECT),
        )
        assertEquals(5.0, averageSentGrade(climbs)!!, 0.001)
    }

    @Test
    fun `ignores ungraded sends rather than treating them as zero`() {
        val climbs = listOf(
            climb(vGrade = 4, outcome = ClimbOutcome.SENT),
            climb(vGrade = null, outcome = ClimbOutcome.SENT),
        )
        assertEquals(4.0, averageSentGrade(climbs)!!, 0.001)
    }

    @Test
    fun `returns null rather than a fabricated grade when there are no sends`() {
        val climbs = listOf(climb(vGrade = 5, outcome = ClimbOutcome.FELL))
        assertNull(averageSentGrade(climbs))
    }

    @Test
    fun `returns null for an empty climb list`() {
        assertNull(averageSentGrade(emptyList()))
    }
}
