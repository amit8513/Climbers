package com.example.climb.ui.analysis

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.example.climb.analysis.toPoseFrames
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

        SectionCard(title = "What's next") {
            Text(
                text = "Movement metrics, pause/lock-off detection, and coaching tips are coming in the next update — this pass covers real pose tracking, played back in sync with your video.",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
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
