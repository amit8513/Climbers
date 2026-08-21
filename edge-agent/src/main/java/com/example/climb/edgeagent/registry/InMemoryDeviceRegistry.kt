package com.example.climb.edgeagent.registry

import com.example.climb.edge.CaptureDeviceRegistration
import com.example.climb.edge.DeviceRegistry
import com.example.climb.edge.EdgeDeviceIdentity

/**
 * No Firestore-backed `captureDevices` registry exists yet (Phase 1.5A scope). An in-memory
 * stand-in so the abstraction has one real, testable implementation until the backend exists —
 * registrations do not survive process death, which is fine for a debug-screen spike.
 */
class InMemoryDeviceRegistry(
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
) : DeviceRegistry {

    private val registrations = mutableMapOf<String, CaptureDeviceRegistration>()

    override suspend fun registerDevice(identity: EdgeDeviceIdentity): CaptureDeviceRegistration {
        val registration = CaptureDeviceRegistration(
            cameraDeviceId = identity.cameraDeviceId,
            organizationId = identity.organizationId,
            wallId = identity.wallId,
            enabled = true,
            registeredAtEpochMs = clockEpochMs(),
        )
        registrations[identity.cameraDeviceId] = registration
        return registration
    }

    override suspend fun getRegistration(cameraDeviceId: String): CaptureDeviceRegistration? {
        return registrations[cameraDeviceId]
    }
}
