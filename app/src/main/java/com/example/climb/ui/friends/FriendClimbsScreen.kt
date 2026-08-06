package com.example.climb.ui.friends

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.analysis.Visibility
import com.example.climb.sharing.FriendClimbsRepository
import com.example.climb.sharing.SharedClimb
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rowDateFormatter = SimpleDateFormat("MMM d", Locale.US)

/**
 * Read-only — everything shown here already passed [com.example.climb.sharing.FriendClimbsRepository]'s
 * security-rule-gated query, so there's no separate client-side visibility check to get wrong.
 */
@Composable
fun FriendClimbsScreen(
    friendUsername: String,
    friendUid: String,
    friendClimbsRepository: FriendClimbsRepository,
    firebaseStorage: FirebaseStorage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val climbs by friendClimbsRepository.observeSharedClimbs(friendUid).collectAsStateWithLifecycle(initialValue = null)
    var selectedClimb by remember { mutableStateOf<SharedClimb?>(null) }

    selectedClimb?.let { climb ->
        FriendClimbPlayerScreen(climb = climb, firebaseStorage = firebaseStorage, onBack = { selectedClimb = null }, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).clickable(onClick = onBack),
            )
            Text(text = "@$friendUsername", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(text = "Shared climbs", color = ClimbPalette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))

            val currentClimbs = climbs
            when {
                currentClimbs == null -> Text("Loading…", color = ClimbPalette.textSecondary, fontSize = 13.sp)
                currentClimbs.isEmpty() -> Text(
                    text = "$friendUsername hasn't shared any climbs with you yet.",
                    color = ClimbPalette.textSecondary,
                    fontSize = 13.sp,
                )
                else -> LazyColumn {
                    items(currentClimbs, key = { it.id }) { climb ->
                        SharedClimbRow(climb = climb, onClick = { selectedClimb = climb })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedClimbRow(climb: SharedClimb, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ClimbPalette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HoldBadge(grade = climb.vGrade, routeColor = climb.routeColor)
        Column(modifier = Modifier.weight(1f)) {
            OutcomePill(outcome = climb.outcome)
            if (climb.notes.isNotBlank()) {
                Text(text = climb.notes, color = ClimbPalette.textSecondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = rowDateFormatter.format(Date(climb.createdAt)), color = ClimbPalette.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text(
                text = if (climb.visibility == Visibility.PUBLIC) "Public" else "Friends",
                color = ClimbPalette.textMuted,
                fontSize = 10.sp,
            )
        }
    }
}
