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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.climb.clubs.ClubRepository
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val attemptVideoDateFormatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US)

/** Plays one club attempt's own local video file directly (no Storage download needed — see
 * [ClubVideosScreen]'s doc comment on why this data never left the device). Styled with the fixed
 * liveSend palette to match the rest of the member club shell. When the attempt is linked to a
 * real gym route ([ClimbAttemptEntity.organizationId]/[ClimbAttemptEntity.routeId] both set),
 * shows a "Share with club" action that uploads this video ([ClubRepository.shareAttemptVideo]) so
 * other members can watch it on that route's page, alongside the staff beta video. */
@Composable
fun ClubAttemptVideoScreen(
    attempt: ClimbAttemptEntity,
    onBack: () -> Unit,
    clubRepository: ClubRepository,
    currentUid: String,
    currentUsername: String,
    modifier: Modifier = Modifier,
) {
    val organizationId = attempt.organizationId
    val routeId = attempt.routeId
    val scope = rememberCoroutineScope()
    var sharing by remember(attempt.id) { mutableStateOf(false) }
    var shared by remember(attempt.id) { mutableStateOf(false) }
    var shareError by remember(attempt.id) { mutableStateOf<String?>(null) }

    // Without its own scroll, this screen's 9:16 video alone is nearly as tall as the whole
    // viewport on most phones, pushing the route name/outcome/date/notes/Share-with-club action
    // entirely below the fold with no way to reach them — a real reported bug (tapping "Share"
    // wasn't possible because it was never actually on screen).
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

        LocalAttemptVideoPlayer(videoPath = attempt.videoPath)

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

        // Only attempts linked to a real gym route can be shared — there's no route page to show
        // them on otherwise (see ClubRepository.shareAttemptVideo).
        if (organizationId != null && routeId != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    shared -> "Shared with club ✓"
                    sharing -> "Sharing…"
                    else -> "Share with club"
                },
                color = if (shared) ClimbPalette.liveSendTextMuted else ClimbPalette.liveSendAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = !sharing && !shared) {
                    sharing = true
                    shareError = null
                    scope.launch {
                        val result = clubRepository.shareAttemptVideo(
                            organizationId = organizationId,
                            routeId = routeId,
                            userId = currentUid,
                            userDisplayName = currentUsername,
                            routeName = attempt.routeName,
                            localVideoPath = attempt.videoPath,
                            vGrade = attempt.vGrade,
                            completed = attempt.completed,
                            flash = attempt.flash,
                        )
                        sharing = false
                        result.onSuccess { shared = true }
                        result.onFailure { shareError = it.message ?: "Couldn't share video" }
                    }
                },
            )
            shareError?.let {
                Text(text = it, color = ClimbPalette.liveSendCta, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LocalAttemptVideoPlayer(videoPath: String) {
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
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(16.dp))
            .background(ClimbPalette.liveSendSurface),
    )
}
