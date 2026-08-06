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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.ClimbAnalysisEntity
import com.example.climb.analysis.ClimbEvent
import com.example.climb.analysis.ClimbEventType
import com.example.climb.analysis.formatTimestampMs
import com.example.climb.analysis.metrics.ClimbMetrics
import com.example.climb.analysis.scoring.CategoryScore
import com.example.climb.analysis.toCategoryScores
import com.example.climb.analysis.toClimbEvents
import com.example.climb.analysis.toClimbMetrics
import com.example.climb.analysis.toCoachingTips
import com.example.climb.analysis.toPoseFrames
import com.example.climb.coaching.CoachingTip
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
private val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f)

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

    var skeletonVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1f) }

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
            if (skeletonVisible) {
                SkeletonOverlay(frame = nearestFrame, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Skeleton overlay", color = ClimbPalette.textPrimary, fontSize = 13.sp)
            Switch(checked = skeletonVisible, onCheckedChange = { skeletonVisible = it })
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

        if (metrics != null) {
            SectionCard(title = "Summary") {
                MetricsGrid(metrics)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (categoryScores.isNotEmpty()) {
            SectionCard(title = "Performance") {
                PerformanceScores(overallScore = analysis.overallScore, overallConfidence = analysis.overallConfidence, categoryScores = categoryScores)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (tips.isNotEmpty()) {
            SectionCard(title = "Coaching tips") {
                tips.forEachIndexed { index, tip ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    CoachingTipRow(tip = tip, onJumpToMoment = { ms -> exoPlayer.seekTo(ms) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (events.isNotEmpty()) {
            SectionCard(title = "Timeline") {
                events.forEachIndexed { index, event ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    TimelineEventRow(event = event, onSeek = { ms -> exoPlayer.seekTo(ms) })
                }
            }
            Spacer(Modifier.height(16.dp))
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
    Column {
        if (overallScore != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = overallScore.toString(), color = ClimbPalette.chalk, fontWeight = FontWeight.Black, fontSize = 34.sp, fontFamily = FontFamily.Monospace)
                Text(text = "/100 overall", color = ClimbPalette.textMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            }
            if (overallConfidence != null) {
                Text(text = "${(overallConfidence * 100).toInt()}% overall confidence", color = ClimbPalette.textMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
        categoryScores.forEachIndexed { index, categoryScore ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            CategoryScoreRow(categoryScore)
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
private fun TimelineEventRow(event: ClimbEvent, onSeek: (Long) -> Unit) {
    val color = eventColor(event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeek(event.startTimestampMs) },
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
}
