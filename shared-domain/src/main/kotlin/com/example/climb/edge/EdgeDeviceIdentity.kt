package com.example.climb.edge

/**
 * Which organization/wall/camera this physical Edge Capture Agent device is configured as.
 * Persisted locally on-device (see [DeviceConfigStore] in :edge-agent) — no backend-driven
 * provisioning exists yet (that's explicitly out of scope for Phase 1.5A).
 */
data class EdgeDeviceIdentity(
    val organizationId: String,
    val wallId: String,
    val cameraDeviceId: String,
) {
    init {
        require(organizationId.isNotBlank()) { "organizationId must not be blank" }
        require(wallId.isNotBlank()) { "wallId must not be blank" }
        require(cameraDeviceId.isNotBlank()) { "cameraDeviceId must not be blank" }
    }
}
