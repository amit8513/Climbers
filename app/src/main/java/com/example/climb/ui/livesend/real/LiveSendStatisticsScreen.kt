package com.example.climb.ui.livesend.real

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.OrganizationMembershipEntity
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendProgressBar
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendStatCard
import com.example.climb.ui.livesend.formatRelativeTime
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
private const val ONE_WEEK_MS = 7 * ONE_DAY_MS

// A rolling 30-day window, not a calendar month — com.example.climb.util.DateUtils has no month
// boundary math to reuse, same reasoning ClubDashboardScreen's own doc comment gives for why
// "Sends Today" doesn't exist as a real per-day aggregate elsewhere in this app.
private const val THIRTY_DAYS_MS = 30 * ONE_DAY_MS

// A member quiet for 14+ days is flagged at risk; newly-joined members get a 3-day grace period
// first so someone who joined yesterday and hasn't opened Club Mode again yet isn't immediately
// flagged alongside members who've actually gone quiet.
private const val CHURN_INACTIVITY_MS = 14 * ONE_DAY_MS
private const val CHURN_GRACE_PERIOD_MS = 3 * ONE_DAY_MS

/**
 * Staff-only "Statistics" destination, reached from [com.example.climb.ui.livesend.ClubDashboardScreen]'s
 * Manage grid — the one screen that actually reads [ClubRepository.observeRouteAttemptEvents],
 * [ClubRepository.observeRouteStatsForOrganization], [ClubRepository.observeZonesForOrganization],
 * and member [OrganizationMembershipEntity.lastActiveAt], all built for exactly this screen (see
 * each's own doc comment) and otherwise unused anywhere in the app. Now a genuine tab in the
 * shared floating island (Home/Social/Members/Stats/Exit) alongside [LiveSendMembersScreen]/
 * [LiveSendCamerasScreen] — per the standing "every floating island in Club Mode stays
 * consistent" rule, Stats had to join the other four screens' bar rather than staying a
 * back-arrow-only pushed destination.
 *
 * Four sections: real daily/weekly active-member counts plus a churn-risk list (members quiet for
 * 14+ days), real time-bucketed attempt/send counts (today / this week / last 30 days), route
 * performance (send rate per route, busiest first), and venue traffic (attempts summed per
 * physical venue by joining an attempt event's routeId -> zoneId -> venueId).
 */
@Composable
fun LiveSendStatisticsScreen(
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    onGoHome: () -> Unit,
    onExitClub: () -> Unit,
    onNavBroadcast: () -> Unit,
    onNavMembers: () -> Unit,
) {
    val members by clubRepository.observeMembersForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val events by clubRepository.observeRouteAttemptEvents(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val routeStats by clubRepository.observeRouteStatsForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val routes by clubRepository.observeActiveRoutesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val zones by clubRepository.observeZonesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val venues by clubRepository.observeVenuesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    // Frozen once per screen visit — this is a staff analytics snapshot, not a live-ticking clock,
    // same reasoning as ProgressScreen's own `remember { System.currentTimeMillis() }`.
    val now = remember { System.currentTimeMillis() }

    val activeToday = remember(members, now) { members.count { it.lastActiveAt != null && now - it.lastActiveAt <= ONE_DAY_MS } }
    val activeThisWeek = remember(members, now) { members.count { it.lastActiveAt != null && now - it.lastActiveAt <= ONE_WEEK_MS } }
    val churnRisk = remember(members, now) {
        members
            .filter { now - it.joinedAt > CHURN_GRACE_PERIOD_MS && (it.lastActiveAt == null || now - it.lastActiveAt > CHURN_INACTIVITY_MS) }
            // Never-active members (null) sort first, then longest-quiet first.
            .sortedBy { it.lastActiveAt ?: 0L }
            .take(10)
    }

    val attemptsToday = remember(events, now) { events.count { now - it.createdAt <= ONE_DAY_MS } }
    val sendsToday = remember(events, now) { events.count { it.completed && now - it.createdAt <= ONE_DAY_MS } }
    val attemptsThisWeek = remember(events, now) { events.count { now - it.createdAt <= ONE_WEEK_MS } }
    val sendsThisWeek = remember(events, now) { events.count { it.completed && now - it.createdAt <= ONE_WEEK_MS } }
    val attemptsThisMonth = remember(events, now) { events.count { now - it.createdAt <= THIRTY_DAYS_MS } }
    val sendsThisMonth = remember(events, now) { events.count { it.completed && now - it.createdAt <= THIRTY_DAYS_MS } }

    val routePerformance = remember(routeStats, routes) {
        val routesById = routes.associateBy { it.id }
        routeStats
            .mapNotNull { stat -> routesById[stat.routeId]?.let { route -> RoutePerformanceRow(route.name, route.vGrade, stat.totalAttempts, stat.totalSends) } }
            .filter { it.totalAttempts > 0 }
            .sortedByDescending { it.totalAttempts }
            .take(8)
    }

    val venueTraffic = remember(events, routes, zones, venues) {
        val venueIdByRouteId = routes.associate { route -> route.id to zones.find { it.id == route.zoneId }?.venueId }
        val venueNameById = venues.associate { it.id to it.name }
        events
            .groupingBy { venueIdByRouteId[it.routeId] }.eachCount()
            .mapNotNull { (venueId, count) -> venueId?.let { venueNameById[it] }?.let { name -> name to count } }
            .sortedByDescending { it.second }
    }

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                // Matches every other Club Mode screen's reservation for the island's real
                // footprint (56dp bar + 14dp*2 vertical margin) so the last section never sits
                // under it.
                .padding(bottom = 104.dp),
        ) {
            LiveSendPageHeader(title = "Statistics", onGoHome = onGoHome)
            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // labelMaxLines = 2 — three-across cards are too narrow for "Active this week"/
                // "Total members" to fit on one line at this font size; wrapping instead of
                // ellipsizing keeps the label readable.
                LiveSendStatCard(value = "$activeToday", label = "Active today", modifier = Modifier.weight(1f), labelMaxLines = 2)
                LiveSendStatCard(value = "$activeThisWeek", label = "Active this week", modifier = Modifier.weight(1f), labelMaxLines = 2)
                LiveSendStatCard(value = "${members.size}", label = "Total members", modifier = Modifier.weight(1f), labelMaxLines = 2)
            }
            Spacer(Modifier.height(16.dp))

            StatsSectionCard(title = "Attempts & sends") {
                ActivityBucketRow(label = "Today", attempts = attemptsToday, sends = sendsToday)
                ActivityBucketRow(label = "This week", attempts = attemptsThisWeek, sends = sendsThisWeek)
                ActivityBucketRow(label = "Last 30 days", attempts = attemptsThisMonth, sends = sendsThisMonth)
            }
            Spacer(Modifier.height(16.dp))

            StatsSectionCard(title = "Route performance") {
                if (routePerformance.isEmpty()) {
                    EmptyHint("Route attempts will show up here once members start logging climbs.")
                } else {
                    routePerformance.forEach { row -> RoutePerformanceRowView(row) }
                    Caption("Sends ÷ attempts, busiest route first.")
                }
            }
            Spacer(Modifier.height(16.dp))

            StatsSectionCard(title = "Venue traffic") {
                if (venueTraffic.isEmpty()) {
                    EmptyHint("Attempts will break down by venue once routes across more than one venue see activity.")
                } else {
                    val maxCount = venueTraffic.maxOf { it.second }
                    venueTraffic.forEach { (name, count) -> VenueTrafficRow(name, count, maxCount) }
                }
            }
            Spacer(Modifier.height(16.dp))

            StatsSectionCard(title = "Churn risk · 14+ days quiet") {
                if (churnRisk.isEmpty()) {
                    EmptyHint("No members have gone quiet for 14+ days.")
                } else {
                    churnRisk.forEach { member -> ChurnRiskRow(member, now) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        LiveSendBottomBar(
            tabs = listOf(
                LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onGoHome),
                LiveSendNavTab(Icons.Filled.Campaign, "Social", selected = false, onClick = onNavBroadcast),
                LiveSendNavTab(Icons.Filled.Group, "Members", selected = false, onClick = onNavMembers),
                LiveSendNavTab(Icons.Filled.BarChart, "Stats", selected = true, onClick = {}),
                LiveSendNavTab(Icons.AutoMirrored.Filled.Logout, "Exit", selected = false, onClick = onExitClub),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private data class RoutePerformanceRow(val routeName: String, val vGrade: Int?, val totalAttempts: Int, val totalSends: Int)

/** Same [LiveSendCard] + [LiveSendSectionLabel] shell every card on this screen shares — mirrors
 * ProgressScreen's own private `LiveSendSectionCard` helper (not shared across files, same as
 * every other screen-local section wrapper in this codebase). */
@Composable
private fun StatsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    LiveSendCard(cornerRadius = 12, padding = 16) {
        LiveSendSectionLabel(text = title, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
}

@Composable
private fun Caption(text: String) {
    Text(text = text, color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ActivityBucketRow(label: String, attempts: Int, sends: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
        Text(
            text = "$attempts attempts · $sends sends",
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun RoutePerformanceRowView(row: RoutePerformanceRow) {
    val percent = if (row.totalAttempts == 0) 0 else (row.totalSends * 100) / row.totalAttempts
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = row.vGrade?.let { "V$it" } ?: "—",
            color = ClimbPalette.liveSendTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.routeName, color = ClimbPalette.liveSendTextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            LiveSendProgressBar(progress = percent / 100f, height = 10)
        }
        Text(
            text = "$percent%",
            color = ClimbPalette.liveSendTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = "${row.totalSends}/${row.totalAttempts}",
            color = ClimbPalette.liveSendTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(42.dp),
        )
    }
}

@Composable
private fun VenueTrafficRow(name: String, count: Int, maxCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = name,
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(90.dp),
        )
        LiveSendProgressBar(progress = count.toFloat() / maxCount, height = 12, modifier = Modifier.weight(1f))
        Text(text = "$count", color = ClimbPalette.liveSendTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun ChurnRiskRow(member: OrganizationMembershipEntity, now: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = member.userDisplayName, color = ClimbPalette.liveSendTextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            text = member.lastActiveAt?.let { "Last active ${formatRelativeTime(it, now)}" } ?: "Never active",
            color = ClimbPalette.liveSendCta,
            fontSize = 12.sp,
        )
    }
}
