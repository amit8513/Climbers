package com.example.climb.edge

import com.example.climb.colordetection.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReferenceFrameMetadataTest {

    private fun valid(
        requestedWidthPx: Int = 1920,
        requestedHeightPx: Int = 1080,
        widthPx: Int = 1920,
        heightPx: Int = 1080,
    ) = ReferenceFrameMetadata(
        requestedGeometryProfileVersion = 1,
        requestedWidthPx = requestedWidthPx,
        requestedHeightPx = requestedHeightPx,
        widthPx = widthPx,
        heightPx = heightPx,
        rotationDegrees = 0,
        mirrored = false,
        actualCropRect = NormalizedRect(0f, 0f, 1f, 1f),
        capturedAtEpochMs = 1_000L,
        organizationId = "org-1",
        wallId = "wall-1",
        cameraDeviceId = "camera-1",
    )

    @Test
    fun `accepts matching requested and actual dimensions`() {
        valid()
    }

    @Test
    fun `preserves actual output dimensions separately from requested dimensions`() {
        val metadata = valid(requestedWidthPx = 1920, requestedHeightPx = 1080, widthPx = 1280, heightPx = 720)

        assertEquals(1920, metadata.requestedWidthPx)
        assertEquals(1080, metadata.requestedHeightPx)
        assertEquals(1280, metadata.widthPx)
        assertEquals(720, metadata.heightPx)
    }

    @Test
    fun `rejects non-positive requested width`() {
        assertThrows(IllegalArgumentException::class.java) { valid(requestedWidthPx = 0) }
    }

    @Test
    fun `rejects non-positive actual width`() {
        assertThrows(IllegalArgumentException::class.java) { valid(widthPx = 0) }
    }

    @Test
    fun `rejects non-positive actual height`() {
        assertThrows(IllegalArgumentException::class.java) { valid(heightPx = -1) }
    }

    @Test
    fun `rejects a non-positive requestedGeometryProfileVersion`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceFrameMetadata(
                requestedGeometryProfileVersion = 0,
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
        }
    }
}
