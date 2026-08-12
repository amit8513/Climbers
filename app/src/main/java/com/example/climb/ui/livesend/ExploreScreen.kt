package com.example.climb.ui.livesend

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
)

data class ExploreVenue(
    val name: String,
    val routesLabel: String,
    // Same rationale as PopularRoute.id: venue names aren't guaranteed unique, so callers that
    // need to resolve a tap back to a specific real venue must use this. Null for the mock preview.
    val id: Long? = null,
)

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
    PopularRoute("Red Route", "Front wall · 8 sends", "V7", ClimbPalette.liveSendCta, ClimbPalette.liveSendTextPrimary),
    PopularRoute("Pink Route", "Main wall · 15 sends", "V4", Color(0xFFFF6FB3), ClimbPalette.liveSendTextPrimary),
    PopularRoute("Yellow Route", "Main wall · 5 sends", "V5", ClimbPalette.liveSendGold, ClimbPalette.liveSendBg),
)
private val MOCK_VENUES = listOf(
    ExploreVenue("Main Wall", "12 routes"),
    ExploreVenue("Front Wall", "9 routes"),
)

@Composable
fun ExploreScreen(
    onSearchClick: () -> Unit,
    onRouteClick: (route: PopularRoute) -> Unit,
    onVenueClick: (venue: ExploreVenue) -> Unit,
    onNavigateFeed: () -> Unit,
    onNavigateProgress: () -> Unit,
    onNavigateRanks: () -> Unit,
    onNavigateClub: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    organizationName: String = "Golomb Club",
    routes: List<PopularRoute> = MOCK_ROUTES,
    venues: List<ExploreVenue> = MOCK_VENUES,
    // Set when the route list below is filtered down to one venue (see
    // com.example.climb.ui.livesend.real.LiveSendClubExploreHost) — shows a small clearable chip
    // so it's obvious the "Popular routes" list isn't the full org list, and gives a way back.
    venueFilterLabel: String? = null,
    onClearVenueFilter: () -> Unit = {},
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
) {
    // initialSection is intentionally unused now — see the ExploreSection doc comment above.
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        // Plain fixed Column, not LazyColumn — the user asked for no whole-page scrolling
        // anywhere in Club Mode. "Popular routes" gets its own bounded-height internal scroll
        // below instead of stretching this page; "Venues" stays fixed and always visible.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                Text(
                    text = organizationName,
                    color = ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                )
                Text(
                    text = "Explore venues & routes",
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

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveSendSectionLabel(
                        text = if (venueFilterLabel != null) "Routes · $venueFilterLabel" else "Popular routes",
                        modifier = Modifier.weight(1f),
                    )
                    if (venueFilterLabel != null) {
                        Text(
                            text = "✕ All routes",
                            color = ClimbPalette.liveSendTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable(onClick = onClearVenueFilter)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Clear venue filter, show all routes"
                                },
                        )
                    }
                    if (onAddRouteClick != null) {
                        if (venueFilterLabel != null) Spacer(Modifier.width(14.dp))
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
                if (routes.isEmpty()) {
                    EmptyState(
                        title = "No routes yet.",
                        message = if (venueFilterLabel != null) "This venue has no routes yet." else "Routes staff set will show up here.",
                    )
                } else {
                    // Fixed-height + its own scroll (~3 rows: PopularRouteRow's real height is
                    // roughly 70dp with its two-line name/subtitle text) so a long real route
                    // list scrolls in place rather than stretching the now-fixed page.
                    Column(
                        modifier = Modifier
                            .heightIn(max = 230.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        routes.forEach { route ->
                            PopularRouteRow(route = route, onClick = { onRouteClick(route) })
                        }
                    }
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveSendSectionLabel(text = "Venues", modifier = Modifier.weight(1f))
                    if (onAddVenueClick != null) {
                        Text(
                            text = "+ Add",
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
                if (venues.isEmpty()) {
                    EmptyState(title = "No venues yet.", message = "Venues staff add will show up here.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        venues.forEach { venue ->
                            LiveSendTile(
                                label = venue.name,
                                sublabel = venue.routesLabel.ifBlank { null },
                                onClick = { onVenueClick(venue) },
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            LiveSendBottomBar(
                tabs = listOf(
                    // Was labeled "Feed" (no real feed screen exists in club mode) — now a real
                    // Home affordance, matching the icon (already Home) that was already here.
                    LiveSendNavTab(Icons.Filled.Home, "Home", selected = false, onClick = onNavigateFeed),
                    LiveSendNavTab(Icons.Filled.QueryStats, "Progress", selected = false, onClick = onNavigateProgress),
                    LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", selected = false, onClick = onNavigateRanks),
                    LiveSendNavTab(Icons.Filled.Group, "Club", selected = true, onClick = onNavigateClub),
                ),
            )
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

@Composable
private fun PopularRouteRow(route: PopularRoute, onClick: () -> Unit) {
    LiveSendCard(
        cornerRadius = 16,
        padding = 16,
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = "${route.name}, ${route.grade}, ${route.subtitle}"
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
            Text(text = "→", color = ClimbPalette.liveSendTextPrimary, fontSize = 16.sp)
        }
    }
}
