package com.example.climb.colordetection

import com.example.climb.clubs.HoldRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldGeometryJsonTest {

    @Test
    fun `an empty list round-trips to an empty list`() {
        assertEquals(emptyList<ReviewedHold>(), emptyList<ReviewedHold>().toHoldGeometryJson().toReviewedHolds())
    }

    @Test
    fun `a populated hold list round-trips exactly`() {
        val holds = listOf(
            ReviewedHold(id = 1, centroidNormalized = Point2D(0.5f, 0.9f), role = HoldRole.START),
            ReviewedHold(id = 2, centroidNormalized = Point2D(0.52f, 0.5f), role = HoldRole.BODY),
            ReviewedHold(id = 3, centroidNormalized = Point2D(0.48f, 0.1f), role = HoldRole.FINISH),
        )

        val roundTripped = holds.toHoldGeometryJson().toReviewedHolds()

        assertEquals(holds, roundTripped)
    }

    @Test
    fun `blank input decodes to an empty list`() {
        assertEquals(emptyList<ReviewedHold>(), "".toReviewedHolds())
    }

    @Test
    fun `corrupt input decodes to an empty list rather than throwing`() {
        val result = "{not valid json".toReviewedHolds()
        assertTrue(result.isEmpty())
    }
}
