package com.example.climb.edge

import org.junit.Assert.assertThrows
import org.junit.Test

class EdgeDeviceIdentityTest {

    @Test
    fun `accepts non-blank fields`() {
        val identity = EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "camera-1")
        assert(identity.organizationId == "org-1")
    }

    @Test
    fun `rejects blank organizationId`() {
        assertThrows(IllegalArgumentException::class.java) {
            EdgeDeviceIdentity(organizationId = "", wallId = "wall-1", cameraDeviceId = "camera-1")
        }
    }

    @Test
    fun `rejects blank wallId`() {
        assertThrows(IllegalArgumentException::class.java) {
            EdgeDeviceIdentity(organizationId = "org-1", wallId = "  ", cameraDeviceId = "camera-1")
        }
    }

    @Test
    fun `rejects blank cameraDeviceId`() {
        assertThrows(IllegalArgumentException::class.java) {
            EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "")
        }
    }
}
