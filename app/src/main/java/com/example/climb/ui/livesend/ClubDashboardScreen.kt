package com.example.climb.ui.livesend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
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
import com.example.climb.ui.components.EmptyState
import com.example.climb.ui.livesend.components.LiveSendAvatar
import com.example.climb.ui.livesend.components.LiveSendBadge
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendStatCard
import com.example.climb.ui.livesend.components.LiveSendTile
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.util.concurrent.TimeUnit

/** Promoted out of `private` (was file-local mock data) so real call sites — see
 * [com.example.climb.navigation.ClubNavHost] — can build real rows from
 * [com.example.climb.clubs.ClubUpdateEntity]. */
data class ActivityItem(val initial: String, val text: String, val timeAgo: String, val photoUrl: String? = null)

/** Coarse, locale-agnostic "N min/hr/day ago" — this project has no existing relative-time
 * formatter to reuse ([com.example.climb.util.DateUtils] only has day/week boundary math). */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val deltaMs = (nowMillis - epochMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(deltaMs)
    val hours = TimeUnit.MILLISECONDS.toHours(deltaMs)
    val days = TimeUnit.MILLISECONDS.toDays(deltaMs)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "$days d ago"
    }
}

/**
 * Live Send's Club Dashboard (Figma node 5:305) — the staff/gym-admin landing screen for "Club
 * Mode": header with the club name + a "● Club Mode" badge + a "Switch" affordance back to normal
 * climber mode, three stats cards, a bounded-height recent-activity feed, a 2x2 "Manage" grid
 * (Routes/Members/Cameras/Broadcast), and the concept's own floating staff nav bar
 * (Home/Broadcast/Members/Exit — no record FAB here, unlike the member-shell screens, since this
 * is an admin surface). The whole page is a fixed, non-scrolling layout — only Recent Activity
 * scrolls internally within its own bounded height — per user request; Routes moved off this bar
 * (still reachable via the Manage grid's Routes tile) in favor of an explicit Home tab. The
 * "Venues" tile ([onManageVenues], name kept for now to avoid a wider param rename) now opens a
 * real Cameras-management screen ([com.example.climb.ui.livesend.real.LiveSendCamerasScreen]) per
 * user request — venues themselves still exist and still back real route creation, this only
 * changed what that Manage-grid entry point leads to.
 *
 * Real-data note ([com.example.climb.navigation.ClubNavHost]): the mock's "Live Now" stat had no
 * real backing (no presence system exists anywhere in this app) — [pendingJoinRequestCount] (a
 * real, honest club-relevant number) takes its slot instead. Likewise "Sends Today" has no real
 * per-day aggregate ([com.example.climb.clubs.ClubStatsEntity] tracks lifetime totals only), so
 * [totalSends] is the club's all-time total, and the tile below is labeled to match rather than
 * mislabeling an approximation as "today." The mock's 3 hardcoded activity items (no cross-member
 * activity log exists) are replaced by [recentUpdates] — real staff-posted club updates.
 */
private val MOCK_ACTIVITY = listOf(
    ActivityItem("A", "Amit sent Blue Route (V7)", "2 min ago"),
    ActivityItem("P", "Pulomee flashed Pink V4", "14 min ago"),
    ActivityItem("S", "Sagar uploaded beta for Yellow V5", "31 min ago"),
)

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
    // Defaults reproduce the original mock content exactly, so
    // com.example.climb.ui.livesend.LiveSendNavHost (the untouched preview) keeps compiling and
    // rendering identically without passing any of these explicitly.
    onGoHome: () -> Unit = onExit,
    onOpenSettings: () -> Unit = {},
    clubName: String = "Golomb Club",
    memberCount: Int = 12,
    pendingJoinRequestCount: Int = 4,
    totalSends: Int = 27,
    recentUpdates: List<ActivityItem> = MOCK_ACTIVITY,
    // False for the real call site (ClubNavHost) — its Scaffold already reserves top system-bar
    // inset space, so applying this a second time pushed this screen's headline visibly lower
    // than the real app's own screens. True only for the untouched standalone design-exploration
    // preview (LiveSendNavHost), which has no Scaffold at all and needs this screen's own inset.
    applyStatusBarPadding: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        // Plain fixed Column, not LazyColumn — the user asked for no whole-page scrolling
        // anywhere in Club Mode. The one section that can grow without bound (Recent Activity)
        // gets its own bounded-height internal scroll below instead of stretching this page.
        // The trailing bottom padding (84dp bar footprint + margin) reserves space so this fixed
        // content never sits under the floating bottom bar overlay.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DashboardHeader(clubName = clubName, onSwitchToNormalMode = onSwitchToNormalMode, onOpenSettings = onOpenSettings)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveSendStatCard(value = "$memberCount", label = "Members", modifier = Modifier.weight(1f))
                LiveSendStatCard(value = "$pendingJoinRequestCount", label = "Requests", modifier = Modifier.weight(1f), valueColor = ClimbPalette.liveSendAccent)
                LiveSendStatCard(value = "$totalSends", label = "Total Sends", modifier = Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveSendSectionLabel(text = "Recent Activity")
                if (recentUpdates.isEmpty()) {
                    EmptyState(title = "No updates yet.", message = "Posted announcements will show up here.")
                } else {
                    // Fixed-height + its own scroll so a growing real update list scrolls in
                    // place (exactly 2 full rows visible: 2 * 64dp row height + 1 * 10dp gap)
                    // instead of stretching the page.
                    Column(
                        modifier = Modifier
                            .heightIn(max = 138.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        recentUpdates.forEach { activity ->
                            ActivityRow(activity)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LiveSendSectionLabel(text = "Manage", modifier = Modifier.fillMaxWidth(), forceUppercase = true)
                Column(verticalArrangement = Arrangement.spacedBy(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        LiveSendTile(label = "Routes", emoji = "🧗", onClick = onManageRoutes)
                        LiveSendTile(label = "Members", emoji = "👥", onClick = onManageMembers)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        LiveSendTile(label = "Cameras", emoji = "📷", onClick = onManageVenues)
                        LiveSendTile(label = "Social", emoji = "📣", onClick = onManageBroadcast)
                    }
                }
            }
        }

        LiveSendBottomBar(
            tabs = listOf(
                // Was a "Routes" tab (Explore is still reachable via the Manage grid's Routes
                // tile above) — replaced with an explicit Home tab per user request.
                // selected=true — this screen IS Club Home, unlike every other screen's Home tab.
                // onGoHome is a real no-op here (see ClubNavHost) rather than a self-navigate,
                // which used to cause a visible transition flash for going nowhere.
                LiveSendNavTab(Icons.Filled.Home, "Home", selected = true, onClick = onGoHome),
                LiveSendNavTab(Icons.Filled.Campaign, "Social", selected = false, onClick = onNavBroadcast),
                LiveSendNavTab(Icons.Filled.Group, "Members", selected = false, onClick = onNavMembers),
                LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExit),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun DashboardHeader(clubName: String, onSwitchToNormalMode: () -> Unit, onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Settings-from-Club-Mode is disabled for now per user request — the icon/button is
        // removed rather than left clickable-but-dead. onOpenSettings/the SETTINGS_PREVIEW
        // destination in ClubNavHost are both still wired and intact, so re-enabling this later
        // is just restoring this Box (see git history for the exact prior version).
        Text(
            text = clubName,
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
