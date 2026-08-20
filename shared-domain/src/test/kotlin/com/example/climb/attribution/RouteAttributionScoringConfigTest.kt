package com.example.climb.attribution

import org.junit.Assert.assertThrows
import org.junit.Test

class RouteAttributionScoringConfigTest {

    @Test
    fun `defaults are valid`() {
        RouteAttributionScoringConfig()
    }

    @Test
    fun `rejects reviewMinScore above verifiedMinScore`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteAttributionScoringConfig(reviewMinScore = 0.9f, verifiedMinScore = 0.75f)
        }
    }

    @Test
    fun `rejects non-positive minWinnerMargin`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteAttributionScoringConfig(minWinnerMargin = 0f)
        }
    }

    @Test
    fun `rejects minLimbLandmarkConfidence outside 0-1`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteAttributionScoringConfig(minLimbLandmarkConfidence = 1.5f)
        }
    }
}
