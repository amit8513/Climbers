package com.example.climb.edgeagent.upload

import com.example.climb.colordetection.NormalizedRect
import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.ReferenceFrameMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalCopyUploaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val metadata = ReferenceFrameMetadata(
        requestedGeometryProfileVersion = 1,
        requestedWidthPx = 1920,
        requestedHeightPx = 1080,
        widthPx = 1920,
        heightPx = 1080,
        rotationDegrees = 0,
        mirrored = false,
        actualCropRect = NormalizedRect(0f, 0f, 1f, 1f),
        capturedAtEpochMs = 1_000L,
        organizationId = "org-1",
        wallId = "wall-1",
        cameraDeviceId = "camera-1",
    )

    @Test
    fun `copies an existing file and reports success`() = runBlocking {
        val sourceFile = tempFolder.newFile("reference.jpg")
        sourceFile.writeBytes(byteArrayOf(1, 2, 3))
        val uploadedDir = File(tempFolder.root, "uploaded")
        val uploader = LocalCopyUploader(uploadedDir)
        val frame = CapturedFrame(sourceFile.absolutePath, sourceFile.length(), metadata)

        val result = uploader.upload(frame)

        assertTrue(result.success)
        assertNull(result.errorMessage)
        val destination = File(result.remoteReference!!)
        assertTrue(destination.exists())
        assertEquals(3L, destination.length())
    }

    @Test
    fun `reports failure when the source file is missing`() = runBlocking {
        val uploader = LocalCopyUploader(File(tempFolder.root, "uploaded"))
        val frame = CapturedFrame("${tempFolder.root}/does-not-exist.jpg", 0, metadata)

        val result = uploader.upload(frame)

        assertFalse(result.success)
        assertNull(result.remoteReference)
        assertTrue(result.errorMessage!!.contains("missing"))
    }

    @Test
    fun `re-uploading the same source overwrites the previous copy`() = runBlocking {
        val sourceFile = tempFolder.newFile("reference.jpg")
        sourceFile.writeBytes(byteArrayOf(1))
        val uploadedDir = File(tempFolder.root, "uploaded")
        val uploader = LocalCopyUploader(uploadedDir)
        val frame = CapturedFrame(sourceFile.absolutePath, sourceFile.length(), metadata)
        uploader.upload(frame)

        sourceFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        val secondResult = uploader.upload(CapturedFrame(sourceFile.absolutePath, sourceFile.length(), metadata))

        assertEquals(4L, File(secondResult.remoteReference!!).length())
    }
}
