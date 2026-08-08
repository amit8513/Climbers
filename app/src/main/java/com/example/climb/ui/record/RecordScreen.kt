package com.example.climb.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.climb.capture.CameraXController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun RecordScreen(
    moviesDir: File,
    onRecorded: (videoPath: String, durationMs: Long) -> Unit,
    countdownSeconds: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var hasAudioPermission by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to record climbs.")
        }
        return
    }

    val controller = remember { CameraXController(context) }
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var startTimeMs by remember { mutableStateOf(0L) }
    // Gives the climber time to set the phone down and get to the wall before the shot that's
    // actually analyzed starts, instead of recording beginning the instant they tap the button
    // (which otherwise eats into the attempt with setup time, or gets a climb cut off partway
    // through once they're finally in position).
    var countdownRemaining by remember { mutableStateOf(0) }

    fun beginRecording() {
        val file = File(moviesDir, "climb_${System.currentTimeMillis()}.mp4")
        pendingFile = file
        startTimeMs = System.currentTimeMillis()
        elapsedSeconds = 0
        statusMessage = null
        isRecording = true
        controller.startRecording(file) { success ->
            isRecording = false
            val recordedFile = pendingFile
            if (success && recordedFile != null) {
                val duration = System.currentTimeMillis() - startTimeMs
                onRecorded(recordedFile.absolutePath, duration)
            } else {
                statusMessage = "Recording failed — try again"
            }
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isImporting = true
            statusMessage = null
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    importVideo(context, uri, moviesDir)
                }
                isImporting = false
                if (imported != null) {
                    onRecorded(imported.first.absolutePath, imported.second)
                } else {
                    statusMessage = "Couldn't import that video — try again"
                }
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    LaunchedEffect(countdownRemaining) {
        if (countdownRemaining > 0) {
            delay(1000)
            countdownRemaining--
            if (countdownRemaining == 0) beginRecording()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    controller.bind(lifecycleOwner, previewView) {
                        statusMessage = "Camera error — try again"
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(bottom = 32.dp)),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            statusMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            if (isRecording) {
                Text(text = "${elapsedSeconds}s", color = MaterialTheme.colorScheme.onBackground)
            }
            if (isImporting) {
                Text(text = "Importing video...", color = MaterialTheme.colorScheme.onBackground)
            }
            Button(
                enabled = !isImporting && countdownRemaining == 0,
                modifier = Modifier.fillMaxWidth(0.6f),
                onClick = {
                    if (!isRecording) {
                        if (countdownSeconds > 0) {
                            countdownRemaining = countdownSeconds
                        } else {
                            beginRecording()
                        }
                    } else {
                        controller.stopRecording()
                    }
                },
            ) {
                Text(if (isRecording) "Stop" else "Record")
            }
            OutlinedButton(
                enabled = !isRecording && !isImporting && countdownRemaining == 0,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(top = 8.dp),
                onClick = {
                    pickVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
            ) {
                Text("Choose from gallery")
            }
        }

        if (countdownRemaining > 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "$countdownRemaining",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = "Get to the wall — recording starts in $countdownRemaining…",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }
    }
}

private fun importVideo(context: android.content.Context, uri: android.net.Uri, moviesDir: File): Pair<File, Long>? {
    return try {
        val destination = File(moviesDir, "climb_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(destination.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }

        destination to duration
    } catch (e: Exception) {
        null
    }
}
