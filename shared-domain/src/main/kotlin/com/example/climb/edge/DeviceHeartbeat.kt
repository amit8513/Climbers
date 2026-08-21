package com.example.climb.edge

/**
 * Liveness signal for one capture device. Mirrors the plan doc's `captureDevices` registry
 * concept (heartbeat timestamps that need instant effect, tracked separately from static
 * per-device auth scope) — no backend exists yet in Phase 1.5A, so nothing here is wired to a
 * real `enabled`/`revoked` check; see `HeartbeatReporter` implementations in :edge-agent.
 */
enum class HeartbeatStatus { ONLINE, DEGRADED, OFFLINE }

data class DeviceHeartbeat(
    val cameraDeviceId: String,
    val status: HeartbeatStatus,
    val timestampEpochMs: Long,
    val firmwareOrAppVersion: String,
) {
    init {
        require(cameraDeviceId.isNotBlank()) { "cameraDeviceId must not be blank" }
        require(firmwareOrAppVersion.isNotBlank()) { "firmwareOrAppVersion must not be blank" }
    }
}

/**
 * What happens to a [DeviceHeartbeat] once produced. No real backend transport exists yet — see
 * `LoggingHeartbeatReporter` in :edge-agent, the only implementation for this phase.
 */
interface HeartbeatReporter {
    suspend fun reportHeartbeat(heartbeat: DeviceHeartbeat): Boolean
}
