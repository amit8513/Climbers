package com.example.climb.ui.clubs

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val attemptVideoDateFormatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US)

/** Plays one club attempt's own local video file directly (no Storage download needed — see
 * [ClubVideosScreen]'s doc comment on why this data never left the device). Styled with the fixed
 * liveSend palette to match the rest of the member club shell.
 *
 * No "share with club" action here — a manually recorded/imported video has no verified capture
 * provenance, so it must never be uploadable as an official club attempt (past behavior let any
 * attempt with an organizationId/routeId upload itself as if it were one, with no distinction from
 * a verified capture; that path is removed, not just hidden, until a real verified-capture flow
 * exists). This screen is view-only. */
@Composable
fun ClubAttemptVideoScreen(
    attempt: ClimbAttemptEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Without its own scroll, this screen's 9:16 video alone is nearly as tall as the whole
    // viewport on most phones, pushing the route name/outcome/date/notes entirely below the fold
    // with no way to reach them on shorter screens.
    Column(
        modifier = modifier
            .fillMaxSize()
            .wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "← Back",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).clickable(onClick = onBack),
        )

        LocalAttemptVideoPlayer(videoPath = attempt.videoPath, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(16.dp))

        Text(
            text = attempt.routeName ?: "Untitled route",
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (attempt.flash) "Flash" else if (attempt.completed) "Sent" else "Fell",
            color = if (attempt.completed) ClimbPalette.sent else ClimbPalette.fell,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = attemptVideoDateFormatter.format(Date(attempt.createdAt)),
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 12.sp,
        )
        if (attempt.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = attempt.notes, color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// Capped at just over half the screen width (rather than fillMaxWidth) so the video's 9:16 shape
// leaves enough room for the route name/outcome/date/notes/Share button to all fit on screen
// without scrolling on most phones — the whole reason this used to hide the Share button below
// the fold. verticalScroll on the parent Column stays as a safety net for long notes.
@Composable
private fun LocalAttemptVideoPlayer(videoPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
        modifier = modifier
            .fillMaxWidth(0.55f)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(16.dp))
            .background(ClimbPalette.liveSendSurface),
    )
}
