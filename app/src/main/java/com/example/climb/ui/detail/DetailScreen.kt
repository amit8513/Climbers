package com.example.climb.ui.detail

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.work.WorkManager
import com.example.climb.BuildConfig
import com.example.climb.R
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.AnalysisStatus
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.analysis.Visibility
import com.example.climb.colordetection.ColorCalibrator
import com.example.climb.colordetection.DebugCoordinateMapper
import com.example.climb.colordetection.PixelBuffer
import com.example.climb.colordetection.RoiSampler
import com.example.climb.colordetection.toJson
import com.example.climb.colordetection.toTargetColorModel
import com.example.climb.data.ClimbRepository
import com.example.climb.playback.ColorIsolationEffect
import com.example.climb.playback.DetectedHoldHighlightEffect
import com.example.climb.playback.HoldHighlightPipeline
import com.example.climb.playback.exportWithColorIsolation
import com.example.climb.playback.exportWithHoldHighlight
import com.example.climb.sharing.ClimbSyncWorker
import com.example.climb.sharing.StorySharer
import com.example.climb.util.saveVideoToGallery
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * State of the opt-in "Detect holds" bonus feature (see [DetailScreen]'s own doc comment on the
 * `exoPlayer` default). The default live-preview/export path is always [ColorIsolationEffect] and
 * does not depend on any of this — this state only governs the separate, user-triggered detection
 * overlay: [Idle] (never pressed, or reset back to default), [Loading] (a real detector pass is
 * running on a reference frame), [Active] (detection found holds and [DetectedHoldHighlightEffect]
 * is currently overriding the live preview), [NotFound] (detection ran and found nothing for this
 * color/frame — falls back to showing the default effect, same as [Idle], but keeps the honest
 * "nothing found" message visible instead of silently reverting).
 *
 * Shared by BOTH ways of reaching a detection result: the generic "Detect holds (bonus)" button
 * (a predefined per-[com.example.climb.data.RouteColor] profile — see [HoldHighlightPipeline]'s
 * `RouteColor`-based `buildMask` overload) and "Calibrate on this hold" (a real per-tap-sampled
 * color center via [ColorCalibrator] — see [CalibrationPickerState] below and
 * [HoldHighlightPipeline]'s [com.example.climb.colordetection.TargetColorModel]-based overload).
 * Both funnel into the same [Active]/[NotFound] outcome states and the same effect-swapping/reset
 * mechanism, since from here on they're indistinguishable — just two different ways of building
 * the [com.example.climb.colordetection.TargetColorModel] that produced the result. [Active] carries
 * that exact model (not just the hold count) so export can bake in precisely what was previewed —
 * re-deriving it from [currentClimb]'s route color at export time would silently discard a
 * calibrated model and re-detect against the generic profile instead.
 */
private sealed interface DetectionBonusState {
    object Idle : DetectionBonusState
    object Loading : DetectionBonusState
    data class Active(val holdCount: Int, val targetModel: com.example.climb.colordetection.TargetColorModel) : DetectionBonusState
    object NotFound : DetectionBonusState
}

/**
 * State of the "Calibrate on this hold" tap-to-calibrate picker overlay ([CalibrationPickerDialog]):
 * [Hidden] (not shown), [PickingPoint] (showing the reference frame full-size in a dialog, waiting
 * for the user to tap the hold they want to highlight). This is deliberately separate from
 * [DetectionBonusState] — it only governs whether the picker dialog itself is visible; once a tap
 * lands, this returns to [Hidden] and [DetectionBonusState] takes over exactly as it does for the
 * generic "Detect holds (bonus)" flow.
 */
private sealed interface CalibrationPickerState {
    object Hidden : CalibrationPickerState
    object PickingPoint : CalibrationPickerState
}

private val detailDateFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)

/** Only these are wired up for cloud sync today — [Visibility.SELECTED_FRIENDS] isn't offered
 * here yet since there's no picker/rules support for it on the main climb log. */
private val SUPPORTED_VISIBILITIES = listOf(Visibility.PRIVATE, Visibility.FRIENDS_ONLY, Visibility.PUBLIC)

private fun Visibility.displayName(): String = when (this) {
    Visibility.PRIVATE -> "Private"
    Visibility.FRIENDS_ONLY -> "Friends only"
    Visibility.SELECTED_FRIENDS -> "Selected friends"
    Visibility.PUBLIC -> "Public"
}

@Composable
fun DetailScreen(
    climbId: Long,
    repository: ClimbRepository,
    currentUid: String,
    currentUsername: String,
    analysisRepository: AnalysisRepository,
    onDeleted: () -> Unit,
    onStartAnalysis: (videoPath: String, durationMs: Long, sourceClimbId: Long) -> Unit,
    onViewAnalysisProgress: (attemptId: Long) -> Unit,
    onViewAnalysisResult: (analysisId: Long) -> Unit,
    onOpenHoldDetectionDebug: () -> Unit = {},
) {
    val climb by repository.observeById(climbId, currentUid).collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentClimb = climb
    if (currentClimb == null) {
        Box(modifier = Modifier.fillMaxSize().wallTexture(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = ClimbPalette.textSecondary)
        }
        return
    }

    val initialHueTolerance = currentClimb.hueToleranceDegrees ?: ColorIsolationEffect.DEFAULT_HUE_TOLERANCE_DEGREES
    val initialHueOffset = currentClimb.hueOffsetDegrees ?: 0f
    var hueTolerancePosition by remember { mutableFloatStateOf(initialHueTolerance) }
    var appliedHueTolerance by remember { mutableFloatStateOf(initialHueTolerance) }
    var hueOffsetPosition by remember { mutableFloatStateOf(initialHueOffset) }
    var appliedHueOffset by remember { mutableFloatStateOf(initialHueOffset) }
    var isSavingVisibility by remember { mutableStateOf(false) }
    var isExportingVideo by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var isSavingToGallery by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    var gallerySavedMessage by remember { mutableStateOf<String?>(null) }
    var isSharingToInstagram by remember { mutableStateOf(false) }
    var instagramError by remember { mutableStateOf<String?>(null) }
    var isSharingToFacebook by remember { mutableStateOf(false) }
    var facebookError by remember { mutableStateOf<String?>(null) }

    // Effects must be set before prepare() — ExoPlayer decides whether to route through the GL
    // effects pipeline at prepare time, so setting them afterwards (e.g. only from the
    // LaunchedEffect below) silently no-ops and video plays back unfiltered. This is the original,
    // always-working default (restored after Phase 6's detection-only pipeline turned out to
    // reject too many real holds — see "Detect holds" below for the now-opt-in bonus feature).
    val exoPlayer = remember(currentClimb.videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setVideoEffects(
                listOf(
                    ColorIsolationEffect(
                        targetColor = currentClimb.routeColor,
                        hueToleranceDegrees = appliedHueTolerance,
                        hueOffsetDegrees = appliedHueOffset,
                    ),
                ),
            )
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(currentClimb.videoPath))))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    var effectsGeneration by remember { mutableStateOf(0) }

    LaunchedEffect(exoPlayer, currentClimb.routeColor, appliedHueTolerance, appliedHueOffset) {
        if (effectsGeneration > 0) {
            exoPlayer.setVideoEffects(
                listOf(
                    ColorIsolationEffect(
                        targetColor = currentClimb.routeColor,
                        hueToleranceDegrees = appliedHueTolerance,
                        hueOffsetDegrees = appliedHueOffset,
                    ),
                ),
            )
            // Force the pipeline to redraw the current frame through the new effect chain
            // immediately, instead of waiting for playback to advance on its own.
            exoPlayer.seekTo(exoPlayer.currentPosition)
        }
        effectsGeneration++
    }

    // Bonus feature (opt-in, button-triggered only — see below): runs the real per-object
    // detection pipeline once on a reference frame and, if it finds anything, temporarily swaps
    // the live preview to DetectedHoldHighlightEffect. Cache the extracted reference frame so
    // pressing the button again after a slider retune doesn't re-decode the video.
    var referenceFrame by remember(currentClimb.videoPath) { mutableStateOf<Bitmap?>(null) }
    var bonusState by remember(currentClimb.videoPath) { mutableStateOf<DetectionBonusState>(DetectionBonusState.Idle) }

    // Restores a previously successful "Calibrate on this hold" result (see onCalibrationTap
    // below) so reopening this climb doesn't require tap-to-calibrate again every time.
    LaunchedEffect(currentClimb.id, currentClimb.calibratedColorModelJson) {
        val savedModel = currentClimb.calibratedColorModelJson?.toTargetColorModel() ?: return@LaunchedEffect
        bonusState = DetectionBonusState.Loading
        val (result, frame) = withContext(Dispatchers.Default) {
            val frame = referenceFrame ?: HoldHighlightPipeline.extractReferenceFrame(currentClimb.videoPath)
            HoldHighlightPipeline.buildMask(frame, savedModel) to frame
        }
        referenceFrame = frame
        if (result.holdCount > 0) {
            exoPlayer.setVideoEffects(listOf(DetectedHoldHighlightEffect(result.maskBitmap)))
            exoPlayer.seekTo(exoPlayer.currentPosition)
            bonusState = DetectionBonusState.Active(result.holdCount, savedModel)
        } else {
            // Lighting/frame differences since the calibration was saved meant it didn't
            // reproduce this time - fall back to the always-working default silently, rather than
            // showing "not found" for something the user didn't just ask for this session.
            bonusState = DetectionBonusState.Idle
        }
    }

    fun resetToDefaultEffect() {
        exoPlayer.setVideoEffects(
            listOf(ColorIsolationEffect(currentClimb.routeColor, appliedHueTolerance, appliedHueOffset)),
        )
        exoPlayer.seekTo(exoPlayer.currentPosition)
        bonusState = DetectionBonusState.Idle
    }

    fun runHoldDetection() {
        scope.launch {
            bonusState = DetectionBonusState.Loading
            val (result, frame, targetModel) = withContext(Dispatchers.Default) {
                val frame = referenceFrame ?: HoldHighlightPipeline.extractReferenceFrame(currentClimb.videoPath)
                val targetModel = HoldHighlightPipeline.targetModelFor(currentClimb.routeColor, appliedHueOffset, appliedHueTolerance)
                Triple(HoldHighlightPipeline.buildMask(frame, targetModel), frame, targetModel)
            }
            referenceFrame = frame
            if (result.holdCount > 0) {
                exoPlayer.setVideoEffects(listOf(DetectedHoldHighlightEffect(result.maskBitmap)))
                exoPlayer.seekTo(exoPlayer.currentPosition)
                bonusState = DetectionBonusState.Active(result.holdCount, targetModel)
            } else {
                bonusState = DetectionBonusState.NotFound
            }
        }
    }

    // Tap-to-calibrate (see CalibrationPickerState/CalibrationPickerDialog): the durable fix for
    // real per-gym lighting variance that the generic "Detect holds (bonus)" profile can't cover —
    // real-footage testing proved a single global color-distance threshold cannot both detect real
    // photos under arbitrary lighting AND keep different route colors discriminated (see
    // RouteColorDetectionConfig.STRICT_DELTA_E_THRESHOLD's own doc comment for the measured
    // numbers). Calibrating against THIS hold's own actual sampled color, instead of a theoretical
    // per-color default, sidesteps that limit entirely.
    var calibrationPickerState by remember(currentClimb.videoPath) { mutableStateOf<CalibrationPickerState>(CalibrationPickerState.Hidden) }
    var isPreparingCalibrationFrame by remember(currentClimb.videoPath) { mutableStateOf(false) }

    fun openCalibrationPicker() {
        scope.launch {
            isPreparingCalibrationFrame = true
            val frame = referenceFrame ?: withContext(Dispatchers.Default) {
                HoldHighlightPipeline.extractReferenceFrame(currentClimb.videoPath)
            }
            referenceFrame = frame
            isPreparingCalibrationFrame = false
            calibrationPickerState = CalibrationPickerState.PickingPoint
        }
    }

    fun onCalibrationTap(tapX: Float, tapY: Float, displayedSize: IntSize) {
        val frame = referenceFrame ?: return
        calibrationPickerState = CalibrationPickerState.Hidden
        scope.launch {
            bonusState = DetectionBonusState.Loading
            val (result, targetModel) = withContext(Dispatchers.Default) {
                val sourcePoint = DebugCoordinateMapper.unmapPoint(
                    targetX = tapX,
                    targetY = tapY,
                    sourceWidth = frame.width,
                    sourceHeight = frame.height,
                    targetWidth = displayedSize.width.toFloat(),
                    targetHeight = displayedSize.height.toFloat(),
                )
                val buffer = PixelBuffer.fromBitmap(frame)
                val centerX = sourcePoint.x.roundToInt().coerceIn(0, buffer.width - 1)
                val centerY = sourcePoint.y.roundToInt().coerceIn(0, buffer.height - 1)
                val samples = RoiSampler.sample(buffer, centerX, centerY)
                val calibratedModel = ColorCalibrator.calibrate(samples, currentClimb.routeColor)
                HoldHighlightPipeline.buildMask(frame, calibratedModel) to calibratedModel
            }
            // A tap that lands mostly on background/wall can legitimately calibrate to a color
            // that then matches nothing real in the frame — same honest "nothing found" fallback
            // as the generic bonus flow (DetectionBonusState.NotFound), not a crash or silent
            // no-op. No separate "that didn't look like a strong color" pre-check is done here:
            // running the real detector against the calibrated model IS that check, and it's more
            // accurate than guessing from the ROI's own color spread alone.
            if (result.holdCount > 0) {
                exoPlayer.setVideoEffects(listOf(DetectedHoldHighlightEffect(result.maskBitmap)))
                exoPlayer.seekTo(exoPlayer.currentPosition)
                bonusState = DetectionBonusState.Active(result.holdCount, targetModel)
                // Persisted so reopening this climb restores the calibrated view instead of
                // requiring the user to tap-to-calibrate again every time (see the
                // restore-on-load LaunchedEffect below).
                repository.update(currentClimb.copy(calibratedColorModelJson = targetModel.toJson()))
            } else {
                bonusState = DetectionBonusState.NotFound
            }
        }
    }

    // Export/share should bake in whichever effect is currently on screen: the default
    // hue-isolation effect, or the bonus detection result (generic or calibrated) if one is
    // active. Exporting always the default regardless of what's being previewed would be a
    // confusing mismatch between what you see and what you save. Reuses the exact
    // TargetColorModel stored on DetectionBonusState.Active rather than re-deriving one from
    // routeColor/sliders, so a calibrated result exports the calibrated model it actually
    // previewed, not a re-detected generic one that could find something different (or nothing).
    suspend fun exportCurrentEffect(outputPath: String) {
        val currentBonusState = bonusState
        if (currentBonusState is DetectionBonusState.Active) {
            exportWithHoldHighlight(
                context = context,
                inputPath = currentClimb.videoPath,
                outputPath = outputPath,
                targetModel = currentBonusState.targetModel,
            )
        } else {
            exportWithColorIsolation(
                context = context,
                inputPath = currentClimb.videoPath,
                outputPath = outputPath,
                routeColor = currentClimb.routeColor,
                hueOffsetDegrees = hueOffsetPosition,
                hueToleranceDegrees = hueTolerancePosition,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wallTexture()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                // Deliberately shorter than the source 9:16 so the metadata and effect
                // controls below stay on screen instead of being pushed off the bottom.
                .aspectRatio(9f / 13f)
                .clip(RoundedCornerShape(16.dp))
                .background(ClimbPalette.wall),
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
                modifier = Modifier.fillMaxSize(),
            )
            if (bonusState is DetectionBonusState.Loading) {
                Box(
                    modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = ClimbPalette.chalk)
                }
            }
        }

        when (val currentBonusState = bonusState) {
            is DetectionBonusState.Idle -> {
                Text(
                    text = "Detect holds (bonus)",
                    color = ClimbPalette.chalk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                        .clickable { runHoldDetection() },
                )
            }
            is DetectionBonusState.Loading -> {
                Text(
                    text = "Detecting holds…",
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                )
            }
            is DetectionBonusState.Active -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${currentBonusState.holdCount} hold${if (currentBonusState.holdCount == 1) "" else "s"} detected",
                        color = ClimbPalette.textMuted,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = "Reset to default",
                        color = ClimbPalette.chalk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { resetToDefaultEffect() },
                    )
                }
            }
            is DetectionBonusState.NotFound -> {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
                    Text(
                        text = "No holds of this color detected in the reference frame — showing the original video.",
                        color = ClimbPalette.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Text(
                        text = "Try again",
                        color = ClimbPalette.chalk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp).clickable { runHoldDetection() },
                    )
                }
            }
        }

        // "Calibrate on this hold" — the tap-to-calibrate alternative to the generic profile-based
        // "Detect holds (bonus)" button above (see CalibrationPickerState's own doc comment for
        // why both exist). Hidden while a detection pass is already running to avoid overlapping
        // attempts; available in every other bonus state so the user can always (re)try it,
        // including after a generic detection already succeeded or failed.
        if (bonusState !is DetectionBonusState.Loading) {
            Text(
                text = if (isPreparingCalibrationFrame) "Preparing frame…" else "Calibrate on this hold",
                color = if (isPreparingCalibrationFrame) ClimbPalette.textMuted else ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    .clickable(enabled = !isPreparingCalibrationFrame) { openCalibrationPicker() },
            )
        }

        // Phase 7 debug tooling entry point — debug builds only, never shown in a release build.
        if (BuildConfig.DEBUG) {
            Text(
                text = "Debug: view hold detection stages",
                color = ClimbPalette.textMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    .clickable(onClick = onOpenHoldDetectionDebug),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldBadge(grade = currentClimb.vGrade, routeColor = currentClimb.routeColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentClimb.routeColor.name
                        .lowercase(Locale.US)
                        .replaceFirstChar { it.uppercase() },
                    color = ClimbPalette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                OutcomePill(outcome = currentClimb.outcome)
            }
            Text(
                text = detailDateFormatter.format(Date(currentClimb.createdAt)),
                color = ClimbPalette.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionCard(title = "Effect tuning", modifier = Modifier.padding(horizontal = 16.dp)) {
            TuningSlider(
                label = "Hue",
                readout = "${if (hueOffsetPosition >= 0) "+" else ""}${hueOffsetPosition.roundToInt()}°",
                value = hueOffsetPosition,
                valueRange = ColorIsolationEffect.MIN_HUE_OFFSET_DEGREES..ColorIsolationEffect.MAX_HUE_OFFSET_DEGREES,
                onValueChange = { hueOffsetPosition = it },
                onValueChangeFinished = {
                    appliedHueOffset = hueOffsetPosition
                    // Retuning the base effect implies retuning from scratch — drop any active
                    // bonus detection result rather than leaving a stale mask on screen.
                    bonusState = DetectionBonusState.Idle
                    scope.launch { repository.update(currentClimb.copy(hueOffsetDegrees = hueOffsetPosition)) }
                },
            )
            Spacer(Modifier.height(12.dp))
            TuningSlider(
                label = "Color sensitivity",
                readout = "${hueTolerancePosition.roundToInt()}°",
                value = hueTolerancePosition,
                valueRange = ColorIsolationEffect.MIN_HUE_TOLERANCE_DEGREES..ColorIsolationEffect.MAX_HUE_TOLERANCE_DEGREES,
                onValueChange = { hueTolerancePosition = it },
                onValueChangeFinished = {
                    appliedHueTolerance = hueTolerancePosition
                    bonusState = DetectionBonusState.Idle
                    scope.launch { repository.update(currentClimb.copy(hueToleranceDegrees = hueTolerancePosition)) }
                },
            )
            Spacer(Modifier.height(14.dp))
            if (isExportingVideo) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp, color = ClimbPalette.chalk)
                    Text(text = "Rendering edited video…", color = ClimbPalette.textSecondary, fontSize = 11.sp)
                }
            } else {
                Text(
                    text = "Save edited video",
                    color = ClimbPalette.chalk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        isExportingVideo = true
                        exportError = null
                        val outputPath = File(
                            File(currentClimb.videoPath).parentFile,
                            "climb_${currentClimb.id}_edited_${System.currentTimeMillis()}.mp4",
                        ).absolutePath
                        scope.launch {
                            runCatching {
                                exportCurrentEffect(outputPath)
                            }.onSuccess {
                                val oldPath = currentClimb.videoPath
                                // The tuning is now baked into the new file's pixels, so it's
                                // reset to defaults — reopening plays the app's normal (default)
                                // highlight effect live on top, same as any other climb.
                                repository.update(
                                    currentClimb.copy(
                                        videoPath = outputPath,
                                        hueOffsetDegrees = null,
                                        hueToleranceDegrees = null,
                                    ),
                                )
                                File(oldPath).delete()
                                hueOffsetPosition = 0f
                                appliedHueOffset = 0f
                                hueTolerancePosition = ColorIsolationEffect.DEFAULT_HUE_TOLERANCE_DEGREES
                                appliedHueTolerance = ColorIsolationEffect.DEFAULT_HUE_TOLERANCE_DEGREES
                                if (currentClimb.visibility != Visibility.PRIVATE) {
                                    ClimbSyncWorker.enqueue(WorkManager.getInstance(context), currentUid, currentUsername, currentClimb.id)
                                }
                            }.onFailure { error ->
                                exportError = error.message ?: "Couldn't save the edited video"
                            }
                            isExportingVideo = false
                        }
                    },
                )
                Text(
                    text = "Bakes the current color effect into the video file — this is what gets shared when you set this climb to Friends only or Public.",
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                exportError?.let { message ->
                    Text(text = message, color = ClimbPalette.fell, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            if (isSavingToGallery) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp, color = ClimbPalette.chalk)
                    Text(text = "Saving to your device…", color = ClimbPalette.textSecondary, fontSize = 11.sp)
                }
            } else {
                Text(
                    text = "Save to device",
                    color = ClimbPalette.chalk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        if (!isExportingVideo) {
                            isSavingToGallery = true
                            galleryError = null
                            gallerySavedMessage = null
                            val tempFile = File(context.cacheDir, "climb_${currentClimb.id}_gallery_${System.currentTimeMillis()}.mp4")
                            scope.launch {
                                runCatching {
                                    exportCurrentEffect(tempFile.absolutePath)
                                    saveVideoToGallery(context, tempFile, "Climb_${currentClimb.id}_${System.currentTimeMillis()}.mp4")
                                }.onSuccess {
                                    gallerySavedMessage = "Saved to your device's Movies folder"
                                }.onFailure { error ->
                                    galleryError = error.message ?: "Couldn't save the video to your device"
                                }
                                tempFile.delete()
                                isSavingToGallery = false
                            }
                        }
                    },
                )
                Text(
                    text = "Renders the current color effect into a copy saved to this device's Movies folder — separate from sharing.",
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                galleryError?.let { message ->
                    Text(text = message, color = ClimbPalette.fell, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                gallerySavedMessage?.let { message ->
                    Text(text = message, color = ClimbPalette.sent, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(text = "Share to", color = ClimbPalette.textMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShareIconButton(
                    iconRes = R.drawable.ic_instagram,
                    contentDescription = "Share to Instagram Story",
                    inProgress = isSharingToInstagram,
                    enabled = !isExportingVideo && !isSharingToInstagram && !isSharingToFacebook,
                    onClick = {
                        isSharingToInstagram = true
                        instagramError = null
                        val exportedFile = File(context.cacheDir, "climb_${currentClimb.id}_story_${System.currentTimeMillis()}.mp4")
                        scope.launch {
                            runCatching {
                                exportCurrentEffect(exportedFile.absolutePath)
                                StorySharer.shareToInstagramStory(context, exportedFile).getOrThrow()
                            }.onFailure { error ->
                                instagramError = error.message ?: "Couldn't open Instagram — is it installed?"
                            }
                            isSharingToInstagram = false
                        }
                    },
                )
                ShareIconButton(
                    iconRes = R.drawable.ic_facebook,
                    contentDescription = "Share to Facebook",
                    inProgress = isSharingToFacebook,
                    enabled = !isExportingVideo && !isSharingToInstagram && !isSharingToFacebook,
                    onClick = {
                        isSharingToFacebook = true
                        facebookError = null
                        val exportedFile = File(context.cacheDir, "climb_${currentClimb.id}_fbshare_${System.currentTimeMillis()}.mp4")
                        scope.launch {
                            runCatching {
                                exportCurrentEffect(exportedFile.absolutePath)
                                StorySharer.shareToFacebook(context, exportedFile).getOrThrow()
                            }.onFailure { error ->
                                facebookError = error.message ?: "Couldn't open Facebook — is it installed?"
                            }
                            isSharingToFacebook = false
                        }
                    },
                )
            }
            instagramError?.let { message ->
                Text(text = message, color = ClimbPalette.fell, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            facebookError?.let { message ->
                Text(text = message, color = ClimbPalette.fell, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        PoseAnalysisSection(
            climbId = currentClimb.id,
            videoPath = currentClimb.videoPath,
            durationMs = currentClimb.durationMs,
            analysisRepository = analysisRepository,
            onStartAnalysis = onStartAnalysis,
            onViewProgress = onViewAnalysisProgress,
            onViewResult = onViewAnalysisResult,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (currentClimb.notes.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Notes", modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = currentClimb.notes,
                    color = ClimbPalette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Sharing", modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Who can see this climb's video and details.",
                color = ClimbPalette.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORTED_VISIBILITIES.forEach { option ->
                    VisibilityChip(
                        label = option.displayName(),
                        selected = currentClimb.visibility == option,
                        enabled = !isSavingVisibility,
                        onClick = {
                            if (!isSavingVisibility) {
                                isSavingVisibility = true
                                scope.launch {
                                    repository.update(currentClimb.copy(visibility = option))
                                    val workManager = WorkManager.getInstance(context)
                                    ClimbSyncWorker.enqueue(workManager, currentUid, currentUsername, currentClimb.id)
                                    // Wait for the sync worker to actually finish — this is exactly the
                                    // window where switching to a different Firebase account mid-sync
                                    // caused writes to fail; the loader tells you when it's safe.
                                    workManager.getWorkInfosForUniqueWorkFlow(ClimbSyncWorker.uniqueWorkName(currentUid, currentClimb.id))
                                        .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }
                                    isSavingVisibility = false
                                }
                            }
                        },
                    )
                }
            }
            if (isSavingVisibility) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp, color = ClimbPalette.chalk)
                    Text(text = "Saving and syncing — don't switch accounts yet", color = ClimbPalette.textSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Delete climb",
                color = ClimbPalette.fell,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        scope.launch {
                            repository.delete(currentClimb)
                            ClimbSyncWorker.enqueue(WorkManager.getInstance(context), currentUid, currentUsername, currentClimb.id)
                            onDeleted()
                        }
                    }
                    .padding(12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    val pickerFrame = referenceFrame
    if (calibrationPickerState is CalibrationPickerState.PickingPoint && pickerFrame != null) {
        CalibrationPickerDialog(
            frame = pickerFrame,
            onCancel = { calibrationPickerState = CalibrationPickerState.Hidden },
            onTap = { tapX, tapY, displayedSize -> onCalibrationTap(tapX, tapY, displayedSize) },
        )
    }
}

/**
 * Full-screen(-ish) dialog showing the reference frame at its own aspect ratio, tappable to pick
 * the hold to calibrate on. A plain [Dialog] (not inline in [DetailScreen]'s own scrollable
 * Column) so its own tap-target sizing/positioning is independent of the surrounding screen's
 * scroll state and layout — the frame is shown at whatever size Compose lays it out at here, and
 * [onTap] reports both the tap offset and that exact laid-out size so the caller can map back to
 * the frame's native pixel coordinates via [DebugCoordinateMapper.unmapPoint].
 */
@Composable
private fun CalibrationPickerDialog(frame: Bitmap, onCancel: () -> Unit, onTap: (Float, Float, IntSize) -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        var displayedSize by remember { mutableStateOf(IntSize.Zero) }
        val imageBitmap = remember(frame) { frame.asImageBitmap() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ClimbPalette.surfaceRaised)
                .padding(16.dp),
        ) {
            Text(
                text = "Tap the hold you want to highlight",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                    .clip(RoundedCornerShape(10.dp))
                    .onSizeChanged { displayedSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (displayedSize.width > 0 && displayedSize.height > 0) {
                                onTap(offset.x, offset.y, displayedSize)
                            }
                        }
                    },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Cancel",
                color = ClimbPalette.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }
    }
}

@Composable
private fun PoseAnalysisSection(
    climbId: Long,
    videoPath: String,
    durationMs: Long,
    analysisRepository: AnalysisRepository,
    onStartAnalysis: (videoPath: String, durationMs: Long, sourceClimbId: Long) -> Unit,
    onViewProgress: (attemptId: Long) -> Unit,
    onViewResult: (analysisId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attempt by analysisRepository.observeLatestAttemptForSourceClimb(climbId).collectAsStateWithLifecycle(initialValue = null)
    val currentAttempt = attempt

    SectionCard(title = "Pose analysis", modifier = modifier) {
        if (currentAttempt == null) {
            Text(
                text = "Run pose analysis to see a skeleton overlay and movement insights.",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Analyze this climb",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onStartAnalysis(videoPath, durationMs, climbId) },
            )
        } else {
            PoseAnalysisStatusRow(attempt = currentAttempt, analysisRepository = analysisRepository, onViewProgress = onViewProgress, onViewResult = onViewResult)
        }
    }
}

@Composable
private fun PoseAnalysisStatusRow(
    attempt: ClimbAttemptEntity,
    analysisRepository: AnalysisRepository,
    onViewProgress: (attemptId: Long) -> Unit,
    onViewResult: (analysisId: Long) -> Unit,
) {
    val analysis by analysisRepository.observeLatestAnalysis(attempt.id).collectAsStateWithLifecycle(initialValue = null)
    val currentAnalysis = analysis

    when {
        currentAnalysis == null -> Text("Starting…", color = ClimbPalette.textSecondary, fontSize = 13.sp)
        currentAnalysis.status == AnalysisStatus.COMPLETE -> Text(
            text = "View analysis",
            color = ClimbPalette.chalk,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clickable { onViewResult(currentAnalysis.id) },
        )
        currentAnalysis.status == AnalysisStatus.FAILED -> Text(
            text = "Analysis failed: ${currentAnalysis.failureReason ?: "unknown error"}",
            color = ClimbPalette.fell,
            fontSize = 13.sp,
        )
        else -> Text(
            text = "Analysis in progress — tap to view",
            color = ClimbPalette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.clickable { onViewProgress(attempt.id) },
        )
    }
}

@Composable
private fun ShareIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    inProgress: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ClimbPalette.surfaceRaised)
            .border(1.dp, ClimbPalette.border, CircleShape)
            .clickable(enabled = enabled && !inProgress, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (inProgress) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ClimbPalette.chalk)
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) ClimbPalette.textPrimary else ClimbPalette.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun VisibilityChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = label,
        color = (if (selected) ClimbPalette.chalkText else ClimbPalette.textSecondary).copy(alpha = if (enabled) 1f else 0.4f),
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background((if (selected) ClimbPalette.chalk else ClimbPalette.surfaceRaised).copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.dp, (if (selected) ClimbPalette.chalk else ClimbPalette.border).copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun TuningSlider(
    label: String,
    readout: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = label, color = ClimbPalette.textSecondary, fontSize = 13.sp)
            Text(
                text = readout,
                color = ClimbPalette.textPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ClimbPalette.chalk,
                activeTrackColor = ClimbPalette.chalk,
                inactiveTrackColor = ClimbPalette.border,
            ),
        )
    }
}
