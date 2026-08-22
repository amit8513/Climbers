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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.analysis.contact.GapState
import com.example.climb.analysis.contact.Limb
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.attribution.RouteCandidate
import com.example.climb.clubs.AttemptResult
import com.example.climb.clubs.AttributionStatus
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartEvidenceStatus
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.Point2D
import com.example.climb.validation.BatchQueueItem
import com.example.climb.validation.CacheOutcome
import com.example.climb.validation.ClipBatchStatus
import com.example.climb.validation.ManualValidationFrameDiagnostics
import com.example.climb.validation.ManualValidationOutcome
import com.example.climb.validation.ManualValidationSession
import com.example.climb.validation.MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT
import com.example.climb.validation.StageProvenance
import com.example.climb.validation.ValidationPipelineProvenance
import com.example.climb.validation.ValidationRouteDefinition
import com.example.climb.validation.ValidationWallSetup
import com.example.climb.validation.eventsNearTimestamp
import com.example.climb.validation.finishHoldEventsFor
import com.example.climb.validation.foreignContactEvents
import com.example.climb.validation.normalizedWeightsUsed
import com.example.climb.validation.secondPlaceCandidate
import com.example.climb.validation.startHoldEventsFor
import com.example.climb.validation.toHumanReadableSummary
import com.example.climb.validation.toJson
import com.example.climb.validation.toRouteCandidate
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

        CandidateRoutesSection(state, viewModel)
        Spacer()

        WallSetupsSection(state, viewModel)
        Spacer()

        VideoImportSection(state, viewModel, onPickVideo = { pickVideoLauncher.launch(PickVisualMediaRequestVideo) })
        Spacer()

        if (state.videoPath != null) {
            VideoPlaybackAndOverlaySection(state, viewModel)
            Spacer()
        }

        GroundTruthSection(state, viewModel)
        Spacer()

        PreFlightChecklistSection(state, viewModel)
        Spacer()

        RunAndReportSection(state, viewModel)
        Spacer()

        RouteAttributionSection(state, viewModel)
        Spacer()

        ExportAndDatasetSection(state, viewModel)
        Spacer()

        SavedSessionsSection(state, viewModel)
        Spacer()

        BatchValidationSection(state, viewModel)

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

/** Phase 4B: lets the developer define 1+ [ValidationRouteDefinition]s from the holds already
 * annotated in [HoldAnnotationSection], so [ManualValidationAttributionRunner] (invoked from
 * [ValidationDebugViewModel.runAnalysis]) has real candidates to feed into
 * `RouteAttributionEngine`. `corridorNormalized` is intentionally never set from this UI pass -
 * every route defined here has `corridorNormalized = null`, so corridor evidence will correctly
 * report UNAVAILABLE for every candidate. That is a real, already-handled scoring state, not a
 * gap left to fix later. */
@Composable
private fun CandidateRoutesSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Candidate Routes", style = MaterialTheme.typography.titleMedium)

    var name by remember { mutableStateOf("") }
    var selectedStartPolicy by remember { mutableStateOf(StartPolicy.SINGLE_HOLD_ANY_HAND) }
    var startHoldIdsText by remember { mutableStateOf("") }
    var bodyHoldIdsText by remember { mutableStateOf("") }
    var selectedFinishPolicy by remember { mutableStateOf<FinishPolicy?>(null) }
    var finishHoldIdsText by remember { mutableStateOf("") }

    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Route name") }, modifier = Modifier.fillMaxWidth())

    Spacer()
    Text("Start policy", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StartPolicy.entries.forEach { policy ->
            RoleToggle(policy.name, selectedStartPolicy == policy) { selectedStartPolicy = policy }
        }
    }
    OutlinedTextField(
        value = startHoldIdsText,
        onValueChange = { startHoldIdsText = it },
        label = { Text("start hold ids (comma-separated)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = bodyHoldIdsText,
        onValueChange = { bodyHoldIdsText = it },
        label = { Text("body hold ids (comma-separated, optional)") },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer()
    Text("Finish policy", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoleToggle("None", selectedFinishPolicy == null) { selectedFinishPolicy = null; finishHoldIdsText = "" }
        FinishPolicy.entries.forEach { policy ->
            RoleToggle(policy.name, selectedFinishPolicy == policy) { selectedFinishPolicy = policy }
        }
    }
    OutlinedTextField(
        value = finishHoldIdsText,
        onValueChange = { finishHoldIdsText = it },
        label = { Text("finish hold ids (comma-separated)") },
        modifier = Modifier.fillMaxWidth(),
        enabled = selectedFinishPolicy != null,
    )

    Spacer()
    Button(onClick = {
        val parsedStartHoldIds = parseHoldIdsLenient(startHoldIdsText)
        if (name.isBlank() || parsedStartHoldIds.isEmpty()) return@Button
        val finishPolicy = selectedFinishPolicy
        val parsedFinishHoldIds = if (finishPolicy != null) parseHoldIdsLenient(finishHoldIdsText) else emptySet()
        // A FinishPolicy pill selected with no parseable finish hold id is an incomplete form
        // entry, not a valid "no finish" route - ignore rather than crash
        // ValidationRouteDefinition's own finishHoldIds/finishPolicy nullness-must-match invariant.
        if (finishPolicy != null && parsedFinishHoldIds.isEmpty()) return@Button

        val nextRouteId = (state.routeDefinitions.maxOfOrNull { it.routeId } ?: 0L) + 1L
        viewModel.addRouteDefinition(
            ValidationRouteDefinition(
                routeId = nextRouteId,
                name = name,
                startHoldIds = parsedStartHoldIds,
                startPolicy = selectedStartPolicy,
                bodyHoldIds = parseHoldIdsLenient(bodyHoldIdsText),
                finishHoldIds = parsedFinishHoldIds,
                finishPolicy = finishPolicy,
                corridorNormalized = null,
            ),
        )
        name = ""
        startHoldIdsText = ""
        bodyHoldIdsText = ""
        finishHoldIdsText = ""
        selectedFinishPolicy = null
        selectedStartPolicy = StartPolicy.SINGLE_HOLD_ANY_HAND
    }) { Text("Add Route") }

    Spacer()
    state.routeDefinitions.forEach { route ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${route.routeId} ${route.name}")
                Text("Remove", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { viewModel.removeRouteDefinition(route.routeId) })
            }
            Text("start: ${route.startPolicy} holds=${route.startHoldIds.sorted()}", style = MaterialTheme.typography.bodySmall)
            Text("body holds=${route.bodyHoldIds.sorted()}", style = MaterialTheme.typography.bodySmall)
            Text("finish: ${route.finishPolicy?.name ?: "None"} holds=${route.finishHoldIds.sorted()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Parses a comma-separated hold-id list leniently - blank/non-numeric tokens are silently
 * dropped rather than rejecting the whole field, matching this screen's existing lenient-input
 * style (e.g. [finishCurrentHold]'s size check before acting). */
private fun parseHoldIdsLenient(text: String): Set<Int> =
    text.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

/** Phase 4B: save/re-apply a physical wall's reference photo + annotated holds + candidate routes
 * across multiple clips filmed against the same wall, via [ValidationWallSetup]. */
@Composable
private fun WallSetupsSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Wall Setups", style = MaterialTheme.typography.titleMedium)
    Button(onClick = { viewModel.saveCurrentWallSetup() }, enabled = state.referenceImagePath != null) {
        Text("Save Current Wall Setup")
    }

    Spacer()
    state.savedWallSetups.forEach { setup: ValidationWallSetup ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${setup.wallOrFixtureId} - ${setup.wallSetupId.take(8)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apply", modifier = Modifier.clickable { viewModel.applyWallSetup(setup) })
                Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { viewModel.deleteWallSetup(setup.wallSetupId) })
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
    Button(onClick = viewModel::markAttemptStartAtScrub) { Text("Mark Attempt Start Here") }
    Text("attempt start: ${state.attemptStartTimestampMs} ms")

    AttributionDebugAtCurrentTimeSection(state)
}

@Composable
private fun OverlayCanvas(state: ValidationDebugUiState) {
    val diagnostics = nearestDiagnostics(state) ?: return
    val focusedCandidate = focusedRouteCandidate(state)
    Canvas(Modifier.fillMaxSize()) {
        state.holds.forEach { hold -> drawPolygon(hold.contourNormalized, holdOverlayColor(hold.holdId, focusedCandidate)) }
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

/** [state.focusedRouteId]'s [ValidationRouteDefinition], projected into Phase 4A's real
 * [RouteCandidate] shape via [toRouteCandidate] - `null` whenever nothing is focused yet, or the
 * focused id no longer matches any currently-defined route (e.g. it was just removed). */
private fun focusedRouteCandidate(state: ValidationDebugUiState): RouteCandidate? {
    val focusedId = state.focusedRouteId ?: return null
    return state.routeDefinitions.firstOrNull { it.routeId == focusedId }?.toRouteCandidate()
}

/** Phase 4B: colors one hold polygon by its role relative to [focusedCandidate] - start holds
 * blue, finish holds purple, body holds a neutral grey, and every hold not belonging to the
 * focused candidate (or when nothing is focused at all) left as [OverlayCanvas]'s original default
 * yellow. */
private fun holdOverlayColor(holdId: Int, focusedCandidate: RouteCandidate?): Color = when {
    focusedCandidate == null -> Color.Yellow
    holdId in focusedCandidate.startHoldIds -> Color(0xFF2196F3)
    holdId in focusedCandidate.finishHoldIds -> Color(0xFF9C27B0)
    holdId in focusedCandidate.bodyHoldIds -> Color(0xFFB0BEC5)
    else -> Color.Yellow
}

/** Phase 4B: the "what's happening right now, for the currently-focused candidate" live readout
 * below the scrub overlay - active only once both a [ValidationDebugUiState.attributionResult] and
 * a [ValidationDebugUiState.focusedRouteId] exist. Every number/event shown here is read straight
 * off already-computed [com.example.climb.validation.AttributionDebugDetails] helpers or the
 * existing per-frame diagnostics - no new decision logic. */
@Composable
private fun AttributionDebugAtCurrentTimeSection(state: ValidationDebugUiState) {
    if (state.attributionResult == null) return
    val focusedId = state.focusedRouteId ?: return
    val focusedDefinition = state.routeDefinitions.firstOrNull { it.routeId == focusedId } ?: return
    val timeline = (state.outcome as? ManualValidationOutcome.Processed)?.timeline ?: return

    val focusedCandidate = focusedDefinition.toRouteCandidate()
    val allCandidates = state.routeDefinitions.map { it.toRouteCandidate() }
    val config = RouteAttributionScoringConfig()
    val diagnostics = nearestDiagnostics(state)

    Spacer()
    HorizontalDivider()
    Text("Attribution Debug At Current Time", style = MaterialTheme.typography.titleMedium)
    Text("Focused route: #$focusedId ${focusedDefinition.name}", style = MaterialTheme.typography.bodySmall)
    Text("t = ${state.scrubPositionMs} ms", style = MaterialTheme.typography.bodySmall)

    if (diagnostics == null) {
        Text("No pose diagnostics near this timestamp yet.", style = MaterialTheme.typography.bodySmall)
    } else {
        for (limb in Limb.entries) {
            val holdId = diagnostics.establishedHoldByLimb[limb] ?: diagnostics.candidateHoldByLimb[limb]
            val kind = when {
                diagnostics.establishedHoldByLimb[limb] != null -> "established"
                diagnostics.candidateHoldByLimb[limb] != null -> "candidate"
                else -> "none"
            }
            val ownership = when {
                holdId == null -> ""
                holdId in focusedCandidate.allHoldIds -> " (focused route)"
                else -> " (foreign)"
            }
            Text("  $limb: $kind hold=${holdId ?: "-"}$ownership", style = MaterialTheme.typography.bodySmall)
        }
    }

    Text("Contacts near this moment (±1000ms):", style = MaterialTheme.typography.bodySmall)
    val nearbyEvents = eventsNearTimestamp(timeline, state.scrubPositionMs, 1000L)
    if (nearbyEvents.isEmpty()) {
        Text("  none", style = MaterialTheme.typography.bodySmall)
    } else {
        nearbyEvents.forEach { event ->
            Text("  ${event.limb} hold#${event.holdId} ${event.type} @${event.timestampMs}ms", style = MaterialTheme.typography.bodySmall)
        }
    }

    Text("Foreign contacts (against focused route):", style = MaterialTheme.typography.bodySmall)
    val foreignEvents = foreignContactEvents(focusedCandidate, allCandidates, timeline, config)
    if (foreignEvents.isEmpty()) {
        Text("  none", style = MaterialTheme.typography.bodySmall)
    } else {
        foreignEvents.forEach { event ->
            Text("  ${event.limb} hold#${event.holdId} @${event.timestampMs}ms", style = MaterialTheme.typography.bodySmall)
        }
    }

    val startObserved = startHoldEventsFor(focusedCandidate, timeline).any { it.timestampMs <= state.scrubPositionMs }
    Text("start evidence: ${if (startObserved) "observed" else "not yet observed"}", style = MaterialTheme.typography.bodySmall)
    val finishObserved = finishHoldEventsFor(focusedCandidate, timeline).any { it.timestampMs <= state.scrubPositionMs }
    Text("finish evidence: ${if (finishObserved) "observed" else "not yet observed"}", style = MaterialTheme.typography.bodySmall)
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

    Spacer()
    HorizontalDivider()
    Text("Expected attempt outcome (optional, for accuracy comparison)", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = state.expectedRouteId,
        onValueChange = viewModel::updateExpectedRouteId,
        label = { Text("expected route id (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AttemptResult.entries.forEach { result ->
            RoleToggle(result.name, state.expectedResult == result) { viewModel.updateExpectedResult(result) }
        }
    }
}

/** Phase 4C: "can Run Analysis actually be pressed right now" checklist, placed right where the
 * developer decides whether to hit Run Analysis - reads [ValidationDebugViewModel.currentPreflightCheck]
 * fresh on every recomposition (a cheap, pure, best-effort computation - see that function's own doc
 * comment), never a stale cached value. Every line is purely informational except the ones
 * [ValidationPreflightCheck.canRunAnalysis] itself already keys off of - the actual gating happens in
 * [RunAndReportSection]'s Run Analysis button, not here. */
@Composable
private fun PreFlightChecklistSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    val preflight = viewModel.currentPreflightCheck()
    Text("Pre-Flight Checklist", style = MaterialTheme.typography.titleMedium)
    ChecklistLine("Reference image", preflight.referenceImagePresent)
    ChecklistLine("Geometry compatible", preflight.geometryCompatible)
    ChecklistLine("Hold annotations", preflight.holdsAnnotated)
    ChecklistLine("2+ candidate routes", preflight.hasTwoOrMoreRoutes)
    ChecklistLine("Expected route (optional)", preflight.expectedRouteLabeled)
    ChecklistLine("Video readable", preflight.videoReadable)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pose artifact")
        Text(
            if (preflight.poseArtifactCached) "CACHED" else "NONE",
            color = if (preflight.poseArtifactCached) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One Pre-Flight Checklist row - a green checkmark for `true`, a red/error-colored cross for
 * `false`, matching this file's existing green-for-good ([RouteAttributionSection]'s
 * `Color(0xFF2E7D32)`) / [MaterialTheme.colorScheme.error]-for-bad color convention. */
@Composable
private fun ChecklistLine(label: String, ok: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
        Text(label)
    }
}

@Composable
private fun RunAndReportSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("5. Run & Report", style = MaterialTheme.typography.titleMedium)
    val preflight = viewModel.currentPreflightCheck()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::runAnalysis, enabled = !state.isProcessing && preflight.canRunAnalysis) {
            Text(if (state.isProcessing) "Processing..." else "Run Analysis")
        }
        Button(onClick = viewModel::saveCurrentSession) { Text("Save Session") }
    }
    if (!preflight.canRunAnalysis) {
        Text(
            preflight.blockingReasons.joinToString(" - "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    state.progress?.let { Text("${it.phase}: ${(it.fractionComplete * 100).toInt()}%") }

    state.pipelineProvenance?.let { PipelineProvenanceReadout(it) }

    val lowPoseCoverage = state.report?.let { it.poseConfidenceCoveragePercent < MIN_ACCEPTABLE_POSE_COVERAGE_PERCENT } ?: false
    if (lowPoseCoverage) {
        Text("LOW POSE COVERAGE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }

    state.pipelineError?.let { pipelineError ->
        Text("Pipeline error: ${pipelineError.code} - ${pipelineError.message}", color = MaterialTheme.colorScheme.error)
    }

    when (val outcome = state.outcome) {
        is ManualValidationOutcome.Rejected -> Text("Rejected: ${outcome.reason}", color = MaterialTheme.colorScheme.error)
        is ManualValidationOutcome.Processed -> state.report?.let { ReportView(it) }
        null -> Unit
    }
}

/** Phase 4C: one line per pipeline stage showing whether its most recent run was a cache hit or a
 * real recompute - shown near the top of [RunAndReportSection]'s output, above the existing report
 * display. Contact/attribution lines are omitted entirely when that stage's provenance is `null`
 * (the run never reached it), per [ValidationPipelineProvenance]'s own doc comment. */
@Composable
private fun PipelineProvenanceReadout(provenance: ValidationPipelineProvenance) {
    HorizontalDivider()
    Text("Pipeline Provenance", style = MaterialTheme.typography.titleMedium)
    ProvenanceLine("Pose", provenance.pose)
    provenance.contact?.let { ProvenanceLine("Contact", it) }
    provenance.attribution?.let { ProvenanceLine("Attribution", it) }
}

@Composable
private fun ProvenanceLine(stageName: String, stage: StageProvenance) {
    val statusText = when (stage.outcome) {
        CacheOutcome.CACHE_HIT -> "CACHE HIT"
        CacheOutcome.RECOMPUTED -> "RECOMPUTED"
    }
    Text("$stageName: $statusText", style = MaterialTheme.typography.bodySmall)
    stage.invalidationReason?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Phase 4B: renders `com.example.climb.attribution.RouteAttributionEngine`'s output for the
 * current session, once [ValidationDebugViewModel.runAnalysis] has actually run - one row per
 * candidate, plus start-evidence detail lines and a "Focus" toggle wired to
 * [ValidationDebugViewModel.setFocusedRoute]. Every number rendered here is read straight off the
 * already-final `AttributionResult`/`SubScoreResult` values, or off the display-only
 * `com.example.climb.validation.AttributionDebugDetails` helpers - no new scoring/gating decision
 * is made in this composable. */
@Composable
private fun RouteAttributionSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Route Attribution (Phase 4B)", style = MaterialTheme.typography.titleMedium)
    val result = state.attributionResult ?: run {
        Text("Run analysis to see attribution results.", style = MaterialTheme.typography.bodySmall)
        return
    }

    val statusColor = when (result.status) {
        AttributionStatus.VERIFIED -> Color(0xFF2E7D32)
        AttributionStatus.REVIEW_REQUIRED -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.error
    }
    Text("Decision: ${result.status} (${result.reasonCode ?: "-"})", color = statusColor)
    Text("Margin: ${result.margin ?: "n/a"}", color = statusColor)
    if (result.status == AttributionStatus.REVIEW_REQUIRED) {
        Text("Margin below threshold - human review needed", color = Color(0xFFFFA000))
    }

    if (result.subScores.isEmpty()) {
        Text("No candidate routes defined for this session yet - add one or more in the Candidate Routes section above.")
        return
    }

    val routesById = state.routeDefinitions.associateBy { it.routeId }
    val secondPlaceId = secondPlaceCandidate(result)?.routeVersionId
    val timeline = (state.outcome as? ManualValidationOutcome.Processed)?.timeline
    val config = RouteAttributionScoringConfig()

    result.subScores.forEach { subScore ->
        val definition = routesById[subScore.routeVersionId]
        val displayName = definition?.name ?: "route-${subScore.routeVersionId}"
        val hardGated = subScore.startEvidenceStatus != StartEvidenceStatus.START_OBSERVED_MATCH
        val weights = normalizedWeightsUsed(subScore, config)

        val background = when {
            subScore.routeVersionId == result.winningRouteVersionId -> Color(0xFFC8E6C9)
            subScore.routeVersionId == secondPlaceId -> Color(0xFFE8F5E9)
            hardGated -> Color(0xFFE0E0E0)
            else -> Color.Transparent
        }
        val textColor = if (hardGated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${subScore.routeVersionId} $displayName", color = textColor)
                RoleToggle(
                    if (state.focusedRouteId == subScore.routeVersionId) "Focused" else "Focus",
                    state.focusedRouteId == subScore.routeVersionId,
                ) { viewModel.setFocusedRoute(subScore.routeVersionId) }
            }
            Text("startEvidenceStatus: ${subScore.startEvidenceStatus}", style = MaterialTheme.typography.bodySmall, color = textColor)
            Text("Hard-gated: ${if (hardGated) "yes" else "no"}", style = MaterialTheme.typography.bodySmall, color = textColor)
            Text("contactCoverageScore: ${subScore.contactCoverageScore}", style = MaterialTheme.typography.bodySmall, color = textColor)
            Text("corridorScore: ${subScore.corridorScore?.toString() ?: "UNAVAILABLE"}", style = MaterialTheme.typography.bodySmall, color = textColor)
            Text("finishScore: ${subScore.finishScore?.toString() ?: "UNAVAILABLE"}", style = MaterialTheme.typography.bodySmall, color = textColor)
            Text(
                "foreignContactEventCount: ${subScore.foreignContactEventCount}, foreignContactPenalty: ${subScore.foreignContactPenalty}",
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
            Text(
                "weights used: start=${weights.startHoldWeight}, contactCoverage=${weights.contactCoverageWeight}, " +
                    "corridor=${weights.corridorWeight}, finish=${weights.finishWeight}",
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
            Text("combinedScore: ${subScore.combinedScore}", style = MaterialTheme.typography.bodySmall, color = textColor)

            if (definition != null && timeline != null) {
                Text("Start evidence details:", style = MaterialTheme.typography.bodySmall, color = textColor)
                val startEvents = startHoldEventsFor(definition.toRouteCandidate(), timeline)
                if (startEvents.isEmpty()) {
                    Text("  none", style = MaterialTheme.typography.bodySmall, color = textColor)
                } else {
                    startEvents.forEach { event ->
                        Text(
                            "  ${event.limb} ${event.type} @${event.timestampMs}ms quality=${event.evidenceQuality}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}

/** Phase 4B: local export preview + dataset rollup - "Preview Export" builds+persists+displays a
 * [com.example.climb.validation.ClipValidationExport] for the current session, "Compute Dataset
 * Summary" tallies every locally-saved export into a
 * [com.example.climb.validation.ValidationDatasetSummary]. Neither button re-runs pose extraction
 * or the attribution engine - both call already-existing [ValidationDebugViewModel] entry points
 * that only assemble/tally already-computed values. */
@Composable
private fun ExportAndDatasetSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Export & Dataset", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::buildAndPersistExport) { Text("Preview Export") }
        Button(onClick = viewModel::computeDatasetSummary) { Text("Compute Dataset Summary") }
    }

    state.currentExport?.let { export ->
        Spacer()
        Text("Export JSON:", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).verticalScroll(rememberScrollState()).padding(8.dp)) {
            SelectionContainer { Text(export.toJson(), style = MaterialTheme.typography.bodySmall) }
        }
        Spacer()
        Text("Export Summary:", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).verticalScroll(rememberScrollState()).padding(8.dp)) {
            SelectionContainer { Text(export.toHumanReadableSummary(), style = MaterialTheme.typography.bodySmall) }
        }
    }

    state.datasetSummary?.let { summary ->
        Spacer()
        HorizontalDivider()
        Text(
            "FALSE VERIFIED ROUTE ASSIGNMENTS: ${summary.wrongWinners}",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Text("Videos processed: ${summary.videosProcessed}")
        Text("Correct winners: ${summary.correctWinners}")
        Text("Verified correct: ${summary.verifiedCorrectCount}")
        Text("Verified incorrect: ${summary.verifiedIncorrectCount}")
        Text("Review required: ${summary.reviewRequiredCount}")
        Text("Unresolved: ${summary.unresolvedCount}")
        Text("Not labeled: ${summary.notLabeledCount}")
        Text("Total labeled clips: ${summary.totalLabeledClips}")
        Text("Clips rejected before attribution: ${summary.clipsRejectedBeforeAttribution}")
        Text("Clips with low pose coverage: ${summary.clipsWithLowPoseCoverage}")
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

/** Phase 4C: lets the developer check off a subset of [ValidationDebugUiState.savedSessions] (e.g.
 * tomorrow's 10-15 real clips) and run them all through [ValidationDebugViewModel.runBatch] one at
 * a time, with live per-clip progress read straight off [ValidationDebugUiState.batchItems] /
 * [ValidationDebugUiState.batchProgress] - no new decision logic here, purely a UI over the
 * already-existing batch-queue plumbing in [ValidationDebugViewModel]. Placed right next to
 * [SavedSessionsSection] since it operates over that same saved-sessions list. */
@Composable
private fun BatchValidationSection(state: ValidationDebugUiState, viewModel: ValidationDebugViewModel) {
    Text("Batch Validation", style = MaterialTheme.typography.titleMedium)
    Text(
        "Select saved sessions below to run them through the pipeline one at a time (e.g. tomorrow's real clips).",
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer()
    state.savedSessions.forEach { session: ManualValidationSession ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = session.validationSessionId in state.batchSelectedSessionIds,
                    onCheckedChange = { viewModel.toggleBatchSelection(session.validationSessionId) },
                )
                Text("${session.wallOrFixtureId} - ${session.validationSessionId.take(8)}")
            }
        }
    }

    Spacer()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.selectAllForBatch(state.savedSessions.map { it.validationSessionId }) }) {
            Text("Select All")
        }
        Button(onClick = viewModel::clearBatchSelection) { Text("Clear Selection") }
    }

    Spacer()
    Button(
        onClick = viewModel::runBatch,
        enabled = state.batchSelectedSessionIds.isNotEmpty() && !state.isBatchRunning,
    ) { Text("Run Batch (${state.batchSelectedSessionIds.size} selected)") }

    if (state.isBatchRunning) {
        Spacer()
        val (completed, total) = state.batchProgress ?: (0 to 0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$completed / $total")
            Button(onClick = viewModel::cancelBatch) { Text("Cancel") }
        }
    }

    if (state.batchItems.isNotEmpty()) {
        Spacer()
        HorizontalDivider()
        Text("Batch Items", style = MaterialTheme.typography.bodySmall)
        state.batchItems.forEach { item -> BatchItemRow(item, viewModel) }
    }
}

/** One [BatchQueueItem]'s row in [BatchValidationSection] - status text colored per this file's
 * existing convention (green-for-good, error-color for FAILED/CANCELLED, an amber/progress color
 * for the in-flight stages, matching [PreFlightChecklistSection]/[RouteAttributionSection]'s own
 * palette), plus an inline error message + Retry button whenever this item is FAILED. */
@Composable
private fun BatchItemRow(item: BatchQueueItem, viewModel: ValidationDebugViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.validationSessionId.take(8)}", style = MaterialTheme.typography.bodySmall)
            Text(item.status.name, color = batchStatusColor(item.status), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
        if (item.status == ClipBatchStatus.FAILED) {
            item.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Retry",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { viewModel.retryClip(item.validationSessionId) },
            )
        }
    }
}

/** Maps a [ClipBatchStatus] to this file's existing color convention: [Color(0xFF2E7D32)] (green)
 * for success, [MaterialTheme.colorScheme.error] for FAILED/CANCELLED, the same amber
 * ([Color(0xFFFFA000)]) [RouteAttributionSection] already uses for REVIEW_REQUIRED for the
 * in-progress stages, and a neutral color for NOT_RUN. */
@Composable
private fun batchStatusColor(status: ClipBatchStatus): Color = when (status) {
    ClipBatchStatus.COMPLETE -> Color(0xFF2E7D32)
    ClipBatchStatus.FAILED, ClipBatchStatus.CANCELLED -> MaterialTheme.colorScheme.error
    ClipBatchStatus.EXTRACTING_POSE, ClipBatchStatus.CONTACT_ANALYSIS, ClipBatchStatus.ATTRIBUTION -> Color(0xFFFFA000)
    ClipBatchStatus.NOT_RUN -> MaterialTheme.colorScheme.onSurfaceVariant
}
