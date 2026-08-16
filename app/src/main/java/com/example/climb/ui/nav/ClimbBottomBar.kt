package com.example.climb.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

private const val ROUTE_HOME = "home"
private const val ROUTE_PROGRESS = "progress"
private const val ROUTE_FRIENDS = "friends"
private const val ROUTE_LEADERBOARD = "leaderboard"

/** Height of the pill itself, excluding the margins and the system nav-bar inset below it. */
private val BAR_HEIGHT = 56.dp

@Composable
fun ClimbBottomBar(
    selectedRoute: String?,
    onHomeClick: () -> Unit,
    onProgressClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onRecordClick: () -> Unit,
) {
    // The bar floats clear of the screen edge instead of being docked to it, so the record
    // button lives inside the row rather than overlapping a notch cut out of the top edge.
    // navigationBarsPadding() sits outside the pill: the gap under it grows with the device's
    // real nav-bar inset while the pill keeps a constant height.
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .height(BAR_HEIGHT)
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab(
            icon = Icons.Filled.Home,
            contentDescription = "Home",
            selected = selectedRoute == ROUTE_HOME,
            onClick = onHomeClick,
            modifier = Modifier.weight(1f),
        )
        NavTab(
            icon = Icons.Filled.QueryStats,
            contentDescription = "Progress",
            selected = selectedRoute == ROUTE_PROGRESS,
            onClick = onProgressClick,
            modifier = Modifier.weight(1f),
        )
        RecordButton(onClick = onRecordClick, modifier = Modifier.padding(horizontal = 4.dp))
        NavTab(
            icon = Icons.Filled.EmojiEvents,
            contentDescription = "Leaderboard",
            selected = selectedRoute == ROUTE_LEADERBOARD,
            onClick = onLeaderboardClick,
            modifier = Modifier.weight(1f),
        )
        NavTab(
            icon = Icons.Filled.Group,
            contentDescription = "Friends",
            selected = selectedRoute == ROUTE_FRIENDS,
            onClick = onFriendsClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavTab(icon: ImageVector, contentDescription: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(BAR_HEIGHT)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted,
            modifier = Modifier.size(if (selected) 26.dp else 22.dp),
        )
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ClimbPalette.liveSendAccent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Record a climb" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", color = ClimbPalette.liveSendAccentText, fontSize = 24.sp)
    }
}
