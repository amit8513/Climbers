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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
 * [ClubVideosScreen]'s doc comment on why this data never left the device). */
@Composable
fun ClubAttemptVideoScreen(attempt: ClimbAttemptEntity, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().wallTexture().padding(horizontal = 16.dp)) {
        Text(
            text = "← Back",
            color = ClimbPalette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).clickable(onClick = onBack),
        )

        LocalAttemptVideoPlayer(videoPath = attempt.videoPath)

        Spacer(Modifier.height(16.dp))

        Text(
            text = attempt.routeName ?: "Untitled route",
            color = ClimbPalette.textPrimary,
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
            color = ClimbPalette.textMuted,
            fontSize = 12.sp,
        )
        if (attempt.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = attempt.notes, color = ClimbPalette.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
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
            .background(ClimbPalette.wall),
    )
}
