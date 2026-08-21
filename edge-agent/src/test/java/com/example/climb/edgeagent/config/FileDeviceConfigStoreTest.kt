package com.example.climb.edgeagent.config

import com.example.climb.edge.EdgeDeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileDeviceConfigStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `load returns null when no file exists yet`() {
        val store = FileDeviceConfigStore(File(tempFolder.root, "identity.txt"))
        assertNull(store.load())
    }

    @Test
    fun `save then load round-trips the identity`() {
        val store = FileDeviceConfigStore(File(tempFolder.root, "identity.txt"))
        val identity = EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "camera-1")

        store.save(identity)
        val loaded = store.load()

        assertEquals(identity, loaded)
    }

    @Test
    fun `save creates missing parent directories`() {
        val nestedFile = File(tempFolder.root, "nested/dir/identity.txt")
        val store = FileDeviceConfigStore(nestedFile)
        val identity = EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "camera-1")

        store.save(identity)

        assertEquals(identity, store.load())
    }

    @Test
    fun `overwriting with a new identity replaces the old one`() {
        val store = FileDeviceConfigStore(File(tempFolder.root, "identity.txt"))
        store.save(EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "camera-1"))

        val second = EdgeDeviceIdentity(organizationId = "org-2", wallId = "wall-2", cameraDeviceId = "camera-2")
        store.save(second)

        assertEquals(second, store.load())
    }
}
