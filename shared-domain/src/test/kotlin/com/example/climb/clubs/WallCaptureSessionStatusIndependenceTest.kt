package com.example.climb.clubs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `captureStatus` (physical recording/upload lifecycle) and `captureAnalysisStatus` (downstream
 * processing lifecycle) used to be modeled as one merged state machine — this guards the fix:
 * every combination of the two enums must be independently settable on [WallCaptureSession], with
 * neither field's value ever silently derived from or overwritten by the other.
 */
class WallCaptureSessionStatusIndependenceTest {

    private fun session(captureStatus: CaptureStatus, captureAnalysisStatus: CaptureAnalysisStatus) = WallCaptureSession(
        id = "session-1",
        organizationId = 1L,
        wallId = 1L,
        cameraDeviceId = 1L,
        readerDeviceId = 1L,
        wristbandCredentialId = 1L,
        attributedUserId = "u1",
        captureStatus = captureStatus,
        captureAnalysisStatus = captureAnalysisStatus,
        armedAt = 0L,
        leaseExpiresAt = 60_000L,
    )

    @Test
    fun `every CaptureStatus combines independently with every CaptureAnalysisStatus`() {
        for (capture in CaptureStatus.entries) {
            for (analysis in CaptureAnalysisStatus.entries) {
                val s = session(capture, analysis)
                assertEquals("captureStatus must hold exactly what was set", capture, s.captureStatus)
                assertEquals("captureAnalysisStatus must hold exactly what was set", analysis, s.captureAnalysisStatus)
            }
        }
    }

    @Test
    fun `changing one field via copy never changes the other`() {
        val original = session(CaptureStatus.ARMED, CaptureAnalysisStatus.NOT_STARTED)

        val captureChanged = original.copy(captureStatus = CaptureStatus.VIDEO_READY)
        assertEquals(CaptureStatus.VIDEO_READY, captureChanged.captureStatus)
        assertEquals("changing captureStatus must not change captureAnalysisStatus", CaptureAnalysisStatus.NOT_STARTED, captureChanged.captureAnalysisStatus)

        val analysisChanged = original.copy(captureAnalysisStatus = CaptureAnalysisStatus.PROCESSING)
        assertEquals("changing captureAnalysisStatus must not change captureStatus", CaptureStatus.ARMED, analysisChanged.captureStatus)
        assertEquals(CaptureAnalysisStatus.PROCESSING, analysisChanged.captureAnalysisStatus)
    }
}
