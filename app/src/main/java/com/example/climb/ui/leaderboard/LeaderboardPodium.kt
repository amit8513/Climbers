package com.example.climb.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.ui.theme.ClimbPalette

@Composable
fun LeaderboardPodium(entries: List<LeaderboardEntry>, category: LeaderboardCategory, onEntryClick: (LeaderboardEntry) -> Unit, modifier: Modifier = Modifier) {
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)
    if (first == null) return

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        second?.let { PodiumEntry(it, category, place = 2, avatarSize = 72.dp, onClick = { onEntryClick(it) }, modifier = Modifier.weight(1f)) }
            ?: Spacer(Modifier.weight(1f))
        PodiumEntry(first, category, place = 1, avatarSize = 88.dp, onClick = { onEntryClick(first) }, modifier = Modifier.weight(1f))
        third?.let { PodiumEntry(it, category, place = 3, avatarSize = 72.dp, onClick = { onEntryClick(it) }, modifier = Modifier.weight(1f)) }
            ?: Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PodiumEntry(
    entry: LeaderboardEntry,
    category: LeaderboardCategory,
    place: Int,
    avatarSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (place) { 1 -> ClimbPalette.gold; 2 -> ClimbPalette.silver; else -> ClimbPalette.bronze }
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(bottom = if (place == 1) 0.dp else 16.dp)
            .semantics { contentDescription = podiumAccessibilityLabel(category, entry, place) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (place == 1) {
            Text(text = "♛", color = ClimbPalette.gold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Box {
            InitialsAvatar(entry.displayName, avatarSize, modifier = Modifier.border(2.dp, accent, androidx.compose.foundation.shape.CircleShape))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(text = "$place", color = ClimbPalette.chalkText, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = entry.displayName, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = primaryValue(category, entry), color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
        podiumSupportingLines(category, entry).forEach { line ->
            Text(text = line, color = ClimbPalette.textSecondary, fontSize = 10.sp)
        }
    }
}
