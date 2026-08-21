package com.example.climb.edgeagent.heartbeat

import com.example.climb.edge.DeviceHeartbeat
import com.example.climb.edge.HeartbeatStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingHeartbeatReporterTest {

    @Test
    fun `reports success and records the last heartbeat`() = runBlocking {
        val logged = mutableListOf<String>()
        val reporter = LoggingHeartbeatReporter(log = { logged.add(it) })
        val heartbeat = DeviceHeartbeat(
            cameraDeviceId = "camera-1",
            status = HeartbeatStatus.ONLINE,
            timestampEpochMs = 1_000L,
            firmwareOrAppVersion = "0.1.0",
        )

        val result = reporter.reportHeartbeat(heartbeat)

        assertTrue(result)
        assertEquals(heartbeat, reporter.lastReported)
        assertEquals(1, logged.size)
    }

    @Test
    fun `a later heartbeat overwrites lastReported`() = runBlocking {
        val reporter = LoggingHeartbeatReporter(log = {})
        val first = DeviceHeartbeat("camera-1", HeartbeatStatus.ONLINE, 1_000L, "0.1.0")
        val second = DeviceHeartbeat("camera-1", HeartbeatStatus.DEGRADED, 2_000L, "0.1.0")

        reporter.reportHeartbeat(first)
        reporter.reportHeartbeat(second)

        assertEquals(second, reporter.lastReported)
    }
}
