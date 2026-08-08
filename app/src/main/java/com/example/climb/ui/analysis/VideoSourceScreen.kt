package com.example.climb.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbRepository
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.US)

/**
 * Entry point for the pose-analysis flow: record/import a fresh video (delegates to the
 * existing [com.example.climb.ui.record.RecordScreen] — it already has both a "Record" and a
 * "Choose from gallery" action, so this screen doesn't duplicate either), or pick a video
 * already logged as a climb in this app.
 */
@Composable
fun VideoSourceScreen(
    repository: ClimbRepository,
    currentUid: String,
    onRecordNew: () -> Unit,
    onExistingVideoSelected: (videoPath: String, durationMs: Long, sourceClimbId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val climbs by repository.observeAll(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    var showExisting by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "CLIMBERS",
                color = ClimbPalette.textMuted,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Analyze a climb",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            Text(
                text = "Pick a video to run pose analysis on.",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(22.dp))

            Button(onClick = onRecordNew, modifier = Modifier.fillMaxWidth()) {
                Text("Record or choose a new video")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(onClick = { showExisting = !showExisting }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showExisting) "Hide your climbs" else "Use an existing climb video")
            }

            if (showExisting) {
                Spacer(Modifier.height(16.dp))
                if (climbs.isEmpty()) {
                    Text(
                        text = "No recorded climbs yet.",
                        color = ClimbPalette.textSecondary,
                        fontSize = 13.sp,
                    )
                } else {
                    climbs.forEach { climb ->
                        ExistingClimbRow(
                            climb = climb,
                            onClick = { onExistingVideoSelected(climb.videoPath, climb.durationMs, climb.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExistingClimbRow(climb: ClimbEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClimbPalette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldBadge(grade = climb.vGrade, routeColor = climb.routeColor)
            Column {
                Text(
                    text = climb.routeColor.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() },
                    color = ClimbPalette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = dateFormatter.format(Date(climb.createdAt)),
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
