package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallCalibrationActivationGuardTest {

    private fun realCalibration(
        referenceSource: ReferenceSource = ReferenceSource.EDGE_AGENT_CAPTURE,
        hardwareValidated: Boolean = true,
        cameraGeometryProfileVersion: Int = 1,
    ) = WallCalibrationEntity(
        id = 1L,
        organizationId = 10L,
        wallId = 20L,
        referenceImageUrl = "https://storage/wall-1.jpg",
        referenceWidthPx = 1920,
        referenceHeightPx = 1080,
        alignmentFingerprint = "fp",
        calibratedBy = "staff-1",
        createdAt = 1_000L,
        configVersion = 1,
        referenceSource = referenceSource,
        cameraGeometryProfileVersion = cameraGeometryProfileVersion,
        hardwareValidated = hardwareValidated,
    )

    @Test
    fun `a fully real, hardware-validated, matching-version calibration is eligible`() {
        val result = WallCalibrationActivationGuard.checkEligibility(realCalibration(), expectedGeometryProfileVersion = 1)

        assertTrue(result.isEligible)
        assertEquals(emptyList<String>(), result.blockingReasons)
    }

    @Test
    fun `a TEST_FIXTURE calibration cannot be activated even when otherwise fully valid`() {
        val fixtureCalibration = realCalibration(referenceSource = ReferenceSource.TEST_FIXTURE, hardwareValidated = true)

        val result = WallCalibrationActivationGuard.checkEligibility(fixtureCalibration, expectedGeometryProfileVersion = 1)

        assertFalse(result.isEligible)
        assertTrue(result.blockingReasons.any { it.contains("TEST_FIXTURE") })
    }

    @Test
    fun `a calibration never marked hardwareValidated cannot be activated`() {
        val unvalidated = realCalibration(hardwareValidated = false)

        val result = WallCalibrationActivationGuard.checkEligibility(unvalidated, expectedGeometryProfileVersion = 1)

        assertFalse(result.isEligible)
        assertTrue(result.blockingReasons.any { it.contains("hardwareValidated") })
    }

    @Test
    fun `a geometry-profile version mismatch blocks activation even for an otherwise real calibration`() {
        val staleProfileCalibration = realCalibration(cameraGeometryProfileVersion = 1)

        val result = WallCalibrationActivationGuard.checkEligibility(staleProfileCalibration, expectedGeometryProfileVersion = 2)

        assertFalse(result.isEligible)
        assertTrue(result.blockingReasons.any { it.contains("cameraGeometryProfileVersion") })
    }

    @Test
    fun `every blocking reason is reported at once, not just the first`() {
        val worstCase = realCalibration(
            referenceSource = ReferenceSource.TEST_FIXTURE,
            hardwareValidated = false,
            cameraGeometryProfileVersion = 1,
        )

        val result = WallCalibrationActivationGuard.checkEligibility(worstCase, expectedGeometryProfileVersion = 2)

        assertFalse(result.isEligible)
        assertEquals(3, result.blockingReasons.size)
    }
}
