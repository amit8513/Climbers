package com.example.climb.edge

import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceHeartbeatTest {

    @Test
    fun `accepts every status`() {
        HeartbeatStatus.entries.forEach { status ->
            DeviceHeartbeat(cameraDeviceId = "camera-1", status = status, timestampEpochMs = 1L, firmwareOrAppVersion = "0.1.0")
        }
    }

    @Test
    fun `rejects blank cameraDeviceId`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceHeartbeat(cameraDeviceId = "", status = HeartbeatStatus.ONLINE, timestampEpochMs = 1L, firmwareOrAppVersion = "0.1.0")
        }
    }

    @Test
    fun `rejects blank firmwareOrAppVersion`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceHeartbeat(cameraDeviceId = "camera-1", status = HeartbeatStatus.ONLINE, timestampEpochMs = 1L, firmwareOrAppVersion = "")
        }
    }
}
