package com.example.climb.ui.detail

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.data.ClimbRepository
import com.example.climb.playback.ColorIsolationEffect
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun DetailScreen(
    climbId: Long,
    repository: ClimbRepository,
    onDeleted: () -> Unit,
) {
    val climb by repository.observeById(climbId).collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentClimb = climb
    if (currentClimb == null) {
        Text("Loading...")
        return
    }

    var hueTolerancePosition by remember { mutableFloatStateOf(ColorIsolationEffect.DEFAULT_HUE_TOLERANCE_DEGREES) }
    var appliedHueTolerance by remember { mutableFloatStateOf(ColorIsolationEffect.DEFAULT_HUE_TOLERANCE_DEGREES) }
    var hueOffsetPosition by remember { mutableFloatStateOf(0f) }
    var appliedHueOffset by remember { mutableFloatStateOf(0f) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${currentClimb.routeColor.name} · ${currentClimb.vGrade?.let { "V$it" } ?: "Ungraded"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(currentClimb.outcome.name, style = MaterialTheme.typography.bodyMedium)
            if (currentClimb.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(currentClimb.notes)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Hue: ${if (hueOffsetPosition >= 0) "+" else ""}${hueOffsetPosition.roundToInt()}°")
            Slider(
                value = hueOffsetPosition,
                onValueChange = { hueOffsetPosition = it },
                onValueChangeFinished = { appliedHueOffset = hueOffsetPosition },
                valueRange = ColorIsolationEffect.MIN_HUE_OFFSET_DEGREES..ColorIsolationEffect.MAX_HUE_OFFSET_DEGREES,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Color sensitivity: ${hueTolerancePosition.roundToInt()}°")
            Slider(
                value = hueTolerancePosition,
                onValueChange = { hueTolerancePosition = it },
                onValueChangeFinished = { appliedHueTolerance = hueTolerancePosition },
                valueRange = ColorIsolationEffect.MIN_HUE_TOLERANCE_DEGREES..ColorIsolationEffect.MAX_HUE_TOLERANCE_DEGREES,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    repository.delete(currentClimb)
                    onDeleted()
                }
            }) {
                Text("Delete")
            }
        }
    }
}
