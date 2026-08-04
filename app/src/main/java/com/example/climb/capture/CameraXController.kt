package com.example.climb.capture

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/** Thin wrapper around CameraX preview + video recording for the record screen. */
class CameraXController(private val context: Context) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView, onError: (Throwable) -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build()
                    val capture = VideoCapture.withOutput(recorder)

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    videoCapture = capture
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to bind camera", t)
                    onError(t)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun startRecording(outputFile: File, onFinalized: (success: Boolean) -> Unit) {
        val capture = videoCapture ?: return onFinalized(false)
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .apply {
                if (hasAudioPermission()) withAudioEnabled()
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    onFinalized(!event.hasError())
                }
            }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CameraXController"
    }
}
