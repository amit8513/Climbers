package com.example.climb.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.colordetection.DebugCoordinateMapper
import com.example.climb.colordetection.PixelBuffer
import com.example.climb.colordetection.RouteColorDetector
import com.example.climb.colordetection.RouteColorProfiles
import com.example.climb.data.ClimbRepository
import com.example.climb.playback.HoldHighlightPipeline
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CANDIDATE_COLOR = Color(0xFFFFD23D) // amber — raw Phase 3 seed, before refinement/validation
private val ACCEPTED_COLOR = Color(0xFF43A047) // green — Phase 5 validated survivor
private val REJECTED_COLOR = Color(0xFFE53935) // red — Phase 4-refined but Phase 5-rejected

private sealed interface DebugLoadState {
    object Loading : DebugLoadState
    data class Ready(
        val referenceFrame: Bitmap,
        val debugResult: RouteColorDetector.DebugDetectionResult,
    ) : DebugLoadState
    data class Failed(val message: String) : DebugLoadState
}

/**
 * Phase 7 ("Debug/Tuning Tools") developer-only visualization: runs
 * [RouteColorDetector.detectWithDebugInfo] on a climb's reference frame and draws every pipeline
 * stage over it — raw Phase-3 candidate boxes, Phase-5-accepted hold contours, and Phase-4-refined
 * but Phase-5-rejected holds — with per-hold numeric readouts, so a developer tuning thresholds can
 * see exactly what passed, what got rejected, and why. Only reachable from a
 * `BuildConfig.DEBUG`-gated entry point in [DetailScreen] — never shown in a release build.
 */
@Composable
fun HoldDetectionDebugScreen(
    climbId: Long,
    repository: ClimbRepository,
    currentUid: String,
    onBack: () -> Unit,
) {
    val climb by repository.observeById(climbId, currentUid).collectAsStateWithLifecycle(initialValue = null)
    val currentClimb = climb

    Column(modifier = Modifier.fillMaxSize().wallTexture().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "← Back",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text(text = "Hold detection debug", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (currentClimb == null) {
            Text("Loading…", color = ClimbPalette.textSecondary, modifier = Modifier.padding(16.dp))
            return@Column
        }

        var state by remember(currentClimb.id) { mutableStateOf<DebugLoadState>(DebugLoadState.Loading) }
        LaunchedEffect(currentClimb.videoPath, currentClimb.routeColor, currentClimb.hueOffsetDegrees, currentClimb.hueToleranceDegrees) {
            state = DebugLoadState.Loading
            state = withContext(Dispatchers.Default) {
                runCatching {
                    val frame = HoldHighlightPipeline.extractReferenceFrame(currentClimb.videoPath)
                    val targetModel = RouteColorProfiles.defaultFor(currentClimb.routeColor)
                    val buffer = PixelBuffer.fromBitmap(frame)
                    val debugResult = RouteColorDetector.detectWithDebugInfo(buffer, targetModel)
                    DebugLoadState.Ready(frame, debugResult) as DebugLoadState
                }.getOrElse { error -> DebugLoadState.Failed(error.message ?: "Detection failed") }
            }
        }

        when (val s = state) {
            is DebugLoadState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = ClimbPalette.chalk) }

            is DebugLoadState.Failed -> Text(
                text = "Couldn't run detection: ${s.message}",
                color = ClimbPalette.fell,
                modifier = Modifier.padding(16.dp),
            )

            is DebugLoadState.Ready -> {
                if (s.debugResult.candidates.isEmpty()) {
                    Text(
                        text = "No Phase 3 candidates found for ${currentClimb.routeColor} in this reference frame.",
                        color = ClimbPalette.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    DebugOverlayCanvas(referenceFrame = s.referenceFrame, debugResult = s.debugResult)
                    Legend()
                    HoldReadouts(debugResult = s.debugResult)
                }
            }
        }
    }
}

@Composable
private fun DebugOverlayCanvas(referenceFrame: Bitmap, debugResult: RouteColorDetector.DebugDetectionResult) {
    val imageBitmap = remember(referenceFrame) { referenceFrame.asImageBitmap() }
    val sourceWidth = referenceFrame.width
    val sourceHeight = referenceFrame.height

    val acceptedIds = remember(debugResult) { debugResult.validated.filter { it.passesFloor }.map { it.hold.id }.toSet() }

    Canvas(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .aspectRatio(sourceWidth.toFloat() / sourceHeight.toFloat())
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(ClimbPalette.wall),
    ) {
        drawImage(image = imageBitmap, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))

        // Layer 1: every raw Phase-3 candidate box, in amber, drawn first so refined
        // accepted/rejected overlays (drawn after) sit visibly on top of it.
        for (candidate in debugResult.candidates) {
            val rect = DebugCoordinateMapper.mapBoundingBox(candidate.boundingBox, sourceWidth, sourceHeight, size.width, size.height)
            drawRect(
                color = CANDIDATE_COLOR,
                topLeft = Offset(rect.left, rect.top),
                size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                style = Stroke(width = 2f),
            )
        }

        // Layer 2: every Phase-4-refined hold's contour, colored by its Phase-5 verdict.
        for (debugHold in debugResult.validated) {
            val hold = debugHold.hold
            val contour = hold.contour
            val color = if (hold.id in acceptedIds) ACCEPTED_COLOR else REJECTED_COLOR
            if (contour != null && contour.size >= 2) {
                val mapped = DebugCoordinateMapper.mapContour(contour, sourceWidth, sourceHeight, size.width, size.height)
                for (i in mapped.indices) {
                    val a = mapped[i]
                    val b = mapped[(i + 1) % mapped.size]
                    drawLine(color = color, start = Offset(a.x, a.y), end = Offset(b.x, b.y), strokeWidth = 3f)
                }
            } else {
                // No contour (shouldn't normally happen post-refinement, but degrade gracefully
                // to the bounding box rather than silently drawing nothing).
                val rect = DebugCoordinateMapper.mapBoundingBox(hold.boundingBox, sourceWidth, sourceHeight, size.width, size.height)
                drawRect(color = color, topLeft = Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.width, rect.height), style = Stroke(width = 3f))
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LegendEntry(color = CANDIDATE_COLOR, label = "Phase 3 candidate")
        LegendEntry(color = ACCEPTED_COLOR, label = "Accepted")
        LegendEntry(color = REJECTED_COLOR, label = "Rejected")
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(text = label, color = ClimbPalette.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun HoldReadouts(debugResult: RouteColorDetector.DebugDetectionResult) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        debugResult.validated.forEachIndexed { index, debugHold ->
            val v = debugHold.validation
            val verdict = if (debugHold.passesFloor) "ACCEPTED" else "REJECTED"
            Text(
                text = "Hold #$index — $verdict — conf=${"%.2f".format(debugHold.confidence)} " +
                    "consistOwn=${"%.2f".format(v.colorConsistencyRatioVsOwnMedian)} " +
                    "consistTarget=${"%.2f".format(v.colorConsistencyRatioVsTargetCenter)} " +
                    "growth=${"%.2f".format(v.growthAreaRatio)} " +
                    "area=${debugHold.hold.area}px",
                color = if (debugHold.passesFloor) ClimbPalette.textSecondary else ClimbPalette.fell,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
