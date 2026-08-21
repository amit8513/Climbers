package com.example.climb.edgeagent.heartbeat

import com.example.climb.edge.DeviceHeartbeat
import com.example.climb.edge.HeartbeatReporter

/**
 * No heartbeat backend exists yet (Phase 1.5A scope — no `captureDevices` doc, no server call).
 * Records the last heartbeat and logs it, so the abstraction is exercised end-to-end from the
 * debug screen without inventing a transport ahead of the real one.
 */
class LoggingHeartbeatReporter(private val log: (String) -> Unit = ::println) : HeartbeatReporter {

    var lastReported: DeviceHeartbeat? = null
        private set

    override suspend fun reportHeartbeat(heartbeat: DeviceHeartbeat): Boolean {
        lastReported = heartbeat
        log("[heartbeat] $heartbeat")
        return true
    }
}
