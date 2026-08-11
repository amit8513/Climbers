package com.example.climb.ui.livesend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendBadge
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendStatCard
import com.example.climb.ui.livesend.components.LiveSendTile
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

private data class ActivityItem(val initial: String, val text: String, val timeAgo: String)

private val todaysActivity = listOf(
    ActivityItem("A", "Amit sent Blue Route (V7)", "2 min ago"),
    ActivityItem("P", "Pulomee flashed Pink V4", "14 min ago"),
    ActivityItem("S", "Sagar uploaded beta for Yellow V5", "31 min ago"),
)

/**
 * Live Send's Club Dashboard (Figma node 5:305) — the staff/gym-admin landing screen for "Club
 * Mode": header with the club name + a "● Club Mode" badge + a "Switch" affordance back to normal
 * climber mode, three today's-stats cards (Members / Live Now / Sends Today), a live activity
 * feed, a 2x2 "Manage" grid (Routes/Members/Venues/Broadcast), and the concept's own floating
 * staff nav bar (Routes/Broadcast/Members/Exit — no record FAB here, unlike the member-shell
 * screens, since this is an admin surface).
 */
@Composable
fun ClubDashboardScreen(
    onSwitchToNormalMode: () -> Unit,
    onManageRoutes: () -> Unit,
    onManageMembers: () -> Unit,
    onManageVenues: () -> Unit,
    onManageBroadcast: () -> Unit,
    onNavRoutes: () -> Unit,
    onNavBroadcast: () -> Unit,
    onNavMembers: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                DashboardHeader(onSwitchToNormalMode = onSwitchToNormalMode)
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveSendStatCard(value = "12", label = "Members", modifier = Modifier.weight(1f))
                    LiveSendStatCard(value = "4", label = "Live Now", modifier = Modifier.weight(1f), valueColor = ClimbPalette.liveSendAccent)
                    LiveSendStatCard(value = "27", label = "Sends Today", modifier = Modifier.weight(1f))
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveSendSectionLabel(text = "Today's Activity")
                    todaysActivity.forEach { activity ->
                        ActivityRow(activity)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveSendSectionLabel(text = "Manage")
                    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                            LiveSendTile(label = "Routes", emoji = "🧗", onClick = onManageRoutes)
                            LiveSendTile(label = "Members", emoji = "👥", onClick = onManageMembers)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                            LiveSendTile(label = "Venues", emoji = "📍", onClick = onManageVenues)
                            LiveSendTile(label = "Broadcast", emoji = "📣", onClick = onManageBroadcast)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }

        LiveSendBottomBar(
            tabs = listOf(
                LiveSendNavTab(Icons.Filled.Terrain, "Routes", selected = true, onClick = onNavRoutes),
                LiveSendNavTab(Icons.Filled.Campaign, "Broadcast", selected = false, onClick = onNavBroadcast),
                LiveSendNavTab(Icons.Filled.Group, "Members", selected = false, onClick = onNavMembers),
                LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExit),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DashboardHeader(onSwitchToNormalMode: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Golomb Club",
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
        LiveSendBadge(
            text = "● Club Mode",
            containerColor = ClimbPalette.liveSendAccent,
            contentColor = ClimbPalette.liveSendAccentText,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onSwitchToNormalMode)
                .semantics {
                    role = Role.Button
                    contentDescription = "Switch to normal mode"
                },
            contentAlignment = Alignment.Center,
        ) {
            LiveSendBadge(
                text = "Switch",
                containerColor = ClimbPalette.liveSendSurface,
                contentColor = ClimbPalette.liveSendTextPrimary,
            )
        }
    }
}

@Composable
private fun ActivityRow(activity: ActivityItem) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveSendAvatar(initial = activity.initial, size = 32)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = activity.text, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = activity.timeAgo, color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
