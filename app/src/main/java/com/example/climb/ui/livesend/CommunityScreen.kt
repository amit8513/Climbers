package com.example.climb.ui.livesend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendFab
import com.example.climb.ui.livesend.components.LiveSendLeaderRow
import com.example.climb.ui.livesend.components.LiveSendMemberRow
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/** The two tabs at the top of [CommunityScreen] — "Leaderboard" (weekly leaders card) and
 * "Members" (club roster card). Figma's flattened export contains both cards' content in the same
 * frame with "Leaderboard" shown selected (chalk text + underline); the underline/selected-tab
 * affordance only makes sense if it actually swaps which card is visible, so that's the one bit of
 * behavior added on top of the spec's static layers rather than showing both cards at once. */
private enum class CommunityTab { LEADERBOARD, MEMBERS }

private data class WeeklyLeader(val rank: Int, val name: String, val grade: String, val medalColor: Color, val medalTextColor: Color)

private val weeklyLeaders = listOf(
    WeeklyLeader(1, "Amit", "V7", ClimbPalette.gold, ClimbPalette.liveSendBg),
    WeeklyLeader(2, "Pulomee", "V5", ClimbPalette.silver, ClimbPalette.liveSendBg),
    WeeklyLeader(3, "Sagar", "V4", ClimbPalette.bronze, ClimbPalette.liveSendTextPrimary),
)

private data class ClubMember(val initial: String, val name: String, val roleLabel: String, val isAdmin: Boolean)

private val clubMembers = listOf(
    ClubMember("A", "Amit", "Admin", isAdmin = true),
    ClubMember("P", "Pulomee", "Member", isAdmin = false),
)

/**
 * Live Send's Community screen (Figma node 5:309) — a "Leaderboard" / "Members" tab row over a
 * weekly-leaders card (top 3 medal ranks) or a club-roster card (avatar + name + role pill), with
 * the concept's shared floating nav bar + record FAB pinned to the bottom.
 */
@Composable
fun CommunityScreen(
    onNavigateFeed: () -> Unit,
    onNavigateProgress: () -> Unit,
    onNavigateRanks: () -> Unit,
    onNavigateClub: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(CommunityTab.LEADERBOARD.ordinal) }

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    text = "Community",
                    color = ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                )
            }

            item {
                CommunityTabRow(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                )
            }

            item {
                if (selectedTab == CommunityTab.LEADERBOARD.ordinal) {
                    LiveSendCard(cornerRadius = 20) {
                        LiveSendSectionLabel(text = "Weekly Leaders", forceUppercase = false, fontSize = 13)
                        Spacer(Modifier.height(16.dp))
                        weeklyLeaders.forEachIndexed { index, leader ->
                            if (index > 0) Spacer(Modifier.height(16.dp))
                            LiveSendLeaderRow(
                                rank = leader.rank,
                                name = leader.name,
                                grade = leader.grade,
                                medalColor = leader.medalColor,
                                medalTextColor = leader.medalTextColor,
                            )
                        }
                    }
                } else {
                    LiveSendCard(cornerRadius = 20) {
                        LiveSendSectionLabel(text = "Club Members · ${clubMembers.size}", forceUppercase = false, fontSize = 13)
                        Spacer(Modifier.height(12.dp))
                        clubMembers.forEach { member ->
                            LiveSendMemberRow(
                                initial = member.initial,
                                name = member.name,
                                roleLabel = member.roleLabel,
                                roleColor = if (member.isAdmin) ClimbPalette.liveSendCta else ClimbPalette.liveSendTextMuted,
                                roleTextColor = if (member.isAdmin) ClimbPalette.liveSendTextPrimary else ClimbPalette.liveSendBg,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            LiveSendBottomBar(
                tabs = listOf(
                    LiveSendNavTab(Icons.Filled.Home, "Feed", selected = false, onClick = onNavigateFeed),
                    LiveSendNavTab(Icons.Filled.QueryStats, "Progress", selected = false, onClick = onNavigateProgress),
                    LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", selected = true, onClick = onNavigateRanks),
                    LiveSendNavTab(Icons.Filled.Group, "Club", selected = false, onClick = onNavigateClub),
                ),
            )
            LiveSendFab(
                onClick = onFabClick,
                icon = Icons.Filled.Videocam,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-18).dp),
            )
        }
    }
}

@Composable
private fun CommunityTabRow(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        CommunityTabLabel(
            text = "Leaderboard",
            selected = selectedTab == CommunityTab.LEADERBOARD.ordinal,
            onClick = { onSelectTab(CommunityTab.LEADERBOARD.ordinal) },
        )
        CommunityTabLabel(
            text = "Members",
            selected = selectedTab == CommunityTab.MEMBERS.ordinal,
            onClick = { onSelectTab(CommunityTab.MEMBERS.ordinal) },
        )
    }
}

/** One top tab + its own 90x3dp underline indicator (the spec's fixed "Underline" rectangle
 * dimensions/radius/color), shown filled only while [selected] so it tracks whichever tab is
 * active rather than being pinned under "Leaderboard" forever. */
@Composable
private fun CommunityTabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = text
            }
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) ClimbPalette.liveSendAccent else Color.Transparent),
        )
    }
}
