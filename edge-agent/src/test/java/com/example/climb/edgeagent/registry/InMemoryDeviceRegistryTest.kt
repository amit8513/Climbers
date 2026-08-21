package com.example.climb.edgeagent.registry

import com.example.climb.edge.EdgeDeviceIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryDeviceRegistryTest {

    private val identity = EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "camera-1")

    @Test
    fun `unregistered device has no registration`() = runBlocking {
        val registry = InMemoryDeviceRegistry()
        assertNull(registry.getRegistration("camera-1"))
    }

    @Test
    fun `registering a device makes it retrievable and enabled`() = runBlocking {
        val registry = InMemoryDeviceRegistry(clockEpochMs = { 5_000L })

        val registration = registry.registerDevice(identity)

        assertEquals("camera-1", registration.cameraDeviceId)
        assertTrue(registration.enabled)
        assertEquals(5_000L, registration.registeredAtEpochMs)
        assertEquals(registration, registry.getRegistration("camera-1"))
    }

    @Test
    fun `re-registering the same device replaces its registration`() = runBlocking {
        val registry = InMemoryDeviceRegistry(clockEpochMs = { 5_000L })
        registry.registerDevice(identity)

        val movedIdentity = identity.copy(wallId = "wall-2")
        registry.registerDevice(movedIdentity)

        assertEquals("wall-2", registry.getRegistration("camera-1")?.wallId)
    }
}
