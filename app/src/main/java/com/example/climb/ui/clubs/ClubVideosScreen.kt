package com.example.climb.ui.clubs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.playback.HoldHighlightPipeline
import com.example.climb.ui.livesend.components.LiveSendSectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "My club videos" — the caller's own analysis attempts linked to this club, sourced entirely
 * from the local Room database (see [AnalysisRepository.observeClubAttempts]). Unlike everything
 * else in Club Mode this never needed to move to Firestore: it's always just the viewer's own
 * data, so there's no cross-device visibility problem to solve here. Each attempt shows a real
 * thumbnail pulled from its own video file (not just a text row) and taps through to
 * [ClubAttemptVideoScreen] to actually watch it. Styled with the fixed liveSend palette to match
 * the rest of the member club shell.
 */
@Composable
fun ClubVideosScreen(
    currentUid: String,
    analysisRepository: AnalysisRepository,
    organization: OrganizationEntity,
    onAttemptClick: (ClimbAttemptEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attempts by analysisRepository.observeClubAttempts(currentUid, organization.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "My club videos",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            LiveSendSectionCard(title = "${organization.name} (${attempts.size})") {
                if (attempts.isEmpty()) {
                    Text(
                        text = "Nothing yet — link a video to a route from this club when you analyze a climb.",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        attempts.forEachIndexed { index, attempt ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            AttemptVideoRow(attempt = attempt, onClick = { onAttemptClick(attempt) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

@Composable
private fun AttemptVideoRow(attempt: ClimbAttemptEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttemptThumbnail(videoPath = attempt.videoPath)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attempt.routeName ?: "Untitled route",
                color = ClimbPalette.liveSendTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(text = dateFormat.format(Date(attempt.createdAt)), color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (attempt.flash) "Flash" else if (attempt.completed) "Sent" else "Fell",
                color = if (attempt.completed) ClimbPalette.sent else ClimbPalette.fell,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Real frame pulled from the attempt's own video (same extractor the hold-detection pipeline
 * uses), not a placeholder icon — cached per composition via [produceState] so scrolling the list
 * doesn't re-decode a thumbnail already shown. */
@Composable
private fun AttemptThumbnail(videoPath: String) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, videoPath) {
        value = withContext(Dispatchers.Default) {
            runCatching { HoldHighlightPipeline.extractReferenceFrame(videoPath) }.getOrNull()
        }
    }
    val bitmap = bitmapState.value

    Box(
        modifier = Modifier
            .width(96.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(10.dp))
            .background(ClimbPalette.liveSendSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(ClimbPalette.mediaScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play", tint = ClimbPalette.liveSendTextPrimary, modifier = Modifier.size(16.dp))
        }
    }
}
