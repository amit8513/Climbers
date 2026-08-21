package com.example.climb.ui.validation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.Limb
import com.example.climb.colordetection.Point2D
import com.example.climb.validation.ManualValidationFrameDiagnostics
import com.example.climb.validation.ManualValidationOutcome
import com.example.climb.validation.ManualValidationSession
import kotlinx.coroutines.delay
import java.io.File

/**
 * Phase 3B developer/debug screen: import a reference wall photo + a manually-recorded climbing
 * clip, annotate hold geometry, run the real MediaPipe→ContactPoseFrame→HoldContactDetector
 * pipeline, and visually compare "his right hand touched hold 7 here" against what the algorithm
 * actually detected. Entirely local/debug tooling — see `com.example.climb.validation`'s
 * trust-boundary doc comments for what this deliberately never touches.
 */
@Composable
fun ValidationDebugScreen() {
    val context = LocalContext.current
    val viewModel: ValidationDebugViewModel = viewModel(factory = ValidationDebugViewModel.factory(context.applicationContext))
    val state by viewModel.state.collectAsState()

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.importReferenceImage(it) }
    }
    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.importVideo(it) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Manual Validation Harness (Phase 3B)", style = MaterialTheme.typography.titleLarge)
        Text(
            "Local/debug only - never official club-camera data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer()

        SessionSetupSection(state, viewModel, onPickImage = { pickImageLauncher.launch(PickVisualMediaRequestImage) })
        Spacer()

        if (state.referenceImagePath != null) {
            HoldAnnotationSection(state, viewModel)
            Spacer()
        }

        VideoImportSection(state, viewModel, onPickVideo = { pickVideoLauncher.launch(PickVisualMediaRequestVideo) })
        Spacer()

        if (state.videoPath != null) {
            VideoPlaybackAndOverlaySection(state, viewModel)
            Spacer()
        }

        GroundTruthSection(state, viewModel)
        Spacer()

        RunAndReportSection(state, viewModel)
        Spacer()

        SavedSessionsSection(state, viewModel)

        state.statusMessage?.let {
            Spacer()
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Spacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
}

private val PickVisualMediaRequestImage = androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
private val PickVisualMediaRequestVideo = androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

@Composable
private fun SessionSetupSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel, onPickImage: () -> Unit) {
    Text("1. Session Setup", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.wallOrFixtureId,
        onValueChange = viewModel::updateWallOrFixtureId,
        label = { Text("wallOrFixtureId (e.g. \"gym-visit-2026-08-21\")") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.cameraGeometryProfileVersion.toString(),
        onValueChange = { it.toIntOrNull()?.let(viewModel::updateGeometryProfileVersion) },
        label = { Text("cameraGeometryProfileVersion") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.notes,
        onValueChange = viewModel::updateNotes,
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer()
    Button(onClick = onPickImage) { Text(if (state.referenceImagePath == null) "Import Reference Wall Photo" else "Re-import Reference Photo") }
    state.referenceImageDimensions?.let { Text("Reference dimensions: ${it.widthPx}x${it.heightPx}") }
}

@Composable
private fun HoldAnnotationSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("2. Hold Annotation", style = MaterialTheme.typography.titleMedium)
    Text("Tap the reference image to place vertices, then \"Finish Hold\".", style = MaterialTheme.typography.bodySmall)

    Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A))) {
        AnnotationCanvas(
            holds = state.holds,
            inProgressVertices = state.inProgressHoldVertices,
            onTap = { viewModel.addHoldVertex(it) },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::finishCurrentHold, enabled = state.inProgressHoldVertices.size >= 3) { Text("Finish Hold") }
        Button(onClick = viewModel::clearInProgressHold, enabled = state.inProgressHoldVertices.isNotEmpty()) { Text("Clear") }
    }

    Spacer()
    state.holds.forEach { hold ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Hold #${hold.holdId}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleToggle("Start", hold.holdId in state.startHoldIds) { viewModel.toggleStartHold(hold.holdId) }
                RoleToggle("Finish", hold.holdId in state.finishHoldIds) { viewModel.toggleFinishHold(hold.holdId) }
                Text("Remove", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { viewModel.removeHold(hold.holdId) })
            }
        }
    }
}

@Composable
private fun RoleToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(label, fontSize = 11.sp) }
}

@Composable
private fun AnnotationCanvas(holds: List<com.example.climb.validation.ValidationHoldAnnotation>, inProgressVertices: List<Point2D>, onTap: (Point2D) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures { offset ->
                        onTap(Point2D(offset.x / widthPx, offset.y / heightPx))
                    }
                },
        ) {
            holds.forEach { hold -> drawPolygon(hold.contourNormalized, Color.Yellow) }
            if (inProgressVertices.size >= 2) drawPolygon(inProgressVertices, Color.Cyan)
            inProgressVertices.forEach { vertex ->
                drawCircle(Color.Cyan, radius = 6f, center = Offset(vertex.x * size.width, vertex.y * size.height))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(points: List<Point2D>, color: Color) {
    if (points.size < 2) return
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        drawLine(
            color = color,
            start = Offset(a.x * size.width, a.y * size.height),
            end = Offset(b.x * size.width, b.y * size.height),
            strokeWidth = 3f,
        )
    }
}

@Composable
private fun VideoImportSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel, onPickVideo: () -> Unit) {
    Text("3. Climbing Video", style = MaterialTheme.typography.titleMedium)
    Button(onClick = onPickVideo) { Text(if (state.videoPath == null) "Import Climbing Video" else "Re-import Video") }
}

@Composable
private fun VideoPlaybackAndOverlaySection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Scrub & Overlay", style = MaterialTheme.typography.titleMedium)
    val context = LocalContext.current
    val videoPath = state.videoPath ?: return

    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(videoPath))))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    var durationMs by remember(videoPath) { mutableStateOf(1L) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.duration > 0) durationMs = exoPlayer.duration
            viewModel.updateScrubPosition(exoPlayer.currentPosition)
            delay(100L)
        }
    }

    Box(Modifier.fillMaxWidth().height(240.dp)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } })
        OverlayCanvas(state)
    }

    Slider(
        value = state.scrubPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
        onValueChange = { exoPlayer.seekTo(it.toLong()) },
        valueRange = 0f..durationMs.toFloat(),
    )
    Text("t = ${state.scrubPositionMs} ms")
}

@Composable
private fun OverlayCanvas(state: ValidationDebugUiState) {
    val diagnostics = nearestDiagnostics(state) ?: return
    Canvas(Modifier.fillMaxSize()) {
        state.holds.forEach { hold -> drawPolygon(hold.contourNormalized, Color.Yellow) }
        for (limb in Limb.entries) {
            val point = diagnostics.proxyPositionByLimb[limb] ?: continue
            val established = diagnostics.establishedHoldByLimb[limb]
            val candidate = diagnostics.candidateHoldByLimb[limb]
            val color = when {
                established != null -> Color.Green
                candidate != null -> Color(0xFFFFC107)
                else -> Color.White
            }
            drawCircle(color, radius = 10f, center = Offset(point.x * size.width, point.y * size.height))
        }
    }
}

private fun nearestDiagnostics(state: ValidationDebugUiState): ManualValidationFrameDiagnostics? {
    val processed = state.outcome as? ManualValidationOutcome.Processed ?: return null
    return processed.frameDiagnostics.minByOrNull { kotlin.math.abs(it.timestampMs - state.scrubPositionMs) }
}

@Composable
private fun GroundTruthSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("4. Ground Truth (optional)", style = MaterialTheme.typography.titleMedium)
    Text(
        "\"Yes, his right hand touched hold 7 here\" - your own observation, for comparison only.",
        style = MaterialTheme.typography.bodySmall,
    )

    var selectedLimb by remember { mutableStateOf(Limb.LEFT_HAND) }
    var holdIdText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Limb.entries.forEach { limb ->
            RoleToggle(limb.name, selectedLimb == limb) { selectedLimb = limb }
        }
    }
    OutlinedTextField(value = holdIdText, onValueChange = { holdIdText = it }, label = { Text("holdId") })
    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("note (optional)") })
    Button(onClick = {
        holdIdText.toIntOrNull()?.let { holdId ->
            viewModel.addGroundTruthContact(selectedLimb, holdId, state.scrubPositionMs, note.ifBlank { null })
            holdIdText = ""
            note = ""
        }
    }) { Text("Add at current scrub position (t=${state.scrubPositionMs}ms)") }

    state.groundTruthContacts.forEachIndexed { index, gt ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${gt.limb} hold #${gt.holdId} @ ${gt.approxTimestampMs}ms ${gt.note.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            Text("Remove", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { viewModel.removeGroundTruthContact(index) })
        }
    }
}

@Composable
private fun RunAndReportSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("5. Run & Report", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::runAnalysis, enabled = !state.isProcessing) { Text(if (state.isProcessing) "Processing..." else "Run Analysis") }
        Button(onClick = viewModel::saveCurrentSession) { Text("Save Session") }
    }
    state.progress?.let { Text("${it.phase}: ${(it.fractionComplete * 100).toInt()}%") }

    when (val outcome = state.outcome) {
        is ManualValidationOutcome.Rejected -> Text("Rejected: ${outcome.reason}", color = MaterialTheme.colorScheme.error)
        is ManualValidationOutcome.Processed -> state.report?.let { ReportView(it) }
        null -> Unit
    }
}

@Composable
private fun ReportView(report: com.example.climb.validation.ManualValidationReport) {
    HorizontalDivider()
    Text("Pose frames: ${report.poseFrameCount}")
    Text("Pose-confidence coverage: ${"%.1f".format(report.poseConfidenceCoveragePercent)}%")
    Text("Established contact events: ${report.establishedEventCount}")
    Limb.entries.forEach { limb -> Text("  $limb: ${report.contactsPerLimb[limb] ?: 0}") }
    Text("Hold IDs touched: ${report.holdIdsTouched.sorted()}")
    Text("Short gaps encountered: ${report.shortGapCount}")
    Text("Long-gap resets: ${report.longGapResetCount}")
    Text("Implausible-jump resets: ${report.implausibleJumpResetCount}")
    Text("Low-confidence periods: ${report.lowConfidencePeriodCount}")
    val comparison = report.groundTruthComparison
    if (comparison == null) {
        Text("No ground truth supplied - no accuracy claim.", color = MaterialTheme.colorScheme.error)
    } else {
        Text("True detected: ${comparison.trueDetectedContacts}, missed: ${comparison.missedContacts}, false: ${comparison.falseContacts}")
        Text("Approx. timing error: ${comparison.approximateContactTimingErrorMs ?: "n/a"} ms")
    }
    Text("Timeline events:")
    report.timeline.events.forEach { event ->
        Text("  t=${event.timestampMs}ms ${event.limb} ${event.type} hold#${event.holdId} conf=${"%.2f".format(event.confidence)} ${event.evidenceQuality}${event.releaseReason?.let { " ($it)" } ?: ""}")
    }
}

@Composable
private fun SavedSessionsSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Saved Sessions", style = MaterialTheme.typography.titleMedium)
    state.savedSessions.forEach { session: ManualValidationSession ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${session.wallOrFixtureId} - ${session.validationSessionId.take(8)}", modifier = Modifier.clickable { viewModel.loadSession(session) })
            Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { viewModel.deleteSession(session.validationSessionId) })
        }
    }
}
