package com.example.climb.validation

import com.example.climb.colordetection.AlignmentCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualValidationGeometryGateTest {

    private fun session(profileVersion: Int = 1) = ManualValidationSession(
        validationSessionId = "session-1",
        referenceImagePath = "/local/ref.jpg",
        videoPath = "/local/video.mp4",
        wallOrFixtureId = "wall-a",
        cameraGeometryProfileVersion = profileVersion,
        createdAtEpochMs = 1_000L,
    )

    @Test
    fun `matching geometry profile version and aspect ratio succeeds with ValidIdentity`() {
        val result = ManualValidationGeometryGate.check(
            session = session(profileVersion = 1),
            referenceImageDimensions = ImageDimensions(1920, 1080),
            videoDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 1,
        )

        assertTrue(result is AlignmentCheckResult.ValidIdentity)
        assertEquals(LOCAL_VALIDATION_WALL_CALIBRATION_ID, (result as AlignmentCheckResult.ValidIdentity).wallCalibrationId)
    }

    @Test
    fun `a mismatched cameraGeometryProfileVersion is rejected`() {
        val result = ManualValidationGeometryGate.check(
            session = session(profileVersion = 1),
            referenceImageDimensions = ImageDimensions(1920, 1080),
            videoDimensions = ImageDimensions(1920, 1080),
            expectedGeometryProfileVersion = 2,
        )

        assertTrue(result is AlignmentCheckResult.CalibrationInvalid)
        assertTrue((result as AlignmentCheckResult.CalibrationInvalid).reason.contains("VALIDATION_GEOMETRY_MISMATCH"))
    }

    @Test
    fun `a mismatched aspect ratio (different orientation or crop) is rejected`() {
        val result = ManualValidationGeometryGate.check(
            session = session(),
            referenceImageDimensions = ImageDimensions(1920, 1080), // 16:9
            videoDimensions = ImageDimensions(1080, 1920), // 9:16 - portrait instead of landscape
            expectedGeometryProfileVersion = 1,
        )

        assertTrue(result is AlignmentCheckResult.CalibrationInvalid)
        assertTrue((result as AlignmentCheckResult.CalibrationInvalid).reason.contains("VALIDATION_GEOMETRY_MISMATCH"))
    }

    @Test
    fun `a negligible aspect ratio difference within tolerance still succeeds`() {
        val result = ManualValidationGeometryGate.check(
            session = session(),
            referenceImageDimensions = ImageDimensions(1920, 1080),
            videoDimensions = ImageDimensions(1918, 1080), // trivial encoder rounding difference
            expectedGeometryProfileVersion = 1,
        )

        assertTrue(result is AlignmentCheckResult.ValidIdentity)
    }
}
