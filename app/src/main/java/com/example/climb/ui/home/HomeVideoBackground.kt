package com.example.climb.ui.home

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.climb.data.ClimbEntity
import com.example.climb.data.settings.HomeVideoMontageStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.climb.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_BACKGROUND_CLIPS = 15
private const val FULL_CLIP_CUT_DIP_MS = 220
private const val SHORT_MONTAGE_HOLD_MS = 4_000L
private const val SHORT_MONTAGE_TRANSITION_MS = 1_200L

/** Base scrim darkness at [SettingsStore.homeVideoOpacity] = 0 (video fully hidden behind it).
 * Never scaled all the way to zero at opacity = 1 — the floor below keeps header/list text
 * readable even when the user wants the video as visible as possible. */
private val BASE_SCRIM_STOPS = listOf(0f to 0.72f, 0.35f to 0.45f, 0.75f to 0.55f, 1f to 0.8f)
private const val SCRIM_FLOOR_FRACTION = 0.2f

/** Newest-first, capped, and filtered to files that still actually exist on disk — a climb row
 * survives its video file being cleared for space, and this must not try to play a missing file. */
fun ClimbEntity.videoFileOrNull(): File? = File(videoPath).takeIf { it.exists() && it.isFile }

/** Inflated from XML (not `PlayerView(ctx)`) so the surface_type="texture_view" attribute takes
 * effect — see [R.layout.player_view_texture_background] for why that matters for a full-screen
 * background specifically. */
private fun newBackgroundPlayerView(context: Context): PlayerView =
    LayoutInflater.from(context).inflate(R.layout.player_view_texture_background, null) as PlayerView

/**
 * A silent, looping montage of the user's own climb videos playing behind the Home screen.
 * [opacity] (0 = fully scrimmed, 1 = as visible as the legibility floor allows) is user-controlled
 * from Settings; [style] picks between playing full clips back-to-back or a short-take montage
 * with a slow dissolve between clips.
 */
@Composable
fun HomeVideoBackground(
    climbs: List<ClimbEntity>,
    opacity: Float,
    style: HomeVideoMontageStyle,
    modifier: Modifier = Modifier,
) {
    val videoFiles = remember(climbs) { climbs.mapNotNull { it.videoFileOrNull() }.take(MAX_BACKGROUND_CLIPS) }
    if (videoFiles.isEmpty()) return

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        when (style) {
            HomeVideoMontageStyle.FULL_CLIPS -> FullClipsPlayer(videoFiles, modifier = Modifier.fillMaxSize())
            HomeVideoMontageStyle.SHORT_MONTAGE -> ShortMontagePlayer(videoFiles, modifier = Modifier.fillMaxSize())
        }
        ScrimOverlay(opacity = opacity)
    }
}

@Composable
private fun ScrimOverlay(opacity: Float) {
    val darkness = (1f - opacity.coerceIn(0f, 1f))
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                *BASE_SCRIM_STOPS.map { (stop, base) ->
                    stop to Color.Black.copy(alpha = base * (darkness.coerceAtLeast(SCRIM_FLOOR_FRACTION)))
                }.toTypedArray(),
            ),
        ),
    )
}

/**
 * Plays each clip through in full, cutting to the next via ExoPlayer's own playlist (no
 * dual-player crossfade rig — a hard cut is exactly what "full clips" implies). A brief opacity
 * dip at each transition keeps the cut from feeling like a jarring flash.
 */
@Composable
private fun FullClipsPlayer(videoFiles: List<File>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoFiles) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(videoFiles.map { MediaItem.fromUri(Uri.fromFile(it)) })
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    val dipOpacity = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Snap down then animate back up, rather than a single animateTo - a plain
                // animateTo from whatever opacity happened to be mid-flight wouldn't guarantee
                // every cut gets the same visible dip.
                scope.launch {
                    dipOpacity.snapTo(0.55f)
                    dipOpacity.animateTo(1f, tween(FULL_CLIP_CUT_DIP_MS))
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

    AndroidView(
        factory = { ctx -> newBackgroundPlayerView(ctx).apply { player = exoPlayer; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } },
        // Hard-clips the TextureView's rendered pixels to this composable's actual bounds — a
        // TextureView participates in normal view Z-ordering (unlike SurfaceView), but its content
        // isn't automatically clipped by Compose the way a Composable's own drawing is, so a
        // resize-mode rescale mid-transition (a new clip loading with a different source aspect
        // ratio) could otherwise let a sliver of unscrimmed video show past this view's edge.
        modifier = modifier.clipToBounds().alpha(dipOpacity.value),
    )
}

private fun loadClip(player: ExoPlayer, file: File) {
    val mediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(file))
        // A player is set playing at the START of its fade-in and only paused once its own
        // fade-OUT finishes, so it needs to keep decoding new frames across fade-in + the full
        // hold + fade-out - two transitions' worth of headroom, not one.
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(0)
                .setEndPositionMs(SHORT_MONTAGE_HOLD_MS + 2 * SHORT_MONTAGE_TRANSITION_MS)
                .build(),
        )
        .build()
    player.setMediaItem(mediaItem)
    player.playWhenReady = false
    player.prepare()
}

/**
 * A short take of each clip (from its start) dissolving slowly into the next, via two ExoPlayers
 * swapping which is "front": the front one is visible and playing; the back one sits paused,
 * already loaded with the next clip, ready to fade in the moment the front one's hold time is up.
 */
@Composable
private fun ShortMontagePlayer(videoFiles: List<File>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerA = remember(videoFiles) { ExoPlayer.Builder(context).build().apply { volume = 0f } }
    val playerB = remember(videoFiles) { ExoPlayer.Builder(context).build().apply { volume = 0f } }
    val alphaA = remember(videoFiles) { Animatable(1f) }
    val alphaB = remember(videoFiles) { Animatable(0f) }
    var frontIsA by remember(videoFiles) { mutableStateOf(true) }

    DisposableEffect(playerA, playerB) {
        onDispose {
            playerA.release()
            playerB.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playerA, playerB) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (frontIsA) playerA.play() else playerB.play()
                Lifecycle.Event.ON_STOP -> {
                    playerA.pause()
                    playerB.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(videoFiles, playerA, playerB) {
        loadClip(playerA, videoFiles[0])
        playerA.playWhenReady = true
        loadClip(playerB, videoFiles[1 % videoFiles.size])
        alphaA.snapTo(1f)
        alphaB.snapTo(0f)
        frontIsA = true

        var currentIndex = 0
        while (isActive) {
            delay(SHORT_MONTAGE_HOLD_MS)
            val front = if (frontIsA) playerA else playerB
            val back = if (frontIsA) playerB else playerA
            val frontAlpha = if (frontIsA) alphaA else alphaB
            val backAlpha = if (frontIsA) alphaB else alphaA

            back.playWhenReady = true
            coroutineScope {
                launch { frontAlpha.animateTo(0f, tween(SHORT_MONTAGE_TRANSITION_MS.toInt())) }
                launch { backAlpha.animateTo(1f, tween(SHORT_MONTAGE_TRANSITION_MS.toInt())) }
            }

            front.playWhenReady = false
            currentIndex = (currentIndex + 1) % videoFiles.size
            frontIsA = !frontIsA
            // `front` (the variable) is now the backgrounded player - preload what should show
            // two cuts from now into it while it's hidden.
            loadClip(front, videoFiles[(currentIndex + 1) % videoFiles.size])
        }
    }

    // clipToBounds() on each player — see FullClipsPlayer's doc comment on why a TextureView needs
    // this explicitly even though it (unlike SurfaceView) otherwise respects normal Z-ordering.
    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = { ctx -> newBackgroundPlayerView(ctx).apply { player = playerA; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } },
            modifier = Modifier.fillMaxSize().alpha(alphaA.value),
        )
        AndroidView(
            factory = { ctx -> newBackgroundPlayerView(ctx).apply { player = playerB; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM } },
            modifier = Modifier.fillMaxSize().alpha(alphaB.value),
        )
    }
}
