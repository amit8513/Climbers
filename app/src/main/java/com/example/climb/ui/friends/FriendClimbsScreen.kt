package com.example.climb.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.example.climb.ui.livesend.components.LiveSendCard
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

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .padding(top = 20.dp)
                    .clickable(onClick = onBack)
                    .semantics { role = Role.Button },
            )
            Text(text = "@$friendUsername", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(text = "Shared climbs", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))

            val currentClimbs = climbs
            when {
                currentClimbs == null -> Text("Loading…", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
                currentClimbs.isEmpty() -> Text(
                    text = "$friendUsername hasn't shared any climbs with you yet.",
                    color = ClimbPalette.liveSendTextMuted,
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
    LiveSendCard(cornerRadius = 14, padding = 10, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldBadge(grade = climb.vGrade, routeColor = climb.routeColor)
            Column(modifier = Modifier.weight(1f)) {
                OutcomePill(outcome = climb.outcome)
                if (climb.notes.isNotBlank()) {
                    Text(text = climb.notes, color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = rowDateFormatter.format(Date(climb.createdAt)), color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(
                    text = if (climb.visibility == Visibility.PUBLIC) "Public" else "Friends",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
