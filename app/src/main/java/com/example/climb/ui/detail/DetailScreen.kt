package com.example.climb.ui.detail

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.work.WorkManager
import com.example.climb.R
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.AnalysisStatus
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbRepository
import com.example.climb.playback.ColorIsolationEffect
import com.example.climb.playback.exportWithColorIsolation
import com.example.climb.sharing.ClimbSyncWorker
import com.example.climb.sharing.StorySharer
import com.example.climb.util.saveVideoToGallery
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    // LaunchedEffect below) silently no-ops and video plays back unfiltered.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wallTexture()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                // Deliberately shorter than the source 9:16 so the metadata and effect
                // controls below stay on screen instead of being pushed off the bottom.
                .aspectRatio(9f / 13f)
                .clip(RoundedCornerShape(16.dp))
                .background(ClimbPalette.wall),
        )

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
                                exportWithColorIsolation(
                                    context = context,
                                    inputPath = currentClimb.videoPath,
                                    outputPath = outputPath,
                                    routeColor = currentClimb.routeColor,
                                    hueOffsetDegrees = hueOffsetPosition,
                                    hueToleranceDegrees = hueTolerancePosition,
                                )
                            }.onSuccess {
                                val oldPath = currentClimb.videoPath
                                // The tuning is now baked into the new file's pixels, so it's
                                // reset to defaults — reopening plays the app's normal (default)
                                // color-isolation live on top, same as any other climb.
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
                                    exportWithColorIsolation(
                                        context = context,
                                        inputPath = currentClimb.videoPath,
                                        outputPath = tempFile.absolutePath,
                                        routeColor = currentClimb.routeColor,
                                        hueOffsetDegrees = hueOffsetPosition,
                                        hueToleranceDegrees = hueTolerancePosition,
                                    )
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
                                exportWithColorIsolation(
                                    context = context,
                                    inputPath = currentClimb.videoPath,
                                    outputPath = exportedFile.absolutePath,
                                    routeColor = currentClimb.routeColor,
                                    hueOffsetDegrees = hueOffsetPosition,
                                    hueToleranceDegrees = hueTolerancePosition,
                                )
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
                                exportWithColorIsolation(
                                    context = context,
                                    inputPath = currentClimb.videoPath,
                                    outputPath = exportedFile.absolutePath,
                                    routeColor = currentClimb.routeColor,
                                    hueOffsetDegrees = hueOffsetPosition,
                                    hueToleranceDegrees = hueTolerancePosition,
                                )
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
