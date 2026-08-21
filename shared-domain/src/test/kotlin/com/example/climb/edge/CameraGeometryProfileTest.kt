package com.example.climb.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraGeometryProfileTest {

    @Test
    fun `default profile is back camera, not mirrored, and internally consistent`() {
        val profile = CameraGeometryProfile()
        assertEquals(CameraLensFacing.BACK, profile.lensFacing)
        assertFalse(profile.mirrorExpected)
        assertEquals(1920, profile.requestedWidthPx)
        assertEquals(1080, profile.requestedHeightPx)
        assertEquals(1, profile.version)
    }

    @Test
    fun `front lens facing cannot become the default POC path, even deliberately`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(lensFacing = CameraLensFacing.FRONT)
        }
    }

    @Test
    fun `mirrored capture cannot become the default POC path, even deliberately`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(mirrorExpected = true)
        }
    }

    @Test
    fun `rejects non-positive requested width`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(requestedWidthPx = 0)
        }
    }

    @Test
    fun `rejects non-positive requested height`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(requestedHeightPx = -1)
        }
    }

    @Test
    fun `rejects rotation not a multiple of 90`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(requestedRotationDegrees = 45)
        }
    }

    @Test
    fun `accepts every multiple of 90`() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            CameraGeometryProfile(requestedRotationDegrees = rotation)
        }
    }

    @Test
    fun `rejects a degenerate crop rect`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(cropRect = com.example.climb.colordetection.NormalizedRect(0.5f, 0f, 0.5f, 1f))
        }
    }

    @Test
    fun `rejects an aspect ratio that does not match the requested resolution`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(
                requestedAspectRatio = CameraAspectRatio.RATIO_4_3,
                requestedWidthPx = 1920,
                requestedHeightPx = 1080,
            )
        }
    }

    @Test
    fun `accepts a 4-3 resolution paired with a 4-3 aspect ratio`() {
        CameraGeometryProfile(
            requestedAspectRatio = CameraAspectRatio.RATIO_4_3,
            requestedWidthPx = 1280,
            requestedHeightPx = 960,
        )
    }

    @Test
    fun `rejects non-positive version`() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraGeometryProfile(version = 0)
        }
    }

    @Test
    fun `compatibility check is an exact version match`() {
        assertTrue(isCameraGeometryProfileCompatible(referenceProfileVersion = 1, attemptProfileVersion = 1))
    }

    @Test
    fun `a profile version mismatch is detectable`() {
        assertFalse(isCameraGeometryProfileCompatible(referenceProfileVersion = 1, attemptProfileVersion = 2))
    }
}
