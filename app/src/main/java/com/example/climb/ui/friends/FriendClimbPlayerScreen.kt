package com.example.climb.ui.friends

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.sharing.SharedClimb
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val playerDateFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)

/**
 * Fetches a fresh Storage download URL every time this screen opens rather than caching one —
 * only a client whose read already passed the Storage rules in `storage.rules` can obtain that
 * token, so nothing is exposed by fetching it live, and nothing stale is ever held onto. No
 * editing controls here (no hue/tolerance tuning, no delete) — this is someone else's climb.
 */
@Composable
fun FriendClimbPlayerScreen(climb: SharedClimb, firebaseStorage: FirebaseStorage, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(climb.videoStoragePath) {
        runCatching { firebaseStorage.reference.child(climb.videoStoragePath).downloadUrl.await() }
            .onSuccess { downloadUrl = it.toString() }
            .onFailure { loadError = it.message ?: "Couldn't load this video" }
    }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp).clickable(onClick = onBack),
            )

            val currentUrl = downloadUrl
            val currentError = loadError
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ClimbPalette.wall),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    currentUrl != null -> RemoteVideoPlayer(url = currentUrl)
                    currentError != null -> Text(text = currentError, color = ClimbPalette.textSecondary, fontSize = 13.sp)
                    else -> CircularProgressIndicator(color = ClimbPalette.chalk, strokeWidth = 2.dp, modifier = Modifier.height(28.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HoldBadge(grade = climb.vGrade, routeColor = climb.routeColor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "@${climb.ownerUsername}", color = ClimbPalette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    OutcomePill(outcome = climb.outcome)
                }
                Text(
                    text = playerDateFormatter.format(Date(climb.createdAt)),
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (climb.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(text = climb.notes, color = ClimbPalette.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RemoteVideoPlayer(url: String) {
    val context = LocalContext.current
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } }, modifier = Modifier.fillMaxSize())
}
