package com.example.climb.ui.livesend.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.ui.theme.ClimbPalette

/**
 * An inline shared/beta video player that starts small (so watching one doesn't take over the
 * whole card list) with a tap-to-enlarge toggle in the corner — replaces the old fillMaxWidth-only
 * players in [com.example.climb.ui.livesend.real.LiveSendSocialScreen]'s SocialSharedVideoCard,
 * [com.example.climb.ui.livesend.real.LiveSendUserProfileScreen]'s ProfileSharedVideoCard, and
 * [com.example.climb.ui.livesend.RouteDetailScreen]'s SharedAttemptCard/BetaVideoCard, all of which
 * had their own near-identical ExoPlayer setup. [aspectRatio] defaults to 9:16 (portrait attempt
 * clips); the beta video card passes its own 335:420 crop.
 */
@Composable
fun ExpandableVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 9f / 16f,
    smallWidthFraction: Float = 0.55f,
) {
    var expanded by rememberSaveable(videoUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(smallWidthFraction))
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        InlineVideoSurface(videoUrl = videoUrl, modifier = Modifier.fillMaxSize())
        // Transparent — sits above the ExoPlayer surface (an embedded native View that would
        // otherwise swallow the touch itself) so tapping anywhere on the video, not just the
        // corner icon, also toggles expanded/collapsed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = { expanded = !expanded })
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) "Shrink video" else "Enlarge video"
                },
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(ClimbPalette.liveSendBg.copy(alpha = 0.6f))
                .clickable(onClick = { expanded = !expanded })
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) "Shrink video" else "Enlarge video"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (expanded) "⤡" else "⤢", color = ClimbPalette.liveSendTextPrimary, fontSize = 14.sp)
        }
    }
}

/** Plays a remote Storage URL directly, no download step — the one real ExoPlayer wrapper every
 * inline player in Club Mode now shares, instead of each screen keeping its own copy. */
@Composable
private fun InlineVideoSurface(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    AndroidView(
        // The native controller (scrubber/play/rewind bar) would otherwise consume every tap
        // itself before it can reach ExpandableVideoPlayer's own tap-to-expand/collapse overlay —
        // disabled here since playWhenReady above already starts playback without it.
        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false } },
        modifier = modifier,
    )
}
