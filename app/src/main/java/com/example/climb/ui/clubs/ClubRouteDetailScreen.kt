package com.example.climb.ui.clubs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.RouteEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import kotlinx.coroutines.launch

/**
 * A single route's page — beta video, "how many people tried it / sent it / fell" analytics, and
 * (staff-only) uploading a beta video and retiring the route. Reached by tapping a route in
 * [ZoneDetailContent], from both Club Mode's "Manage" tab and the member club's "Routes" tab.
 */
@Composable
fun RouteDetailContent(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    route: RouteEntity,
    isStaff: Boolean,
    onRetired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats by clubRepository.observeRouteStats(route.id).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${route.name}${route.vGrade?.let { " (V$it)" } ?: ""}",
            color = ClimbPalette.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val betaVideoUrl = route.betaVideoUrl
        if (betaVideoUrl != null) {
            SectionCard(title = "Beta") {
                BetaVideoPlayer(videoUrl = betaVideoUrl)
            }
            Spacer(Modifier.height(16.dp))
        }

        SectionCard(title = "Analytics") {
            val attempts = stats?.totalAttempts ?: 0
            val sends = stats?.totalSends ?: 0
            val fails = stats?.totalFails ?: 0
            if (attempts == 0) {
                Text(text = "No attempts logged yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
            } else {
                val sendRate = (sends * 100f / attempts).let { "%.0f".format(it) }
                Column {
                    StatRow("Tried", "$attempts")
                    Spacer(Modifier.height(8.dp))
                    StatRow("Sent", "$sends")
                    Spacer(Modifier.height(8.dp))
                    StatRow("Fell", "$fails")
                    Spacer(Modifier.height(8.dp))
                    StatRow("Send rate", "$sendRate%")
                }
            }
        }

        if (isStaff) {
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Beta video") {
                BetaVideoUploader(currentUid = currentUid, clubRepository = clubRepository, organization = organization, route = route)
            }
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Retire this route") {
                Text(
                    text = "Existing attempts and analyses linked to it stay readable — it just stops being offered for new ones.",
                    color = ClimbPalette.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Button(
                    onClick = { scope.launch { clubRepository.retireRoute(organization.id, currentUid, route); onRetired() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Retire route") }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = ClimbPalette.textSecondary, fontSize = 14.sp)
        Text(text = value, color = ClimbPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BetaVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun BetaVideoUploader(currentUid: String, clubRepository: ClubRepository, organization: OrganizationEntity, route: RouteEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        errorMessage = null
        scope.launch {
            val contentType = context.contentResolver.getType(uri)
            val uploadResult = clubRepository.uploadBetaVideo(organization.id, currentUid, route.id, uri, contentType)
            val url = uploadResult.getOrNull()
            if (url == null) {
                uploading = false
                errorMessage = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                return@launch
            }
            val attachResult = clubRepository.setRouteBetaVideo(organization.id, currentUid, route, url)
            uploading = false
            attachResult.onFailure { errorMessage = it.message ?: "Something went wrong" }
        }
    }

    Text(
        text = if (route.betaVideoUrl != null) "Replace the video showing how to climb this route." else "Show members how to climb this route.",
        color = ClimbPalette.textMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    errorMessage?.let { Text(text = it, color = ClimbPalette.fell, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
    Button(
        enabled = !uploading,
        modifier = Modifier.fillMaxWidth(),
        onClick = { pickVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
    ) {
        if (uploading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(if (route.betaVideoUrl != null) "Replace beta video" else "Upload beta video")
        }
    }
}
