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
 * name/location label. [dotColor] (reused for both the dot and the badge fill) is the route's own
 * assigned color — fixed, like [com.example.climb.data.RouteColor] in the shipped app — rather
 * than something that should shift with the user's chosen [com.example.climb.data.settings.ClimbThemeOption]. */
private data class PopularRoute(
    val name: String,
    val subtitle: String,
    val grade: String,
    val dotColor: Color,
    val badgeTextColor: Color,
)

private val popularRoutes = listOf(
    PopularRoute("Red Route", "Front wall · 8 sends", "V7", ClimbPalette.liveSendCta, ClimbPalette.liveSendTextPrimary),
    // Route-specific pink, no existing palette role — same rationale as the fixed accents in ClimbPalette.
    PopularRoute("Pink Route", "Main wall · 15 sends", "V4", Color(0xFFFF6FB3), ClimbPalette.liveSendTextPrimary),
    PopularRoute("Yellow Route", "Main wall · 5 sends", "V5", ClimbPalette.liveSendGold, ClimbPalette.liveSendBg),
)

private data class ExploreVenue(val name: String, val routesLabel: String)

private val exploreVenues = listOf(
    ExploreVenue("Main Wall", "12 routes"),
    ExploreVenue("Front Wall", "9 routes"),
)

/**
 * Live Send's Explore screen (Figma node 5:307) — club header, a search pill, a "Popular routes"
 * list of three color-coded route rows, and a two-up "Venues" tile row, over the concept's shared
 * floating nav bar + record FAB. Same page shell (Box + wallTexture + LazyColumn + bottom-pinned
 * bar/FAB overlay) as [CommunityScreen] for consistency across the 10-screen exploration; "Club" is
 * the selected nav tab here per the spec's chalk-colored "Club" label (5:476).
 */
@Composable
fun ExploreScreen(
    onSearchClick: () -> Unit,
    onRouteClick: (routeName: String) -> Unit,
    onVenueClick: (venueName: String) -> Unit,
    onNavigateFeed: () -> Unit,
    onNavigateProgress: () -> Unit,
    onNavigateRanks: () -> Unit,
    onNavigateClub: () -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column {
                    Text(
                        text = "Golomb Club",
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
            }

            item {
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
            }

            item {
                Column {
                    LiveSendSectionLabel(text = "Popular routes")
                    Spacer(Modifier.height(10.dp))
                    popularRoutes.forEachIndexed { index, route ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        PopularRouteRow(route = route, onClick = { onRouteClick(route.name) })
                    }
                }
            }

            item {
                Column {
                    LiveSendSectionLabel(text = "Venues")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        exploreVenues.forEach { venue ->
                            LiveSendTile(
                                label = venue.name,
                                sublabel = venue.routesLabel,
                                onClick = { onVenueClick(venue.name) },
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
                    LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", selected = false, onClick = onNavigateRanks),
                    LiveSendNavTab(Icons.Filled.Group, "Club", selected = true, onClick = onNavigateClub),
                ),
            )
            LiveSendFab(
                onClick = onFabClick,
                icon = Icons.Filled.Videocam,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 0.dp),
            )
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
                text = "${route.name}\n${route.subtitle}",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = "→", color = ClimbPalette.liveSendTextPrimary, fontSize = 16.sp)
        }
    }
}
