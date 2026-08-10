package com.example.climb.ui.analysis

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.ClimbAnalysisEntity
import com.example.climb.analysis.ClimbEvent
import com.example.climb.analysis.ClimbEventType
import com.example.climb.analysis.BetaOpportunity
import com.example.climb.analysis.ComparisonLine
import com.example.climb.analysis.FOOT_LEG_LANDMARKS
import com.example.climb.analysis.ImprovementItem
import com.example.climb.analysis.NextSessionFocusItem
import com.example.climb.analysis.QualityIndicator
import com.example.climb.analysis.SessionOverview
import com.example.climb.analysis.StrengthItem
import com.example.climb.analysis.TechnicalLimitation
import com.example.climb.analysis.TechnicalObservation
import com.example.climb.analysis.buildAttemptComparison
import com.example.climb.analysis.buildBetaOpportunities
import com.example.climb.analysis.buildImprovements
import com.example.climb.analysis.buildNextSessionFocus
import com.example.climb.analysis.buildQualityIndicators
import com.example.climb.analysis.buildSessionOverview
import com.example.climb.analysis.buildStrengths
import com.example.climb.analysis.buildTechnicalLimitations
import com.example.climb.analysis.buildTechnicalPerformanceReport
import com.example.climb.analysis.formatTimestampMs
import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.relatedLandmarksFor
import com.example.climb.analysis.scoring.CategoryScore
import com.example.climb.analysis.toCategoryScores
import com.example.climb.analysis.toClimbEvents
import com.example.climb.analysis.toClimbMetrics
import com.example.climb.analysis.toCoachingTips
import com.example.climb.analysis.toPoseFrames
import com.example.climb.coaching.CoachingTip
import com.example.climb.pose.PoseLandmarkType
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
private val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f)

private sealed interface AttemptComparisonState {
    object Loading : AttemptComparisonState
    object NoRouteName : AttemptComparisonState
    object NoPreviousAttempt : AttemptComparisonState
    data class Found(val lines: List<ComparisonLine>) : AttemptComparisonState
}

private fun formatSeconds(ms: Long): String = "${"%.1f".format(ms / 1000f)}s"

@Composable
fun AnalysisResultScreen(
    analysisId: Long,
    analysisRepository: AnalysisRepository,
    modifier: Modifier = Modifier,
) {
    val analysis by analysisRepository.observeAnalysis(analysisId).collectAsStateWithLifecycle(initialValue = null)
    val currentAnalysis = analysis

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        if (currentAnalysis == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = ClimbPalette.textSecondary)
            }
        } else {
            AnalysisResultContent(currentAnalysis, analysisRepository)
        }
    }
}

@Composable
private fun AnalysisResultContent(analysis: ClimbAnalysisEntity, analysisRepository: AnalysisRepository) {
    val attempt by analysisRepository.observeAttempt(analysis.attemptId).collectAsStateWithLifecycle(initialValue = null)
    val currentAttempt = attempt
    if (currentAttempt == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = ClimbPalette.textSecondary)
        }
        return
    }

    val frames = remember(analysis.poseFramesJson) { analysis.poseFramesJson.toPoseFrames() }
    val metrics = remember(analysis.metricsJson) { analysis.metricsJson.toClimbMetrics() }
    val events = remember(analysis.eventsJson) { analysis.eventsJson.toClimbEvents() }
    val tips = remember(analysis.tipsJson) { analysis.tipsJson.toCoachingTips() }
    val categoryScores = remember(analysis.categoryScoresJson) { analysis.categoryScoresJson.toCategoryScores() }
    val strengths = remember(metrics, events) { metrics?.let { buildStrengths(it, events) } ?: emptyList() }
    val improvements = remember(metrics, events) { metrics?.let { buildImprovements(it, events) } ?: emptyList() }
    val betaOpportunities = remember(events) { buildBetaOpportunities(events) }
    val sessionOverview = remember(metrics, strengths, improvements) { metrics?.let { buildSessionOverview(it, strengths, improvements) } }
    val nextSessionFocus = remember(metrics, improvements) { metrics?.let { buildNextSessionFocus(improvements, it) } ?: emptyList() }
    var comparisonState by remember { mutableStateOf<AttemptComparisonState>(AttemptComparisonState.Loading) }
    LaunchedEffect(currentAttempt.id, metrics) {
        val currentMetrics = metrics
        val routeName = currentAttempt.routeName?.takeIf { it.isNotBlank() }
        comparisonState = when {
            currentMetrics == null -> AttemptComparisonState.Loading
            routeName == null -> AttemptComparisonState.NoRouteName
            else -> {
                val previousAnalysis = analysisRepository.getPreviousCompletedAnalysisForRoute(currentAttempt)
                val previousMetrics = previousAnalysis?.metricsJson?.toClimbMetrics()
                if (previousAnalysis == null || previousMetrics == null) {
                    AttemptComparisonState.NoPreviousAttempt
                } else {
                    AttemptComparisonState.Found(buildAttemptComparison(previousMetrics, previousAnalysis.overallScore, currentMetrics, analysis.overallScore))
                }
            }
        }
    }
    val context = LocalContext.current

    val exoPlayer = remember(currentAttempt.videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(currentAttempt.videoPath))))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    var currentPositionMs by remember { mutableStateOf(0L) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            delay(50)
        }
    }

    var overlayMode by remember { mutableStateOf(OverlayMode.SKELETON) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var highlightedLandmarks by remember { mutableStateOf<Set<PoseLandmarkType>>(emptySet()) }
    LaunchedEffect(highlightedLandmarks) {
        if (highlightedLandmarks.isNotEmpty()) {
            delay(2_500)
            highlightedLandmarks = emptySet()
        }
    }
    val onSeekAndHighlight: (Long, Set<PoseLandmarkType>) -> Unit = { ms, landmarks ->
        exoPlayer.seekTo(ms)
        highlightedLandmarks = landmarks
    }

    val nearestFrame = remember(frames, currentPositionMs) {
        frames.minByOrNull { kotlin.math.abs(it.timestampMs - currentPositionMs) }
    }

    val videoWidth = analysis.videoWidth
    val videoHeight = analysis.videoHeight
    val aspectRatio = if (videoWidth != null && videoHeight != null && videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        9f / 16f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = currentAttempt.routeName ?: "Climb analysis",
            color = ClimbPalette.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(currentAttempt.vGrade?.let { "V$it" } ?: "Ungraded", color = ClimbPalette.textSecondary, fontSize = 13.sp)
            Text("·", color = ClimbPalette.textMuted, fontSize = 13.sp)
            Text(currentAttempt.wallType.name.lowercase().replaceFirstChar { it.uppercase() }, color = ClimbPalette.textSecondary, fontSize = 13.sp)
            Text("·", color = ClimbPalette.textMuted, fontSize = 13.sp)
            Text(dateFormatter.format(Date(currentAttempt.createdAt)), color = ClimbPalette.textSecondary, fontSize = 13.sp)
        }
        ConfidenceBadge(analysis.confidence)

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(ClimbPalette.wall),
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
                modifier = Modifier.fillMaxSize(),
            )
            when (overlayMode) {
                OverlayMode.NONE -> Unit
                OverlayMode.SKELETON -> SkeletonOverlay(
                    frame = nearestFrame,
                    modifier = Modifier.fillMaxSize(),
                    highlightedLandmarks = highlightedLandmarks,
                )
                OverlayMode.BODY_PART_TRACKING -> BodyPartTrackingOverlay(
                    frames = frames,
                    currentPositionMs = currentPositionMs,
                    modifier = Modifier.fillMaxSize(),
                    highlightedLandmarks = highlightedLandmarks,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Overlay", color = ClimbPalette.textMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayModeChip(mode = OverlayMode.NONE, label = "None", selected = overlayMode, onSelect = { overlayMode = it })
            OverlayModeChip(mode = OverlayMode.SKELETON, label = "Skeleton", selected = overlayMode, onSelect = { overlayMode = it })
            OverlayModeChip(mode = OverlayMode.BODY_PART_TRACKING, label = "Body tracking", selected = overlayMode, onSelect = { overlayMode = it })
        }
        if (overlayMode == OverlayMode.BODY_PART_TRACKING) {
            Spacer(Modifier.height(8.dp))
            BodyPartTrackingLegend()
        }

        Spacer(Modifier.height(8.dp))
        Text("Playback speed", color = ClimbPalette.textMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLAYBACK_SPEEDS.forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = {
                        playbackSpeed = speed
                        exoPlayer.setPlaybackSpeed(speed)
                    },
                    label = { Text("${speed}x") },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        sessionOverview?.let { overview ->
            SectionCard(title = "Session overview") {
                SessionOverviewCard(overview)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (metrics != null) {
            SectionCard(title = "Compared to your last attempt on this route") {
                ComparisonCardContent(comparisonState, currentAttempt.routeName)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (categoryScores.isNotEmpty()) {
            SectionCard(title = "Performance") {
                PerformanceScores(overallScore = analysis.overallScore, overallConfidence = analysis.overallConfidence, categoryScores = categoryScores)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (metrics != null) {
            CollapsibleSectionCard(title = "Summary") {
                MetricsGrid(metrics)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (metrics != null && categoryScores.isNotEmpty()) {
            val qualityIndicators = remember(metrics, events, categoryScores) { buildQualityIndicators(metrics, events, categoryScores) }
            if (qualityIndicators.isNotEmpty()) {
                CollapsibleSectionCard(title = "Session quality", badge = "${qualityIndicators.size}") {
                    QualityIndicatorList(qualityIndicators)
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (metrics != null) {
            val technicalObservations = remember(metrics) { buildTechnicalPerformanceReport(metrics) }
            CollapsibleSectionCard(title = "Technical performance", badge = "${technicalObservations.size}") {
                Column {
                    technicalObservations.forEachIndexed { index, observation ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        TechnicalObservationRow(observation)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (metrics != null) {
            if (strengths.isNotEmpty()) {
                CollapsibleSectionCard(title = "Strengths demonstrated", badge = "${strengths.size}") {
                    Column {
                        strengths.forEachIndexed { index, item ->
                            if (index > 0) Spacer(Modifier.height(14.dp))
                            StrengthRow(item = item, onSeek = onSeekAndHighlight)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (improvements.isNotEmpty()) {
                CollapsibleSectionCard(title = "Areas needing work", badge = "${improvements.size}") {
                    Column {
                        improvements.forEachIndexed { index, item ->
                            if (index > 0) Spacer(Modifier.height(14.dp))
                            ImprovementRow(item = item, onSeek = onSeekAndHighlight)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (betaOpportunities.isNotEmpty()) {
                CollapsibleSectionCard(title = "Beta optimization opportunities", badge = "${betaOpportunities.size}") {
                    Column {
                        betaOpportunities.forEachIndexed { index, item ->
                            if (index > 0) Spacer(Modifier.height(14.dp))
                            BetaOpportunityRow(item = item, onSeek = onSeekAndHighlight)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (nextSessionFocus.isNotEmpty()) {
            CollapsibleSectionCard(title = "Focus points for next session", badge = "${nextSessionFocus.size}") {
                Column {
                    nextSessionFocus.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        NextSessionFocusRow(index = index + 1, item = item)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (tips.isNotEmpty()) {
            CollapsibleSectionCard(title = "Coaching tips", badge = "${tips.size}") {
                Column {
                    tips.forEachIndexed { index, tip ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        CoachingTipRow(
                            tip = tip,
                            onJumpToMoment = { ms -> onSeekAndHighlight(ms, if (tip.category == "Footwork") FOOT_LEG_LANDMARKS else emptySet()) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (events.isNotEmpty()) {
            CollapsibleSectionCard(title = "Timeline", badge = "${events.size}") {
                Column {
                    events.forEachIndexed { index, event ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        TimelineEventRow(event = event, onSeek = onSeekAndHighlight)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (metrics != null && categoryScores.isNotEmpty()) {
            val technicalLimitations = remember(metrics, categoryScores) { buildTechnicalLimitations(metrics, categoryScores) }
            CollapsibleSectionCard(title = "Technical limitations and warnings", badge = "${technicalLimitations.size}") {
                Column {
                    technicalLimitations.forEachIndexed { index, limitation ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        TechnicalLimitationRow(limitation)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (currentAttempt.notes.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Notes") {
                Text(text = currentAttempt.notes, color = ClimbPalette.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BodyPartTrackingLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(color = ClimbPalette.gold, label = "Left hand")
        LegendDot(color = ClimbPalette.silver, label = "Right hand")
        LegendDot(color = ClimbPalette.sent, label = "Left foot")
        LegendDot(color = ClimbPalette.fell, label = "Right foot")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text = label, color = ClimbPalette.textMuted, fontSize = 10.sp)
    }
}

/**
 * Collapsed by default so the full report reads as a scannable list of section headers rather
 * than a wall of text — tapping a header expands just that section, independent of the others.
 * Mirrors [SectionCard]'s chrome (border/background/corner radius) without touching that shared
 * component, since [SectionCard] is also used by screens that don't want this behavior.
 */
@Composable
private fun CollapsibleSectionCard(title: String, badge: String? = null, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClimbPalette.surface)
            .border(1.dp, ClimbPalette.border, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { contentDescription = if (expanded) "Collapse $title" else "Expand $title" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title.uppercase(), color = ClimbPalette.textMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ClimbPalette.surfaceRaised)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(text = badge, color = ClimbPalette.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Text(
                text = if (expanded) "−" else "+",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun OverlayModeChip(mode: OverlayMode, label: String, selected: OverlayMode, onSelect: (OverlayMode) -> Unit) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}

@Composable
private fun ConfidenceBadge(confidence: Float?) {
    if (confidence == null) return
    val percent = (confidence * 100).toInt()
    val color = when {
        percent >= 70 -> ClimbPalette.sent
        percent >= 40 -> ClimbPalette.project
        else -> ClimbPalette.fell
    }
    Text(
        text = "Analysis confidence: $percent%",
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PerformanceScores(overallScore: Int?, overallConfidence: Float?, categoryScores: List<CategoryScore>) {
    var showFullscreenRadar by remember { mutableStateOf(false) }
    var showScoreHelp by remember { mutableStateOf(false) }

    Column {
        if (overallScore != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = overallScore.toString(), color = ClimbPalette.chalk, fontWeight = FontWeight.Black, fontSize = 34.sp, fontFamily = FontFamily.Monospace)
                Text(text = "/100 overall", color = ClimbPalette.textMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (overallConfidence != null) {
                    Text(text = "${(overallConfidence * 100).toInt()}% overall confidence", color = ClimbPalette.textMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "What do these scores mean? ⓘ",
                    color = ClimbPalette.chalk,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { showScoreHelp = !showScoreHelp }
                        .semantics { contentDescription = "Explain how these scores are calculated and their limitations" },
                )
            }
            if (showScoreHelp) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Every score is calculated from pose tracking only — body position and movement, not grip, hold type, or wall angle. " +
                        "Confidence reflects how much reliable tracking data supported that score, not how good the climbing was. " +
                        "Treat this as a rough, evidence-linked estimate, not an objective rating.",
                    color = ClimbPalette.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }

        if (categoryScores.size >= 3) {
            Spacer(Modifier.height(8.dp))
            PerformanceRadarChart(
                categoryScores = categoryScores,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .clickable { showFullscreenRadar = true }
                    .semantics { contentDescription = "Performance radar chart across six categories. Tap to view fullscreen." },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap chart to view fullscreen",
                color = ClimbPalette.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.clickable { showFullscreenRadar = true },
            )
        }

        Spacer(Modifier.height(14.dp))
        categoryScores.forEachIndexed { index, categoryScore ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            CategoryScoreRow(categoryScore)
        }
    }

    if (showFullscreenRadar) {
        FullscreenRadarDialog(overallScore = overallScore, categoryScores = categoryScores, onDismiss = { showFullscreenRadar = false })
    }
}

@Composable
private fun FullscreenRadarDialog(overallScore: Int?, categoryScores: List<CategoryScore>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(ClimbPalette.bg)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "Close",
                        color = ClimbPalette.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .semantics { contentDescription = "Close fullscreen performance chart" },
                    )
                }
                Spacer(Modifier.height(12.dp))
                PerformanceRadarChart(
                    categoryScores = categoryScores,
                    overallScore = overallScore,
                    labelTextSizeSp = 14f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .semantics { contentDescription = "Fullscreen performance radar chart across six categories" },
                )
                Spacer(Modifier.height(16.dp))
                categoryScores.forEach { categoryScore ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = categoryScore.category.displayName, color = ClimbPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(text = "${categoryScore.score}", color = ClimbPalette.chalk, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryScoreRow(categoryScore: CategoryScore) {
    val confidenceColor = when {
        categoryScore.confidence >= 0.7f -> ClimbPalette.sent
        categoryScore.confidence >= 0.4f -> ClimbPalette.project
        else -> ClimbPalette.fell
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = categoryScore.category.displayName, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "${categoryScore.score}", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Text(text = "${(categoryScore.confidence * 100).toInt()}% conf.", color = confidenceColor, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = categoryScore.explanation, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun QualityIndicatorList(indicators: List<QualityIndicator>) {
    Column {
        indicators.forEachIndexed { index, indicator ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (indicator.positive) ClimbPalette.sent else ClimbPalette.fell),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = indicator.label, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun TechnicalLimitationRow(limitation: TechnicalLimitation) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(if (limitation.evidence != null) ClimbPalette.project else ClimbPalette.textMuted),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = limitation.text, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ComparisonCardContent(state: AttemptComparisonState, routeName: String?) {
    when (state) {
        is AttemptComparisonState.Loading -> Text(
            text = "Checking for a previous attempt on this route…",
            color = ClimbPalette.textMuted,
            fontSize = 12.sp,
        )
        is AttemptComparisonState.NoRouteName -> Text(
            text = "This attempt has no route name set. Add one (and use the same name on future attempts) to compare them here.",
            color = ClimbPalette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        is AttemptComparisonState.NoPreviousAttempt -> Text(
            text = "No earlier completed attempt found for \"${routeName.orEmpty()}\" yet. Log and analyze another attempt with this exact route name to see a comparison here.",
            color = ClimbPalette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        is AttemptComparisonState.Found -> if (state.lines.isEmpty()) {
            Text(
                text = "This attempt's metrics were nearly identical to your last attempt on \"${routeName.orEmpty()}\".",
                color = ClimbPalette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        } else {
            ComparisonList(state.lines)
        }
    }
}

@Composable
private fun ComparisonList(lines: List<ComparisonLine>) {
    Column {
        lines.forEachIndexed { index, line ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when (line.improved) {
                                true -> ClimbPalette.sent
                                false -> ClimbPalette.fell
                                null -> ClimbPalette.textMuted
                            },
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = line.label, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun SessionOverviewCard(overview: SessionOverview) {
    Column {
        Text(text = overview.summary, color = ClimbPalette.textPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(text = overview.attemptResult, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        overview.topStrength?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = "Top strength: $it", color = ClimbPalette.sent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        overview.topWeakness?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = "Highest-priority focus: $it", color = ClimbPalette.fell, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        overview.qualityWarning?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = ClimbPalette.project, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun NextSessionFocusRow(index: Int, item: NextSessionFocusItem) {
    Column {
        Text(text = "$index. ${item.title}", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = item.action, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(3.dp))
        Text(text = "Evidence: ${item.evidence}", color = ClimbPalette.textMuted, fontSize = 10.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = "Success: ${item.successCriterion}", color = ClimbPalette.textMuted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun StrengthRow(item: StrengthItem, onSeek: (Long, Set<PoseLandmarkType>) -> Unit) {
    val modifier = if (item.startTimestampMs != null) Modifier.clickable { onSeek(item.startTimestampMs, item.relatedLandmarks) } else Modifier
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = item.title, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (item.startTimestampMs != null) {
                Text(text = formatTimestampMs(item.startTimestampMs), color = ClimbPalette.chalk, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = item.whyItMatters, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(3.dp))
        Text(text = "${(item.confidence * 100).toInt()}% confidence · ${item.evidence}", color = ClimbPalette.textMuted, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun ImprovementRow(item: ImprovementItem, onSeek: (Long, Set<PoseLandmarkType>) -> Unit) {
    val modifier = if (item.startTimestampMs != null) Modifier.clickable { onSeek(item.startTimestampMs, item.relatedLandmarks) } else Modifier
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = item.issue, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (item.startTimestampMs != null) {
                Text(text = formatTimestampMs(item.startTimestampMs), color = ClimbPalette.chalk, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = item.impact, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(3.dp))
        Text(text = item.recommendation, color = ClimbPalette.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun BetaOpportunityRow(item: BetaOpportunity, onSeek: (Long, Set<PoseLandmarkType>) -> Unit) {
    val modifier = if (item.timestampMs != null) Modifier.clickable { onSeek(item.timestampMs, item.relatedLandmarks) } else Modifier
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = item.observedIssue, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (item.timestampMs != null) {
                Text(text = formatTimestampMs(item.timestampMs), color = ClimbPalette.chalk, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = item.suggestedAlternative, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        if (item.requiresRouteContext) {
            Spacer(Modifier.height(3.dp))
            Text(text = "Depends on hold/route context not available here", color = ClimbPalette.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TechnicalObservationRow(observation: TechnicalObservation) {
    Column {
        Text(text = observation.section.uppercase(), color = ClimbPalette.textMuted, fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(text = observation.observation, color = ClimbPalette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun MetricsGrid(metrics: ClimbMetrics) {
    Column {
        MetricsRow(
            "Total time" to formatSeconds(metrics.totalDurationMs),
            "Active movement" to formatSeconds(metrics.activeMovementMs),
        )
        Spacer(Modifier.height(10.dp))
        MetricsRow(
            "Pause time" to formatSeconds(metrics.pauseTimeMs),
            "Longest pause" to formatSeconds(metrics.longestPauseMs),
        )
        Spacer(Modifier.height(10.dp))
        MetricsRow(
            "Lock-off time" to formatSeconds(metrics.totalLockoffMs),
            "Straight-arm time" to "${metrics.straightArmPercentage.toInt()}%",
        )
        Spacer(Modifier.height(10.dp))
        MetricsRow(
            "Foot adjustments" to metrics.possibleFootAdjustments.toString(),
            "Movement efficiency" to "${metrics.estimatedMovementEfficiency}",
        )
        if (metrics.possibleDisengagedLegSegments > 0) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCell(label = "Extended-leg moments", value = metrics.possibleDisengagedLegSegments.toString(), modifier = Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Lower body", color = ClimbPalette.textMuted, fontSize = 11.sp, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(10.dp))
        MetricsRow(
            "Knee range of motion" to "${metrics.kneeRangeOfMotionDegrees.roundToInt()}°",
            "Foot stability" to if (metrics.footStabilityScore > 0) "${metrics.footStabilityScore}/100" else "—",
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCell(label = "Leg-drive moments", value = metrics.legDriveCandidateCount.toString(), modifier = Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricsRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCell(label = left.first, value = left.second, modifier = Modifier.weight(1f))
        MetricCell(label = right.first, value = right.second, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        Text(text = label.uppercase(), color = ClimbPalette.textMuted, fontSize = 10.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun CoachingTipRow(tip: CoachingTip, onJumpToMoment: (Long) -> Unit) {
    val accent = when (tip.priority) {
        0 -> ClimbPalette.sent
        1 -> ClimbPalette.fell
        else -> ClimbPalette.project
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, accent, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(text = tip.category.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            }
            Text(text = "${(tip.confidence * 100).toInt()}% confidence", color = ClimbPalette.textMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(text = tip.title, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = tip.explanation, color = ClimbPalette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        tip.drill?.let { drill ->
            Spacer(Modifier.height(6.dp))
            Text(text = "Drill: $drill", color = ClimbPalette.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        tip.timestampMs?.let { ms ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Jump to ${formatTimestampMs(ms)}",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onJumpToMoment(ms) },
            )
        }
    }
}

@Composable
private fun TimelineEventRow(event: ClimbEvent, onSeek: (Long, Set<PoseLandmarkType>) -> Unit) {
    val color = eventColor(event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeek(event.startTimestampMs, relatedLandmarksFor(event.type)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = event.userVisibleTitle, color = ClimbPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = event.userVisibleDescription, color = ClimbPalette.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Text(
            text = formatTimestampMs(event.startTimestampMs),
            color = ClimbPalette.textMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
@ReadOnlyComposable
private fun eventColor(event: ClimbEvent): Color = when (event.type) {
    ClimbEventType.LONG_PAUSE -> ClimbPalette.fell
    ClimbEventType.SUSTAINED_LOCKOFF -> ClimbPalette.project
    ClimbEventType.POSSIBLE_FOOT_ADJUSTMENT -> ClimbPalette.project
    ClimbEventType.POSSIBLE_FOOT_SLIP -> ClimbPalette.fell
    ClimbEventType.POSSIBLE_DISENGAGED_LEG -> ClimbPalette.project
    ClimbEventType.LOW_CONFIDENCE_RANGE -> ClimbPalette.textMuted
    ClimbEventType.EFFICIENT_SEQUENCE -> ClimbPalette.sent
    ClimbEventType.EXCESSIVE_BODY_REPOSITIONING -> ClimbPalette.fell
    ClimbEventType.LARGE_DYNAMIC_MOVE -> ClimbPalette.chalk
    ClimbEventType.CLIMB_START, ClimbEventType.CLIMB_END -> ClimbPalette.textMuted
    ClimbEventType.HIGH_STEP -> ClimbPalette.sent
    ClimbEventType.POSSIBLE_STABILITY_LOSS -> ClimbPalette.fell
    ClimbEventType.RECOVERY -> ClimbPalette.sent
    ClimbEventType.POSSIBLE_FALL -> ClimbPalette.fell
    ClimbEventType.FINISH_STABILIZATION -> ClimbPalette.sent
    ClimbEventType.POSSIBLE_MISSED_REACH -> ClimbPalette.fell
    ClimbEventType.LEG_DRIVE_CANDIDATE -> ClimbPalette.sent
}
