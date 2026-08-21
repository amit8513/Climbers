package com.example.climb.edgeagent.debug

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.climb.edge.CameraCaptureConfig
import com.example.climb.edgeagent.camera.CameraSourceAdapter
import com.example.climb.edgeagent.camera.CameraXCameraSourceAdapter
import com.example.climb.edgeagent.camera.FakeCameraSourceAdapter
import com.example.climb.edgeagent.config.FileDeviceConfigStore
import com.example.climb.edgeagent.heartbeat.LoggingHeartbeatReporter
import com.example.climb.edgeagent.registry.InMemoryDeviceRegistry
import com.example.climb.edgeagent.upload.LocalCopyUploader
import kotlinx.coroutines.launch
import java.io.File

/**
 * Phase 1.5A debug/preview screen — lets a real still reference frame be captured and inspected
 * on an actual Android device, or the same flow exercised end-to-end with
 * [FakeCameraSourceAdapter] when no camera hardware is available. Not the future
 * `RouteRegistrationScreen` (Phase 2, §13) — this has no route/wall-registration UI at all.
 */
@Composable
fun EdgeAgentDebugScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val configStore = remember { FileDeviceConfigStore(File(context.filesDir, "device_identity.txt")) }
    val deviceRegistry = remember { InMemoryDeviceRegistry() }
    val heartbeatReporter = remember { LoggingHeartbeatReporter() }
    val viewModel: EdgeAgentViewModel = viewModel(
        factory = EdgeAgentViewModel.factory(configStore, deviceRegistry, heartbeatReporter),
    )
    val state by viewModel.uiState.collectAsState()

    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    val captureDirectory = remember { File(context.filesDir, "captures") }
    val uploadDirectory = remember { File(context.filesDir, "uploaded") }
    val uploader = remember { LocalCopyUploader(uploadDirectory) }

    var cameraAdapter by remember { mutableStateOf<CameraSourceAdapter?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wall Camera Agent — Phase 1.5A debug", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = state.organizationId,
            onValueChange = viewModel::onOrganizationIdChanged,
            label = { Text("organizationId") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.wallId,
            onValueChange = viewModel::onWallIdChanged,
            label = { Text("wallId") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.cameraDeviceId,
            onValueChange = viewModel::onCameraDeviceIdChanged,
            label = { Text("cameraDeviceId") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::saveIdentity) {
            Text(if (state.identitySaved) "Saved" else "Save device identity")
        }

        HorizontalDivider()

        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                factory = { viewContext ->
                    PreviewView(viewContext).also { previewView ->
                        val identity = viewModel.currentIdentityOrNull()
                        if (identity != null) {
                            val adapter = CameraXCameraSourceAdapter(
                                context = viewContext,
                                lifecycleOwner = lifecycleOwner,
                                previewView = previewView,
                                outputDirectory = captureDirectory,
                                config = CameraCaptureConfig(),
                                identity = identity,
                            )
                            cameraAdapter = adapter
                            scope.launch { adapter.bind() }
                        }
                    }
                },
            )
        } else {
            Text(
                "Camera permission not granted — grant it to bind a real " +
                    "CameraXCameraSourceAdapter. FakeCameraSourceAdapter is still usable below.",
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { cameraAdapter?.let { viewModel.captureReferenceFrame(it, uploader) } },
                enabled = cameraAdapter != null,
            ) { Text("Capture (real camera)") }

            Button(
                onClick = {
                    viewModel.currentIdentityOrNull()?.let { identity ->
                        val fake = FakeCameraSourceAdapter(captureDirectory, CameraCaptureConfig(), identity)
                        viewModel.captureReferenceFrame(fake, uploader)
                    }
                },
            ) { Text("Capture (fake)") }
        }

        Button(onClick = viewModel::sendHeartbeat) { Text("Send heartbeat") }
        Text("Heartbeat: ${state.heartbeatStatusText}")

        HorizontalDivider()

        state.lastCapturedFrame?.let { frame ->
            Text("Last captured frame", style = MaterialTheme.typography.titleMedium)
            Text("path: ${frame.filePath}")
            Text("size: ${frame.fileSizeBytes} bytes")
            Text("requested resolution: ${frame.metadata.requestedWidthPx}x${frame.metadata.requestedHeightPx}")
            Text("actual resolution: ${frame.metadata.widthPx}x${frame.metadata.heightPx}")
            Text("rotationDegrees: ${frame.metadata.rotationDegrees}")
            Text("mirrored: ${frame.metadata.mirrored}")
            Text("capturedAtEpochMs: ${frame.metadata.capturedAtEpochMs}")
            Text("requestedGeometryProfileVersion: ${frame.metadata.requestedGeometryProfileVersion}")
        }

        state.lastUploadReference?.let { ref -> Text("Last upload reference: $ref") }
        state.lastError?.let { err -> Text("Error: $err", color = MaterialTheme.colorScheme.error) }
    }
}
