package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.ClubStatsEntity
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.RouteEntity
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * "What's happening at my gym today" — the member club shell's landing tab. Deliberately built
 * only from data that already exists: [ClubRepository.observeActiveRoutesForOrganization] (new
 * this week / latest beta), [ClubRepository.observeClubLeaderboard] filtered to the current user
 * (activity totals — all-time, not a real weekly window; [ClubStatsEntity] doesn't track per-week
 * numbers, so this deliberately doesn't invent a "this week" figure it can't compute correctly),
 * and [ClubRepository.observeUpdatesForOrganization] (updates preview). There's no "current
 * project" section — the data model has no per-user-per-route attempt state to detect one from.
 * Cards here are read-only for now; deep-linking into a specific route's detail needs
 * zoneId → venue resolution that isn't wired up yet.
 */
@Composable
fun ClubOverviewScreen(
    currentUid: String,
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    val activeRoutes by clubRepository.observeActiveRoutesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val leaderboard by clubRepository.observeClubLeaderboard(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val updates by clubRepository.observeUpdatesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())

    val newRoutes = remember(activeRoutes) { activeRoutes.filter { System.currentTimeMillis() - it.createdAt < NEW_WINDOW_MS } }
    val betaRoutes = remember(activeRoutes) { activeRoutes.filter { it.betaVideoUrl != null } }
    val myStats = remember(leaderboard, currentUid) { leaderboard.firstOrNull { it.userId == currentUid } }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(text = "Overview", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 20.dp, bottom = 2.dp))
            Text(text = organization.name, color = ClimbPalette.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 20.dp))

            NewThisWeekCard(newRoutes)
            Spacer(Modifier.height(16.dp))

            if (myStats != null) {
                YourActivityCard(myStats)
                Spacer(Modifier.height(16.dp))
            }

            if (betaRoutes.isNotEmpty()) {
                LatestBetaCard(betaRoutes)
                Spacer(Modifier.height(16.dp))
            }

            SectionCard(title = "Club updates") {
                if (updates.isEmpty()) {
                    Text(text = "No updates yet.", color = ClimbPalette.textMuted, fontSize = 13.sp)
                } else {
                    Column {
                        updates.take(2).forEachIndexed { index, update ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            GymUpdateCard(update)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun NewThisWeekCard(newRoutes: List<RouteEntity>) {
    SectionCard(title = "New this week") {
        if (newRoutes.isEmpty()) {
            Text(text = "Nothing new set this week.", color = ClimbPalette.textMuted, fontSize = 13.sp)
        } else {
            Text(
                text = "${newRoutes.size} NEW ${if (newRoutes.size == 1) "ROUTE" else "ROUTES"}",
                color = ClimbPalette.sent,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                newRoutes.take(6).forEachIndexed { index, route ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    GradeChip(route.vGrade)
                }
            }
        }
    }
}

@Composable
private fun GradeChip(vGrade: Int?) {
    Box(
        modifier = Modifier.size(width = 44.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(ClimbPalette.wall),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = vGrade?.let { "V$it" } ?: "?", color = ClimbPalette.chalk, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun YourActivityCard(stats: ClubStatsEntity) {
    SectionCard(title = "Your activity here") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBlock(value = stats.totalAttempts.toString(), label = "Attempts")
            StatBlock(value = stats.totalSends.toString(), label = "Sends")
            StatBlock(value = stats.bestVGradeSent?.let { "V$it" } ?: "—", label = "Best send")
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(text = label, color = ClimbPalette.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun LatestBetaCard(betaRoutes: List<RouteEntity>) {
    SectionCard(title = "Latest beta") {
        Column {
            betaRoutes.take(3).forEachIndexed { index, route ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradeChip(route.vGrade)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = route.name, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = "▶ Beta available", color = ClimbPalette.chalk, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
