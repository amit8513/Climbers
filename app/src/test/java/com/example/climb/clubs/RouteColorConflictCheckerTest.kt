package com.example.climb.clubs

import com.example.climb.colordetection.FixedCameraRouteRegistrationConfig
import com.example.climb.data.RouteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteColorConflictCheckerTest {

    @Test
    fun `an identical color to an existing active route conflicts`() {
        val result = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = RouteColor.RED.hex,
            activeColorHexesOnSameWall = listOf(RouteColor.RED.hex),
        )

        assertTrue(result.hasConflict)
        assertEquals(listOf(RouteColor.RED.hex), result.conflictingColorHexes)
    }

    @Test
    fun `a perceptually distant color does not conflict`() {
        val result = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = RouteColor.RED.hex,
            activeColorHexesOnSameWall = listOf(RouteColor.BLUE.hex),
        )

        assertFalse(result.hasConflict)
        assertEquals(emptyList<Long>(), result.conflictingColorHexes)
    }

    @Test
    fun `an empty wall has no conflicts for any color`() {
        val result = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = RouteColor.RED.hex,
            activeColorHexesOnSameWall = emptyList(),
        )

        assertFalse(result.hasConflict)
    }

    @Test
    fun `only the actually-conflicting colors are reported, not every active color`() {
        val result = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = RouteColor.RED.hex,
            activeColorHexesOnSameWall = listOf(RouteColor.RED.hex, RouteColor.BLUE.hex, RouteColor.GREEN.hex),
        )

        assertTrue(result.hasConflict)
        assertEquals(listOf(RouteColor.RED.hex), result.conflictingColorHexes)
    }

    @Test
    fun `a tighter margin config can turn a previously-conflicting pair into a non-conflict`() {
        val looseConfig = FixedCameraRouteRegistrationConfig(minCompetitiveMarginDeltaE = 0.01)

        val result = RouteColorConflictChecker.checkConflicts(
            candidateColorHex = RouteColor.RED.hex,
            activeColorHexesOnSameWall = listOf(RouteColor.ORANGE.hex),
            config = looseConfig,
        )

        assertFalse(result.hasConflict)
    }
}
