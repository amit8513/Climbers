package com.example.climb.ui.clubs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "My club videos" — the caller's own analysis attempts linked to this club, sourced entirely
 * from the local Room database (see [AnalysisRepository.observeClubAttempts]). Unlike everything
 * else in Club Mode this never needed to move to Firestore: it's always just the viewer's own
 * data, so there's no cross-device visibility problem to solve here.
 */
@Composable
fun ClubVideosScreen(
    currentUid: String,
    analysisRepository: AnalysisRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    val attempts by analysisRepository.observeClubAttempts(currentUid, organization.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "My club videos",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            SectionCard(title = "${organization.name} (${attempts.size})") {
                if (attempts.isEmpty()) {
                    Text(
                        text = "Nothing yet — link a video to a route from this club when you analyze a climb.",
                        color = ClimbPalette.textMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        attempts.forEachIndexed { index, attempt ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            AttemptRow(attempt)
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
private fun AttemptRow(attempt: ClimbAttemptEntity) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                text = attempt.routeName ?: "Untitled route",
                color = ClimbPalette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(text = dateFormat.format(Date(attempt.createdAt)), color = ClimbPalette.textMuted, fontSize = 11.sp)
        }
        Text(
            text = if (attempt.flash) "Flash" else if (attempt.completed) "Sent" else "Fell",
            color = if (attempt.completed) ClimbPalette.sent else ClimbPalette.fell,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
