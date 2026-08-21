package com.example.climb.ui.clubs.routeregistration

import com.example.climb.clubs.HoldRole
import com.example.climb.colordetection.Point2D
import com.example.climb.colordetection.ReviewedHold
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRegistrationHoldSelectionTest {

    private val holds = listOf(
        ReviewedHold(1, Point2D(0.5f, 0.9f), HoldRole.BODY),
        ReviewedHold(2, Point2D(0.5f, 0.5f), HoldRole.BODY),
        ReviewedHold(3, Point2D(0.5f, 0.1f), HoldRole.BODY),
    )

    @Test
    fun `selecting a start hold marks exactly that hold START and leaves others untouched`() {
        val result = RouteRegistrationHoldSelection.selectStartHold(holds, holdId = 1)

        assertEquals(HoldRole.START, result.first { it.id == 1 }.role)
        assertEquals(HoldRole.BODY, result.first { it.id == 2 }.role)
        assertEquals(HoldRole.BODY, result.first { it.id == 3 }.role)
    }

    @Test
    fun `re-selecting a different start hold demotes the previous one, never leaving two starts`() {
        val afterFirst = RouteRegistrationHoldSelection.selectStartHold(holds, holdId = 1)
        val afterSecond = RouteRegistrationHoldSelection.selectStartHold(afterFirst, holdId = 2)

        assertEquals(1, afterSecond.count { it.role == HoldRole.START })
        assertEquals(HoldRole.START, afterSecond.first { it.id == 2 }.role)
        assertEquals(HoldRole.BODY, afterSecond.first { it.id == 1 }.role)
    }

    @Test
    fun `selecting a finish hold marks exactly that hold FINISH and leaves others untouched`() {
        val result = RouteRegistrationHoldSelection.selectFinishHold(holds, holdId = 3)

        assertEquals(HoldRole.FINISH, result.first { it.id == 3 }.role)
        assertEquals(HoldRole.BODY, result.first { it.id == 1 }.role)
    }

    @Test
    fun `re-selecting a different finish hold demotes the previous one, never leaving two finishes`() {
        val afterFirst = RouteRegistrationHoldSelection.selectFinishHold(holds, holdId = 3)
        val afterSecond = RouteRegistrationHoldSelection.selectFinishHold(afterFirst, holdId = 2)

        assertEquals(1, afterSecond.count { it.role == HoldRole.FINISH })
        assertEquals(HoldRole.FINISH, afterSecond.first { it.id == 2 }.role)
        assertEquals(HoldRole.BODY, afterSecond.first { it.id == 3 }.role)
    }

    @Test
    fun `start and finish can be two distinct holds at once`() {
        val withStart = RouteRegistrationHoldSelection.selectStartHold(holds, holdId = 1)
        val withBoth = RouteRegistrationHoldSelection.selectFinishHold(withStart, holdId = 3)

        assertEquals(HoldRole.START, withBoth.first { it.id == 1 }.role)
        assertEquals(HoldRole.FINISH, withBoth.first { it.id == 3 }.role)
    }

    @Test
    fun `setRole applies a free-form correction without touching any other hold`() {
        val result = RouteRegistrationHoldSelection.setRole(holds, holdId = 2, role = HoldRole.FINISH)

        assertEquals(HoldRole.FINISH, result.first { it.id == 2 }.role)
        assertEquals(HoldRole.BODY, result.first { it.id == 1 }.role)
        assertEquals(HoldRole.BODY, result.first { it.id == 3 }.role)
    }

    @Test
    fun `removeHold drops exactly the targeted hold`() {
        val result = RouteRegistrationHoldSelection.removeHold(holds, holdId = 2)

        assertEquals(listOf(1, 3), result.map { it.id })
    }
}
