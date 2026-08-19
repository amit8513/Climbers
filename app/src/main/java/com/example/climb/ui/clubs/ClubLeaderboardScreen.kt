package com.example.climb.ui.clubs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.ClubStatsEntity
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.ui.livesend.components.LiveSendSectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/** Which stat the club rank list is currently sorted by. A plain client-side re-sort of the same
 * [ClubStatsEntity] list [ClubLeaderboardScreen] already observes — no separate query per category. */
private enum class ClubRankCategory(val label: String) {
    MOST_ATTEMPTED("Most Attempted"),
    MOST_SENT("Most Sent"),
    MOST_FAILED("Most Failed"),
}

private fun ClubStatsEntity.valueFor(category: ClubRankCategory): Int = when (category) {
    ClubRankCategory.MOST_ATTEMPTED -> totalAttempts
    ClubRankCategory.MOST_SENT -> totalSends
    ClubRankCategory.MOST_FAILED -> totalAttempts - totalSends
}

/** One V grade's top sender(s) within this club — see [ClubLeaderboardScreen]'s "Best senders by
 * grade" section. [names] holds more than one entry only on a tied send count. */
private data class BestSenderRow(val vGrade: Int, val names: List<String>, val sendCount: Int)

/**
 * A lightweight, club-scoped ranking — [ClubRankCategory] switches whether members are sorted by
 * total attempts, total sends, or total fails (see [ClubStatsEntity]), plus a "Best senders by
 * grade" breakdown of who's sent the most routes at each real V grade
 * ([ClubRepository.observeRouteCompletionsForOrganization] joined against each route's real
 * [com.example.climb.clubs.RouteEntity.vGrade]). Deliberately not the app-wide Leaderboard's full
 * scoring engine, which is built entirely around the friends graph and
 * [com.example.climb.data.ClimbEntity] — neither applies here: this ranks real members of one club
 * by their real club-linked attempts/sends only, with no friends concept involved at all, and is
 * strictly a Club Mode screen — this ranking never feeds the personal, friends-based Leaderboard.
 * Display names come straight off the denormalized [ClubStatsEntity.userDisplayName]/
 * [com.example.climb.clubs.RouteCompletionEntity.userDisplayName]. Styled with the fixed liveSend
 * palette to match the rest of the member club shell.
 */
@Composable
fun ClubLeaderboardScreen(
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    modifier: Modifier = Modifier,
) {
    val stats by clubRepository.observeClubLeaderboard(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val routes by clubRepository.observeRoutesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val completions by clubRepository.observeRouteCompletionsForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())

    var category by remember { mutableStateOf(ClubRankCategory.MOST_SENT) }

    val ranked = remember(stats, category) { stats.sortedByDescending { it.valueFor(category) } }

    val bestSenders = remember(routes, completions) {
        val vGradeByRouteId = routes.mapNotNull { route -> route.vGrade?.let { route.id to it } }.toMap()
        completions
            .mapNotNull { completion -> vGradeByRouteId[completion.routeId]?.let { grade -> grade to completion } }
            .groupBy({ it.first }, { it.second })
            .map { (grade, comps) ->
                val sendsByUser = comps.groupBy { it.userId }
                val topCount = sendsByUser.values.maxOf { it.size }
                val topNames = sendsByUser.values.filter { it.size == topCount }.map { it.first().userDisplayName }.distinct().sorted()
                BestSenderRow(vGrade = grade, names = topNames, sendCount = topCount)
            }
            .sortedByDescending { it.vGrade }
    }

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "Club leaderboard",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            LiveSendSectionCard(title = organization.name) {
                RankCategoryToggle(selected = category, onSelect = { category = it })
                Spacer(Modifier.height(14.dp))
                if (ranked.isEmpty()) {
                    Text(
                        text = "No club-linked attempts yet — the first member to link a video to a route here shows up.",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        ranked.forEachIndexed { index, entry ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            LeaderboardRow(rank = index + 1, entry = entry, category = category)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LiveSendSectionCard(title = "Best senders by grade") {
                if (bestSenders.isEmpty()) {
                    Text(
                        text = "No real sends linked to a graded route yet.",
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Column {
                        bestSenders.forEachIndexed { index, row ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            BestSenderRowView(row)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

/** Same 3-wide segmented-pill shape as [com.example.climb.ui.livesend.AttemptWindowToggle] —
 * switches which [ClubRankCategory] the list above is sorted by. */
@Composable
private fun RankCategoryToggle(selected: ClubRankCategory, onSelect: (ClubRankCategory) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ClimbPalette.liveSendSurface)
            .padding(3.dp),
    ) {
        ClubRankCategory.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) ClimbPalette.liveSendAccent else Color.Transparent)
                    .clickable(onClick = { onSelect(option) })
                    .semantics { role = Role.Button }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = option.label,
                    color = if (isSelected) ClimbPalette.liveSendBg else ClimbPalette.liveSendTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: ClubStatsEntity, category: ClubRankCategory) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "#$rank ${entry.userDisplayName}, ${entry.valueFor(category)} ${category.label.lowercase()}"
        },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "#$rank", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            Column {
                Text(text = entry.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = "${entry.totalSends} sends · ${entry.totalAttempts} attempts", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "${entry.valueFor(category)}", color = ClimbPalette.liveSendAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            entry.bestVGradeSent?.let { grade ->
                Text(text = "V$grade best", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BestSenderRowView(row: BestSenderRow) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "V${row.vGrade}, best senders ${row.names.joinToString()}, ${row.sendCount} sends"
        },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "V${row.vGrade}", color = ClimbPalette.liveSendAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        Text(
            text = row.names.joinToString(", "),
            color = ClimbPalette.liveSendTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(text = if (row.sendCount == 1) "1 send" else "${row.sendCount} sends", color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp)
    }
}
