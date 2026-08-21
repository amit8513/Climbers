package com.example.climb.edgeagent.camera

import com.example.climb.edge.CameraCaptureConfig
import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.EdgeDeviceIdentity
import com.example.climb.edge.ReferenceFrameMetadata
import java.io.File

/**
 * No real camera involved — writes a small placeholder file so callers (tests, and the debug
 * screen when run without camera hardware) exercise real file I/O (existence, size, cleanup)
 * without needing a device/emulator with a camera.
 *
 * [simulatedActualWidthPx]/[simulatedActualHeightPx] default to [config]'s requested dimensions
 * (the common case), but can be overridden to simulate hardware negotiating a different
 * resolution than requested — the same requested/actual split
 * [CameraXCameraSourceAdapter] reports for real, made reproducible here without hardware.
 */
class FakeCameraSourceAdapter(
    private val outputDirectory: File,
    private val config: CameraCaptureConfig,
    private val identity: EdgeDeviceIdentity,
    private val simulatedActualWidthPx: Int = config.targetWidthPx,
    private val simulatedActualHeightPx: Int = config.targetHeightPx,
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
) : CameraSourceAdapter {

    var captureCount: Int = 0
        private set

    override suspend fun captureStillReferenceFrame(): CapturedFrame {
        captureCount++
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val outputFile = File(outputDirectory, "fake_reference_$captureCount.jpg")
        outputFile.writeBytes(ByteArray(FAKE_FILE_SIZE_BYTES) { 0xFF.toByte() })

        return CapturedFrame(
            filePath = outputFile.absolutePath,
            fileSizeBytes = outputFile.length(),
            metadata = ReferenceFrameMetadata(
                requestedGeometryProfileVersion = config.version,
                requestedWidthPx = config.targetWidthPx,
                requestedHeightPx = config.targetHeightPx,
                widthPx = simulatedActualWidthPx,
                heightPx = simulatedActualHeightPx,
                rotationDegrees = config.targetRotationDegrees,
                mirrored = config.mirrored,
                actualCropRect = config.cropRect,
                capturedAtEpochMs = clockEpochMs(),
                organizationId = identity.organizationId,
                wallId = identity.wallId,
                cameraDeviceId = identity.cameraDeviceId,
            ),
        )
    }

    companion object {
        private const val FAKE_FILE_SIZE_BYTES = 256
    }
}
