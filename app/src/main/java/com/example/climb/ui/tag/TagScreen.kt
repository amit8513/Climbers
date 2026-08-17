package com.example.climb.ui.tag

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.work.WorkManager
import com.example.climb.analysis.Visibility
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.data.RouteColor
import com.example.climb.playback.exportTrimmedVideo
import com.example.climb.sharing.ClimbSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToLong

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
fun TagScreen(
    videoPath: String,
    durationMs: Long,
    repository: ClimbRepository,
    currentUid: String,
    currentUsername: String,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var vGrade by remember { mutableStateOf<Int?>(null) }
    var routeColor by remember { mutableStateOf<RouteColor?>(null) }
    var outcome by remember { mutableStateOf(ClimbOutcome.SENT) }
    var notes by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(Visibility.PRIVATE) }
    var saving by remember { mutableStateOf(false) }
    var savingProgressLabel by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Handles default to the full clip (0..durationMs) — a user who never touches them gets
    // exactly the untouched recording, so trimming is opt-in rather than a forced extra step.
    var trimStartMs by remember { mutableStateOf(0f) }
    var trimEndMs by remember { mutableStateOf(durationMs.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tag this climb", style = MaterialTheme.typography.headlineSmall)

        // durationMs can be 0 for a video whose metadata couldn't be read (see
        // RecordScreen.importVideo) - trimming a clip of unknown length isn't meaningful, so the
        // whole control is skipped rather than showing a degenerate 0..0 slider.
        if (durationMs > 0) {
            TrimControl(
                videoPath = videoPath,
                durationMs = durationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                onTrimChange = { start, end -> trimStartMs = start; trimEndMs = end },
            )
        }

        Text("Route color")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(RouteColor.entries) { color ->
                ColorSwatch(
                    color = color,
                    selected = routeColor == color,
                    onClick = { routeColor = color },
                )
            }
        }

        Text("Grade")
        LazyRow(modifier = Modifier.testTag("gradeRow"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..17).toList()) { grade ->
                FilterChip(
                    selected = vGrade == grade,
                    onClick = { vGrade = if (vGrade == grade) null else grade },
                    label = { Text("V$grade") },
                )
            }
        }

        Text("Outcome")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClimbOutcome.entries.forEach { option ->
                FilterChip(
                    selected = outcome == option,
                    onClick = { outcome = option },
                    label = { Text(option.name) },
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Text("Who can see this")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUPPORTED_VISIBILITIES.forEach { option ->
                FilterChip(
                    selected = visibility == option,
                    onClick = { visibility = option },
                    label = { Text(option.displayName()) },
                )
            }
        }

        saveError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = routeColor != null && !saving,
            onClick = {
                val color = routeColor ?: return@Button
                saving = true
                saveError = null
                savingProgressLabel = null
                scope.launch {
                    // Only 0..durationMs unchanged counts as "didn't trim" - the common case for
                    // anyone who never touches the handles, which must stay a true no-op (same
                    // file, same duration) rather than a redundant re-export of the full clip.
                    val trimmed = trimStartMs > 0f || trimEndMs < durationMs.toFloat()
                    var finalVideoPath = videoPath
                    var finalDurationMs = durationMs

                    if (trimmed) {
                        savingProgressLabel = "Trimming video…"
                        val startMs = trimStartMs.roundToLong()
                        val endMs = trimEndMs.roundToLong()
                        val outputFile = File(File(videoPath).parentFile, "trim_${System.currentTimeMillis()}.mp4")
                        try {
                            exportTrimmedVideo(
                                context = context,
                                inputPath = videoPath,
                                outputPath = outputFile.absolutePath,
                                startPositionMs = startMs,
                                endPositionMs = endMs,
                            )
                        } catch (e: Exception) {
                            saving = false
                            savingProgressLabel = null
                            saveError = "Couldn't trim that video — try again, or reset the handles to save the full clip"
                            return@launch
                        }
                        // The pre-trim recording is fully superseded by the trimmed copy above -
                        // best-effort cleanup; a failure here (e.g. already gone) isn't fatal since
                        // the climb is about to be saved against the new trimmed file, not this one.
                        File(videoPath).delete()
                        finalVideoPath = outputFile.absolutePath
                        finalDurationMs = endMs - startMs
                    }

                    savingProgressLabel = "Saving..."
                    val climbId = repository.save(
                        ClimbEntity(
                            userId = currentUid,
                            videoPath = finalVideoPath,
                            createdAt = System.currentTimeMillis(),
                            durationMs = finalDurationMs,
                            vGrade = vGrade,
                            routeColor = color,
                            outcome = outcome,
                            notes = notes,
                            visibility = visibility,
                        ),
                    )
                    ClimbSyncWorker.enqueue(WorkManager.getInstance(context), currentUid, currentUsername, climbId)
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) (savingProgressLabel ?: "Saving...") else "Save climb")
        }
    }
}

/**
 * Optional dual-handle trim control: a small looping [PlayerView] preview of the currently
 * selected `[trimStartMs, trimEndMs)` range (via [MediaItem.ClippingConfiguration], the same
 * mechanism [com.example.climb.ui.home.HomeVideoBackground] uses for its background clips) plus a
 * [RangeSlider] to adjust it. Purely a preview - nothing is exported here; the actual trimmed file
 * is only produced by [exportTrimmedVideo] when the climb is saved.
 */
@Composable
private fun TrimControl(
    videoPath: String,
    durationMs: Long,
    trimStartMs: Float,
    trimEndMs: Float,
    onTrimChange: (start: Float, end: Float) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Debounced against the raw drag values (not every intermediate frame of a drag) - re-preparing
    // the player on every pixel of a RangeSlider drag would be needlessly expensive and janky.
    LaunchedEffect(videoPath, trimStartMs, trimEndMs) {
        delay(150)
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(videoPath)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs.roundToLong())
                    .setEndPositionMs(trimEndMs.roundToLong().coerceAtLeast(trimStartMs.roundToLong() + 1))
                    .build(),
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Trim (optional)")
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTrimTimestamp(trimStartMs.roundToLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatTrimTimestamp(trimEndMs.roundToLong()), style = MaterialTheme.typography.labelSmall)
        }
        RangeSlider(
            value = trimStartMs..trimEndMs,
            onValueChange = { range -> onTrimChange(range.start, range.endInclusive) },
            valueRange = 0f..durationMs.toFloat(),
        )
    }
}

private fun formatTrimTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun ColorSwatch(color: RouteColor, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color.hex))
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = if (color == RouteColor.WHITE || color == RouteColor.YELLOW) Color.Black else Color.White,
                )
            }
        }
    }
}
