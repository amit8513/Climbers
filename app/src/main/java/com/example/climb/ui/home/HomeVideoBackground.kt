package com.example.climb.ui.home

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.climb.data.ClimbEntity
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_BACKGROUND_CLIPS = 15

/** Newest-first, capped, and filtered to files that still actually exist on disk — a climb row
 * survives its video file being cleared for space, and this must not try to play a missing file. */
fun ClimbEntity.videoFileOrNull(): File? = File(videoPath).takeIf { it.exists() && it.isFile }

/**
 * A silent, looping montage of the user's own climb videos playing behind the Home screen,
 * cutting from one clip to the next via ExoPlayer's own playlist (no dual-player crossfade rig —
 * a hard cut between clips is exactly the "cuts" the feature asked for). A brief opacity dip at
 * each transition keeps the cut from feeling like a jarring flash while staying simple: one
 * player, one listener, no coordinated pre-buffering of a second player to get right.
 *
 * Pauses on lifecycle STOP and resumes on START so it doesn't keep decoding video while the app
 * is backgrounded — this is decorative, not something worth spending battery on when unseen.
 */
@Composable
fun HomeVideoBackground(climbs: List<ClimbEntity>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val videoFiles = remember(climbs) { climbs.mapNotNull { it.videoFileOrNull() }.take(MAX_BACKGROUND_CLIPS) }
    if (videoFiles.isEmpty()) return

    val exoPlayer = remember(videoFiles) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(videoFiles.map { MediaItem.fromUri(Uri.fromFile(it)) })
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    val opacity = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Snap down then animate back up, rather than a single animateTo — a plain
                // animateTo from whatever opacity happened to be mid-flight wouldn't guarantee
                // every cut gets the same visible dip.
                scope.launch {
                    opacity.snapTo(0.55f)
                    opacity.animateTo(1f, tween(220))
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> exoPlayer.play()
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize().alpha(opacity.value),
        )
        // Darkens top and bottom (where the header and list text sit) more than the middle, so
        // the montage still reads as visible motion rather than being blanket-dimmed into
        // invisibility.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.72f),
                    0.35f to Color.Black.copy(alpha = 0.45f),
                    0.75f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.8f),
                ),
            ),
        )
    }
}
