package com.example.climb.ui.livesend.real

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.clubs.ClubRepository
import com.example.climb.clubs.OrganizationEntity
import com.example.climb.clubs.RouteEntity
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ARCHIVED_DATE_FORMATTER = SimpleDateFormat("MMM d, yyyy", Locale.US)

/**
 * Staff-only "Route History" destination — replaces the Explore/RouteDetail bar's former
 * "Progress" tab (see [com.example.climb.navigation.ClubNavHost]'s `club_route_history` and
 * [LiveSendClubExploreHost]'s `onHistoryTab`). Shows every archived route
 * ([RouteEntity.retiredAt] non-null, set by [ClubRepository.retireRoute] — see
 * [RouteDetailScreen][com.example.climb.ui.livesend.RouteDetailScreen]'s "Archive route" action),
 * grouped into V-grade sections (ascending, ungraded last), most-recently-archived first within
 * each. A route that's actually deleted ([ClubRepository.deleteRoute]) never shows up here — it no
 * longer exists at all, unlike archiving, which just stops offering it for new attempts while
 * keeping it (and its history) fully readable.
 */
@Composable
fun RouteHistoryScreen(
    clubRepository: ClubRepository,
    organization: OrganizationEntity,
    onGoHome: () -> Unit,
    onRanksTab: () -> Unit,
    onBackToRoutes: () -> Unit,
) {
    val routes by clubRepository.observeRoutesForOrganization(organization.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val archived = remember(routes) { routes.filter { it.retiredAt != null }.sortedByDescending { it.retiredAt } }
    // groupBy preserves each bucket's relative order, so every bucket below stays sorted
    // most-recently-archived-first without re-sorting after grouping.
    val gradedBuckets = remember(archived) { archived.filter { it.vGrade != null }.groupBy { it.vGrade!! }.toSortedMap() }
    val ungraded = remember(archived) { archived.filter { it.vGrade == null } }

    Box(modifier = Modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                // Matches every other Club Mode screen's reservation for the island's real
                // footprint (56dp bar + 14dp*2 vertical margin).
                .padding(bottom = 104.dp),
        ) {
            LiveSendPageHeader(title = "Route History", onGoHome = onGoHome)
            Spacer(Modifier.height(20.dp))

            if (archived.isEmpty()) {
                Text(
                    text = "No archived routes yet. Archiving a route from its detail page moves it here.",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 13.sp,
                )
            } else {
                gradedBuckets.forEach { (vGrade, routesInGrade) ->
                    RouteHistorySection(title = "V$vGrade (${routesInGrade.size})", routes = routesInGrade)
                    Spacer(Modifier.height(16.dp))
                }
                if (ungraded.isNotEmpty()) {
                    RouteHistorySection(title = "Ungraded (${ungraded.size})", routes = ungraded)
                }
            }
        }

        LiveSendBottomBar(
            tabs = listOf(
                LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onGoHome),
                LiveSendNavTab(Icons.Filled.History, "History", selected = true, onClick = {}),
                LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", selected = false, onClick = onRanksTab),
                LiveSendNavTab(Icons.Filled.Terrain, "Routes", selected = false, onClick = onBackToRoutes),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RouteHistorySection(title: String, routes: List<RouteEntity>) {
    LiveSendSectionLabel(text = title)
    Spacer(Modifier.height(10.dp))
    routes.forEach { route ->
        RouteHistoryRow(route)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RouteHistoryRow(route: RouteEntity) {
    LiveSendCard(cornerRadius = 14, padding = 14) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = route.name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                // retiredAt is never actually null here — every route reaching this screen was
                // filtered to archived-only above — but the type is still nullable, so this falls
                // back to a plain label rather than a non-null assertion that can't really fail.
                text = route.retiredAt?.let { "Archived ${ARCHIVED_DATE_FORMATTER.format(Date(it))}" } ?: "Archived",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 12.sp,
            )
        }
    }
}
