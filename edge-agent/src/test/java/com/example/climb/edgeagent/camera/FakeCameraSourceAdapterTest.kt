package com.example.climb.edgeagent.camera

import com.example.climb.edge.CameraCaptureConfig
import com.example.climb.edge.CameraGeometryProfile
import com.example.climb.edge.EdgeDeviceIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FakeCameraSourceAdapterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val identity = EdgeDeviceIdentity(organizationId = "org-1", wallId = "wall-1", cameraDeviceId = "camera-1")

    @Test
    fun `writes a real file and reports its path`() = runBlocking {
        val outputDir = tempFolder.newFolder("captures")
        val adapter = FakeCameraSourceAdapter(outputDir, CameraCaptureConfig(), identity)

        val frame = adapter.captureStillReferenceFrame()

        val file = File(frame.filePath)
        assertTrue(file.exists())
        assertEquals(file.length(), frame.fileSizeBytes)
    }

    @Test
    fun `records the configured geometry profile version and requested dimensions`() = runBlocking {
        val outputDir = tempFolder.newFolder("captures")
        val profile = CameraGeometryProfile(requestedWidthPx = 1280, requestedHeightPx = 720, version = 3)
        val config = CameraCaptureConfig(geometryProfile = profile)
        val adapter = FakeCameraSourceAdapter(outputDir, config, identity)

        val frame = adapter.captureStillReferenceFrame()

        assertEquals(3, frame.metadata.requestedGeometryProfileVersion)
        assertEquals(1280, frame.metadata.requestedWidthPx)
        assertEquals(720, frame.metadata.requestedHeightPx)
    }

    @Test
    fun `metadata otherwise matches the config when no actual-size override is given`() = runBlocking {
        val outputDir = tempFolder.newFolder("captures")
        val profile = CameraGeometryProfile(requestedRotationDegrees = 90)
        val config = CameraCaptureConfig(geometryProfile = profile)
        val adapter = FakeCameraSourceAdapter(outputDir, config, identity)

        val frame = adapter.captureStillReferenceFrame()

        assertEquals(90, frame.metadata.rotationDegrees)
        assertEquals(identity.organizationId, frame.metadata.organizationId)
        assertEquals(identity.wallId, frame.metadata.wallId)
        assertEquals(identity.cameraDeviceId, frame.metadata.cameraDeviceId)
    }

    @Test
    fun `preserves simulated actual dimensions separately from requested dimensions`() = runBlocking {
        val outputDir = tempFolder.newFolder("captures")
        val config = CameraCaptureConfig(CameraGeometryProfile(requestedWidthPx = 1920, requestedHeightPx = 1080))
        val adapter = FakeCameraSourceAdapter(
            outputDir,
            config,
            identity,
            simulatedActualWidthPx = 1280,
            simulatedActualHeightPx = 720,
        )

        val frame = adapter.captureStillReferenceFrame()

        assertEquals(1920, frame.metadata.requestedWidthPx)
        assertEquals(1080, frame.metadata.requestedHeightPx)
        assertEquals(1280, frame.metadata.widthPx)
        assertEquals(720, frame.metadata.heightPx)
    }

    @Test
    fun `each capture produces a distinct file`() = runBlocking {
        val outputDir = tempFolder.newFolder("captures")
        val adapter = FakeCameraSourceAdapter(outputDir, CameraCaptureConfig(), identity)

        val first = adapter.captureStillReferenceFrame()
        val second = adapter.captureStillReferenceFrame()

        assertNotEquals(first.filePath, second.filePath)
        assertEquals(2, adapter.captureCount)
    }
}
