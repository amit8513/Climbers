package com.example.climb.analysis.metrics

import org.junit.Assert.assertThrows
import org.junit.Test

class HoldContactConfigTest {

    @Test
    fun `defaults are valid`() {
        HoldContactConfig()
    }

    @Test
    fun `rejects candidate threshold not tighter than approach threshold`() {
        assertThrows(IllegalArgumentException::class.java) {
            HoldContactConfig(contactApproachDistanceThreshold = 0.02f, contactCandidateDistanceThreshold = 0.03f)
        }
    }

    @Test
    fun `rejects release threshold not looser than candidate threshold`() {
        assertThrows(IllegalArgumentException::class.java) {
            HoldContactConfig(contactCandidateDistanceThreshold = 0.03f, contactReleaseDistanceThreshold = 0.02f)
        }
    }

    @Test
    fun `rejects short-gap threshold at or above the long-gap reset threshold`() {
        assertThrows(IllegalArgumentException::class.java) {
            HoldContactConfig(contactShortGapMaxMs = 600L, contactTrackingGapResetMs = 500L)
        }
    }

    @Test
    fun `rejects non-positive topKNearbyHolds`() {
        assertThrows(IllegalArgumentException::class.java) {
            HoldContactConfig(topKNearbyHolds = 0)
        }
    }
}
