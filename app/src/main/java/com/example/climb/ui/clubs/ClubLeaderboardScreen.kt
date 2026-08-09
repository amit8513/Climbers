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
import androidx.compose.foundation.layout.width
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
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.ClubStatsEntity
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * A lightweight, club-scoped ranking — total sends first, hardest grade sent as tiebreaker (see
 * [ClubStatsEntity]). Deliberately not the app-wide Leaderboard's full scoring engine, which is
 * built entirely around the friends graph and [com.example.climb.data.ClimbEntity] — neither
 * applies here, since this ranks members of one club by their club-linked analysis attempts.
 * Display names come straight off [ClubStatsEntity.userDisplayName], denormalized at write time.
 */
@Composable
fun ClubLeaderboardScreen(
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    val stats by clubRepository.observeClubLeaderboard(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "Club leaderboard",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            SectionCard(title = organization.name) {
                if (stats.isEmpty()) {
                    Text(
                        text = "No club-linked attempts yet — the first member to link a video to a route here shows up.",
                        color = ClimbPalette.textMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        stats.forEachIndexed { index, entry ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            LeaderboardRow(rank = index + 1, entry = entry, displayName = entry.userDisplayName)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: ClubStatsEntity, displayName: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "#$rank", color = ClimbPalette.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            Column {
                Text(text = displayName, color = ClimbPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = "${entry.totalSends} sends · ${entry.totalAttempts} attempts", color = ClimbPalette.textMuted, fontSize = 11.sp)
            }
        }
        entry.bestVGradeSent?.let { grade ->
            Text(text = "V$grade", color = ClimbPalette.chalk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
