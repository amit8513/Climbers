package com.example.climb.edge

import com.example.climb.colordetection.NormalizedRect
import org.junit.Assert.assertThrows
import org.junit.Test

class CapturedFrameTest {

    private val metadata = ReferenceFrameMetadata(
        requestedGeometryProfileVersion = 1,
        requestedWidthPx = 1920,
        requestedHeightPx = 1080,
        widthPx = 1920,
        heightPx = 1080,
        rotationDegrees = 0,
        mirrored = false,
        actualCropRect = NormalizedRect(0f, 0f, 1f, 1f),
        capturedAtEpochMs = 1_000L,
        organizationId = "org-1",
        wallId = "wall-1",
        cameraDeviceId = "camera-1",
    )

    @Test
    fun `accepts a real-looking path and size`() {
        CapturedFrame(filePath = "/data/user/0/com.example.climb.edgeagent/files/captures/a.jpg", fileSizeBytes = 4096, metadata = metadata)
    }

    @Test
    fun `rejects blank filePath`() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturedFrame(filePath = "", fileSizeBytes = 4096, metadata = metadata)
        }
    }

    @Test
    fun `rejects negative fileSizeBytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            CapturedFrame(filePath = "/tmp/a.jpg", fileSizeBytes = -1, metadata = metadata)
        }
    }
}
