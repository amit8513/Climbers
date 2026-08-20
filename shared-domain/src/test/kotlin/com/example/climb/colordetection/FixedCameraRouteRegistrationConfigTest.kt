package com.example.climb.colordetection

import org.junit.Assert.assertThrows
import org.junit.Test

class FixedCameraRouteRegistrationConfigTest {

    @Test
    fun `defaults are valid`() {
        FixedCameraRouteRegistrationConfig()
    }

    @Test
    fun `rejects non-positive minCompetitiveMarginDeltaE`() {
        assertThrows(IllegalArgumentException::class.java) {
            FixedCameraRouteRegistrationConfig(minCompetitiveMarginDeltaE = 0.0)
        }
    }

    @Test
    fun `rejects non-positive maxAbsoluteDeltaEForAnyMatch`() {
        assertThrows(IllegalArgumentException::class.java) {
            FixedCameraRouteRegistrationConfig(maxAbsoluteDeltaEForAnyMatch = -1.0)
        }
    }

    @Test
    fun `rejects non-positive maxAlignmentFingerprintDistance`() {
        assertThrows(IllegalArgumentException::class.java) {
            FixedCameraRouteRegistrationConfig(maxAlignmentFingerprintDistance = 0.0)
        }
    }
}
