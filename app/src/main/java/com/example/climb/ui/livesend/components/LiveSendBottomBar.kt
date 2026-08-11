package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

private val BAR_HEIGHT = 56.dp

/** One tab of [LiveSendBottomBar] — icon + label, unlike the icon-only [com.example.climb.ui.nav.ClubBarTab]
 * this concept's nav bar pairs with (Feed/Progress/Ranks/Club, or Routes/Broadcast/Members/Exit). */
data class LiveSendNavTab(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Live Send's own floating pill bottom bar — the icon-over-label nav strip repeated on HomeFeed,
 * RouteDetail, Explore, Progress, Community, and (with different tabs, no FAB) ClubDashboard.
 * Same construction as the shipped app's [com.example.climb.ui.nav.ClubBottomBar] (floating
 * rounded pill, surface-raised background) but flat/borderless per this concept's design tokens,
 * with text labels rather than icon-only. The record [LiveSendFab] is deliberately a *separate*
 * composable rather than baked in here, since only the member-shell screens overlay one — screens
 * that use it should place it themselves, offset above this bar (see the spec's Fab nodes, which
 * sit above and centered on the NavBar).
 */
@Composable
fun LiveSendBottomBar(tabs: List<LiveSendNavTab>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .height(BAR_HEIGHT)
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            LiveSendNavTabItem(tab = tab, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LiveSendNavTabItem(tab: LiveSendNavTab, modifier: Modifier = Modifier) {
    val tint = if (tab.selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted
    Column(
        modifier = modifier
            .height(BAR_HEIGHT)
            .clickable(onClick = tab.onClick)
            .semantics { contentDescription = tab.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = tab.label,
            color = tint,
            fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The circular red record/add action that floats above [LiveSendBottomBar] on the member-shell
 * screens (HomeFeed, RouteDetail, Explore, Progress, Community all show the same Fab). Its own
 * composable — not a tab — because it overlaps the bar rather than sitting inline with the other
 * tabs, and only some screens show it (ClubDashboard's staff nav bar has none).
 */
@Composable
fun LiveSendFab(onClick: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier, size: Int = 64) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(ClimbPalette.liveSendCta)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = "Record", tint = ClimbPalette.liveSendTextPrimary, modifier = Modifier.size(24.dp))
    }
}
