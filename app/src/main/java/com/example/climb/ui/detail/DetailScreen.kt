package com.example.climb.ui.detail

import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.data.ClimbRepository
import com.example.climb.playback.ColorIsolationEffect
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val detailDateFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)

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
        Box(modifier = Modifier.fillMaxSize().wallTexture(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = ClimbPalette.textSecondary)
        }
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
                onValueChangeFinished = { appliedHueOffset = hueOffsetPosition },
            )
            Spacer(Modifier.height(12.dp))
            TuningSlider(
                label = "Color sensitivity",
                readout = "${hueTolerancePosition.roundToInt()}°",
                value = hueTolerancePosition,
                valueRange = ColorIsolationEffect.MIN_HUE_TOLERANCE_DEGREES..ColorIsolationEffect.MAX_HUE_TOLERANCE_DEGREES,
                onValueChange = { hueTolerancePosition = it },
                onValueChangeFinished = { appliedHueTolerance = hueTolerancePosition },
            )
        }

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
