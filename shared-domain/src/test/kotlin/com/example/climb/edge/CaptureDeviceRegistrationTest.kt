package com.example.climb.edge

import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureDeviceRegistrationTest {

    @Test
    fun `accepts non-blank ids`() {
        CaptureDeviceRegistration(
            cameraDeviceId = "camera-1",
            organizationId = "org-1",
            wallId = "wall-1",
            enabled = true,
            registeredAtEpochMs = 1L,
        )
    }

    @Test
    fun `rejects blank cameraDeviceId`() {
        assertThrows(IllegalArgumentException::class.java) {
            CaptureDeviceRegistration(
                cameraDeviceId = "",
                organizationId = "org-1",
                wallId = "wall-1",
                enabled = true,
                registeredAtEpochMs = 1L,
            )
        }
    }
}
