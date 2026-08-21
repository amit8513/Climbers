package com.example.climb.edge

/**
 * The result of registering one [EdgeDeviceIdentity]. Mirrors the shape a future
 * `captureDevices/{deviceUid}` Firestore document would hold (per the plan doc's device-identity
 * section) — no Firestore-backed registry exists yet; see `InMemoryDeviceRegistry` in
 * :edge-agent, the only implementation for this phase.
 */
data class CaptureDeviceRegistration(
    val cameraDeviceId: String,
    val organizationId: String,
    val wallId: String,
    val enabled: Boolean,
    val registeredAtEpochMs: Long,
) {
    init {
        require(cameraDeviceId.isNotBlank()) { "cameraDeviceId must not be blank" }
        require(organizationId.isNotBlank()) { "organizationId must not be blank" }
        require(wallId.isNotBlank()) { "wallId must not be blank" }
    }
}

interface DeviceRegistry {
    suspend fun registerDevice(identity: EdgeDeviceIdentity): CaptureDeviceRegistration
    suspend fun getRegistration(cameraDeviceId: String): CaptureDeviceRegistration?
}
