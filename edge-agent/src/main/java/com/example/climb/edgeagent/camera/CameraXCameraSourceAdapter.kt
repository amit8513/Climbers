package com.example.climb.edgeagent.camera

import android.content.Context
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.climb.edge.CameraCaptureConfig
import com.example.climb.edge.CameraLensFacing
import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.EdgeDeviceIdentity
import com.example.climb.edge.ReferenceFrameMetadata
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Binds CameraX to [lifecycleOwner]/[previewView] and captures still reference frames via
 * `ImageCapture`. [config]'s resolution/rotation are only *requested* — per
 * docs/ROUTE_ATTRIBUTION_PLAN.md §10 the fixed resolution/orientation/crop assumption is a POC
 * convention, not something this adapter enforces — the metadata on the returned [CapturedFrame]
 * always reflects what CameraX's `resolutionInfo` actually reports for this specific device, so a
 * mismatch is visible rather than silently assumed away.
 *
 * Not unit-tested: binding a real camera needs either a physical device or an instrumented test
 * with camera hardware, neither of which this phase has (see
 * hardware/wall-reader-firmware-adjacent gate notes in NEXT_STEPS.md). [FakeCameraSourceAdapter]
 * covers the same interface contract without hardware.
 */
class CameraXCameraSourceAdapter(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val outputDirectory: File,
    private val config: CameraCaptureConfig,
    private val identity: EdgeDeviceIdentity,
) : CameraSourceAdapter {

    private var imageCapture: ImageCapture? = null

    /** Must complete before [captureStillReferenceFrame] is called. */
    suspend fun bind(): Unit = suspendCancellableCoroutine { cont ->
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setTargetRotation(config.targetRotationDegrees.toSurfaceRotation())
                        .build()

                    // Derived from the geometry profile, not hardcoded, so this adapter is
                    // genuinely wired to CameraGeometryProfile.lensFacing rather than just
                    // happening to agree with it. FRONT can't reach this line today —
                    // CameraGeometryProfile's own init block rejects it outright — but the
                    // mapping exists so the day it can, this line doesn't silently ignore it.
                    val cameraSelector = when (config.geometryProfile.lensFacing) {
                        CameraLensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                        CameraLensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        capture,
                    )
                    imageCapture = capture
                    if (cont.isActive) cont.resume(Unit)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override suspend fun captureStillReferenceFrame(): CapturedFrame {
        val capture = imageCapture
            ?: error("CameraXCameraSourceAdapter.bind() must complete before capturing")
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val outputFile = File(outputDirectory, "reference_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val resolution = capture.resolutionInfo
                        val metadata = ReferenceFrameMetadata(
                            requestedGeometryProfileVersion = config.version,
                            requestedWidthPx = config.targetWidthPx,
                            requestedHeightPx = config.targetHeightPx,
                            // Actual, independently measured from CameraX — never assumed to
                            // equal the request (falls back to it only if resolutionInfo is
                            // somehow unavailable, which should not happen post-bind()).
                            widthPx = resolution?.resolution?.width ?: config.targetWidthPx,
                            heightPx = resolution?.resolution?.height ?: config.targetHeightPx,
                            rotationDegrees = resolution?.rotationDegrees ?: config.targetRotationDegrees,
                            // v1 honesty limit — see ReferenceFrameMetadata's doc comment: not
                            // independently measured from CameraX yet, just echoes the request.
                            mirrored = config.mirrored,
                            actualCropRect = config.cropRect,
                            capturedAtEpochMs = System.currentTimeMillis(),
                            organizationId = identity.organizationId,
                            wallId = identity.wallId,
                            cameraDeviceId = identity.cameraDeviceId,
                        )
                        if (cont.isActive) {
                            cont.resume(
                                CapturedFrame(
                                    filePath = outputFile.absolutePath,
                                    fileSizeBytes = outputFile.length(),
                                    metadata = metadata,
                                ),
                            )
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }
    }

    private fun Int.toSurfaceRotation(): Int = when (((this % 360) + 360) % 360) {
        90 -> Surface.ROTATION_90
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_270
        else -> Surface.ROTATION_0
    }
}
