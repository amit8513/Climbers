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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Videocam
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
import com.example.climb.ui.components.EmptyState
import com.example.climb.ui.livesend.components.GradeBadge
import com.example.climb.ui.livesend.components.LiveIndicatorDot
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendFab
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendTile
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/** One row of the "Popular routes" list — a color dot, a colored grade badge, and a two-line
 * name/location label. [dotColor] (reused for both the dot and the badge fill) is a *presentation*
 * cycle assigned client-side (real [com.example.climb.clubs.RouteEntity] rows carry no color of
 * their own, unlike the shipped app's local [com.example.climb.data.RouteColor]) — not something
 * that should shift with the user's chosen [com.example.climb.data.settings.ClimbThemeOption], so
 * it's drawn from the fixed `liveSend*` accents rather than a theme-reactive one. Promoted out of
 * `private` (was file-local mock data) so real call sites can build real rows.
 */
data class PopularRoute(
    val name: String,
    val subtitle: String,
    val grade: String,
    val dotColor: Color,
    val badgeTextColor: Color,
    // Real gym route names aren't guaranteed unique within an organization (different
    // venues/zones can each have their own "Red Route"), so callers that need to resolve a tap
    // back to a specific real route must use this rather than matching on [name]. Null for the
    // mock preview, which has no real routes to identify.
    val id: Long? = null,
    // Real per-route attempt counts (com.example.climb.clubs.RouteAttemptEventEntity, same source
    // as the staff Statistics screen's own time-bucketed counts) — drives the "Most attempted"
    // ranking/sort below. Zero (not fabricated) for the mock preview and for any real route with
    // no logged attempts yet.
    val attemptsToday: Int = 0,
    val attemptsThisWeek: Int = 0,
)

data class ExploreZone(
    val name: String,
    val routesLabel: String,
    // Same rationale as PopularRoute.id: zone names aren't guaranteed unique, so callers that
    // need to resolve a tap back to a specific real zone must use this. Null for the mock preview.
    val id: Long? = null,
)

/** Which time window "Most attempted" ranks/sorts by — a plain UI toggle, not a refetch, since the
 * caller already hands over both counts per [PopularRoute]. */
enum class AttemptWindow { TODAY, THIS_WEEK }

/** Which section the Dashboard's "Routes" vs "Venues" Manage tile should land on. Originally this
 * scrolled a LazyColumn to the Venues section; now that the whole page is a fixed, non-scrolling
 * layout (both sections are always simultaneously visible), there's no scroll position left to
 * differentiate — kept as a plumbing no-op (still threaded through from the Dashboard's two tiles,
 * still compiles) rather than deleted, since removing it would be a bigger, unrequested change
 * than this pass's scope. See [com.example.climb.ui.livesend.real.LiveSendClubExploreHost]. */
enum class ExploreSection { ROUTES, VENUES }

/**
 * Live Send's Explore screen (Figma node 5:307) — club header, a search pill, a "Popular routes"
 * list of color-coded route rows, and a "Venues" tile row, over the concept's shared floating nav
 * bar + record FAB. Same page shell (Box + wallTexture + LazyColumn + bottom-pinned bar/FAB
 * overlay) as [CommunityScreen] for consistency across the 10-screen exploration; "Club" is the
 * selected nav tab here per the spec's chalk-colored "Club" label (5:476).
 *
 * [routes]/[venues] are real data as of [com.example.climb.ui.livesend.real.LiveSendClubExploreHost]
 * — the original mock had exactly 3 hardcoded routes and 2 hardcoded venues; both empty-list states
 * are handled explicitly below rather than silently rendering nothing.
 */
/** Original mock content — kept as defaults so [com.example.climb.ui.livesend.LiveSendNavHost]
 * (the untouched design-exploration preview under Settings > UI Concepts) keeps compiling and
 * rendering identically without passing any of the new real-data parameters explicitly. */
private val MOCK_ROUTES = listOf(
    PopularRoute("Red Route", "Front wall · 8 sends", "V7", ClimbPalette.liveSendCta, ClimbPalette.liveSendTextPrimary, attemptsToday = 3, attemptsThisWeek = 11),
    PopularRoute("Pink Route", "Main wall · 15 sends", "V4", Color(0xFFFF6FB3), ClimbPalette.liveSendTextPrimary, attemptsToday = 5, attemptsThisWeek = 18),
    PopularRoute("Yellow Route", "Main wall · 5 sends", "V5", ClimbPalette.liveSendGold, ClimbPalette.liveSendBg, attemptsToday = 1, attemptsThisWeek = 6),
)
private val MOCK_ZONES = listOf(
    ExploreZone("Main Wall", "12 routes"),
    ExploreZone("Front Wall", "9 routes"),
)

@Composable
fun ExploreScreen(
    onSearchClick: () -> Unit,
    onRouteClick: (route: PopularRoute) -> Unit,
    onZoneClick: (zone: ExploreZone) -> Unit,
    onNavigateFeed: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateRanks: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    routes: List<PopularRoute> = MOCK_ROUTES,
    zones: List<ExploreZone> = MOCK_ZONES,
    // Set when the route list below is filtered down to one zone (see
    // com.example.climb.ui.livesend.real.LiveSendClubExploreHost) — shows a small clearable chip
    // so it's obvious the "Most attempted" list isn't the full org list, and gives a way back.
    zoneFilterLabel: String? = null,
    onClearZoneFilter: () -> Unit = {},
    // Staff Club Mode has no use for logging their own climbs from this screen — real per-context
    // gating from com.example.climb.ui.livesend.real.LiveSendClubExploreHost. Defaults to visible
    // so the mock preview and the member-facing context are unchanged.
    showRecordFab: Boolean = true,
    // Which section to land on when this screen is entered — see [ExploreSection].
    initialSection: ExploreSection = ExploreSection.ROUTES,
    // Staff-only "+ Add" actions — null hides the affordance entirely (mock preview and the
    // member-facing context pass nothing, so nothing changes there).
    onAddRouteClick: (() -> Unit)? = null,
    onAddVenueClick: (() -> Unit)? = null,
    // False in the member shell, where the outer MemberClubNavHost's own shared floating island
    // already shows for this tab (per user request that every floating island in Club Mode stay
    // consistent, rather than this screen's own distinct Home/History/Ranks/Routes bar). Staff
    // Club Mode has no such shared chrome, so it keeps rendering its own bar (default true, also
    // preserving the untouched mock preview).
    showOwnBottomBar: Boolean = true,
    // False in every real Club Mode call site — the enclosing Scaffold (staff ClubNavHost or
    // MemberClubNavHost) already reserves top system-bar inset space in the padding it hands its
    // NavHost, so applying this a second time here pushed this screen's headline visibly lower
    // than Overview/Videos/Chat (which never had their own statusBarsPadding). True only for the
    // untouched standalone design-exploration preview (LiveSendNavHost), which has no Scaffold at
    // all and needs this screen to handle its own inset.
    applyStatusBarPadding: Boolean = true,
) {
    // initialSection is intentionally unused now — see the ExploreSection doc comment above.
    var attemptWindow by remember { mutableStateOf(AttemptWindow.TODAY) }
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        // Plain fixed Column, not LazyColumn — the user asked for no whole-page scrolling
        // anywhere in Club Mode. "Most attempted" gets its own bounded-height internal scroll
        // below instead of stretching this page; "Zones" stays fixed and always visible.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                // When showOwnBottomBar is false (the member shell), MemberClubNavHost's own
                // Scaffold already reserves real, measured space for its shared floating island via
                // the padding it hands this screen, so this only needs to be a small visual gap
                // above it (see LiveSendSocialScreen's matching comment) — a bigger value here was
                // double-reserving that space, which is why this box used to sit noticeably higher
                // than the island. When showOwnBottomBar is true, THIS screen renders its own bottom
                // bar as an overlay with no such enclosing reservation, so it still needs real
                // clearance above that self-drawn bar.
                .padding(bottom = if (showOwnBottomBar) 90.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                // Was the org name (e.g. "Golomb Club") — already shown on Overview, so this
                // tab's own headline instead names what it actually shows: routes plus every
                // route's beta video.
                Text(
                    text = "Club betas",
                    color = ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                )
                Text(
                    text = "Explore zones & routes",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ClimbPalette.liveSendSurfaceRaised)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Search routes"
                    }
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = "🔍  Search routes", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
            }

            // Zones moved above Most attempted — this page's fixed, non-scrolling layout meant a
            // long enough route list (even bounded to 230dp) could still push Zones far enough
            // down to sit outside the available height, hiding it entirely with no way to scroll
            // to it. Putting Zones first guarantees it's always visible.
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveSendSectionLabel(text = "Zones", modifier = Modifier.weight(1f))
                    if (onAddVenueClick != null) {
                        Text(
                            text = "+ Add venue",
                            color = ClimbPalette.liveSendAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onAddVenueClick)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Add venue"
                                },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (zones.isEmpty()) {
                    EmptyState(title = "No zones yet.", message = "Zones staff add will show up here.")
                } else {
                    // Scrolls sideways rather than overflowing off-screen once there are more
                    // zones than fit in one row.
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        zones.forEach { zone ->
                            LiveSendTile(
                                label = zone.name,
                                sublabel = zone.routesLabel.ifBlank { null },
                                onClick = { onZoneClick(zone) },
                                width = 108.dp,
                                height = 56.dp,
                            )
                        }
                    }
                }
            }

            // weight(1f) so this section — the main content of the page — stretches to fill
            // whatever vertical space Zones and the header above don't use, instead of being
            // capped to a short fixed-height box.
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveSendSectionLabel(
                        text = if (zoneFilterLabel != null) "Routes · $zoneFilterLabel" else "Route Betas",
                        modifier = Modifier.weight(1f),
                    )
                    if (zoneFilterLabel != null) {
                        Text(
                            text = "✕ All routes",
                            color = ClimbPalette.liveSendTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onClearZoneFilter)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Clear zone filter, show all routes"
                                },
                        )
                    }
                    if (onAddRouteClick != null) {
                        if (zoneFilterLabel != null) Spacer(Modifier.width(14.dp))
                        Text(
                            text = "+ Add",
                            color = ClimbPalette.liveSendAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onAddRouteClick)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Add route"
                                },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                AttemptWindowToggle(selected = attemptWindow, onSelect = { attemptWindow = it })
                Spacer(Modifier.height(10.dp))
                if (routes.isEmpty()) {
                    EmptyState(
                        title = "No routes yet.",
                        message = if (zoneFilterLabel != null) "This zone has no routes yet." else "Routes staff set will show up here.",
                    )
                } else {
                    // Same bordered-box treatment as Social's Updates/Shared tabs — fills the rest
                    // of this section's (now weighted) space, scrolling in place rather than being
                    // capped to a short fixed-height box.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Ranked by the selected window's real attempt count (com.example.climb.clubs.
                            // RouteAttemptEventEntity, same source as the staff Statistics screen), busiest
                            // first — routes with zero attempts in this window still show, just last.
                            val attemptsFor: (PopularRoute) -> Int = { route ->
                                if (attemptWindow == AttemptWindow.TODAY) route.attemptsToday else route.attemptsThisWeek
                            }
                            routes.sortedByDescending(attemptsFor).forEach { route ->
                                PopularRouteRow(route = route, attempts = attemptsFor(route), onClick = { onRouteClick(route) })
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (showOwnBottomBar) {
                LiveSendBottomBar(
                    tabs = listOf(
                        // Was labeled "Feed" (no real feed screen exists in club mode) — now a real
                        // Home affordance, matching the icon (already Home) that was already here.
                        LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onNavigateFeed),
                        LiveSendNavTab(Icons.Filled.History, "History", selected = false, onClick = onNavigateHistory),
                        LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", selected = false, onClick = onNavigateRanks),
                        // Was "Club" (Icons.Filled.Group), which navigated to the Club Dashboard even
                        // though it was already marked selected — the same real destination "Home"
                        // already goes to in this shell, a redundant, still-live tap target. This IS
                        // the routes list, so it's now a real self tab (selected, no-op) instead.
                        LiveSendNavTab(Icons.Filled.Terrain, "Routes", selected = true, onClick = {}),
                    ),
                )
            }
            if (showRecordFab) {
                LiveSendFab(
                    onClick = onFabClick,
                    icon = Icons.Filled.Videocam,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 0.dp),
                )
            }
        }
    }
}

/** A plain two-option segmented pill, same shape as [com.example.climb.ui.livesend.real.SocialTabBar]
 * (staff Manage Social's Updates/Chat switch) — picks which of [PopularRoute.attemptsToday]/
 * [PopularRoute.attemptsThisWeek] the "Most attempted" list below ranks and labels by. */
@Composable
private fun AttemptWindowToggle(selected: AttemptWindow, onSelect: (AttemptWindow) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ClimbPalette.liveSendSurfaceRaised)
            .padding(3.dp),
    ) {
        AttemptWindow.entries.forEach { window ->
            val isSelected = window == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) ClimbPalette.liveSendAccent else Color.Transparent)
                    .clickable(onClick = { onSelect(window) })
                    .semantics { role = Role.Button }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (window == AttemptWindow.TODAY) "Today" else "This week",
                    color = if (isSelected) ClimbPalette.liveSendBg else ClimbPalette.liveSendTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PopularRouteRow(route: PopularRoute, attempts: Int, onClick: () -> Unit) {
    LiveSendCard(
        cornerRadius = 16,
        padding = 16,
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = "${route.name}, ${route.grade}, $attempts attempts"
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveIndicatorDot(color = route.dotColor, size = 14)
            Spacer(Modifier.width(12.dp))
            GradeBadge(grade = route.grade, containerColor = route.dotColor, contentColor = route.badgeTextColor)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (route.subtitle.isBlank()) route.name else "${route.name}\n${route.subtitle}",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (attempts == 1) "1 attempt" else "$attempts attempts",
                color = if (attempts > 0) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}
