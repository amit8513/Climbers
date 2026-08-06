package com.example.climb.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.NotchedBarShape

private const val ROUTE_HOME = "home"
private const val ROUTE_PROGRESS = "progress"
private const val ROUTE_FRIENDS = "friends"
private const val ROUTE_LEADERBOARD = "leaderboard"

@Composable
fun ClimbBottomBar(
    selectedRoute: String?,
    onHomeClick: () -> Unit,
    onProgressClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onRecordClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // No fixed height here — the bar wraps its content (tab row + real nav-bar inset),
        // so it grows to whatever this device's system nav bar actually needs instead of a
        // guessed constant. The colored background/notch/stitch continue behind that inset.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NotchedBarShape(notchRadius = 36.dp))
                .background(ClimbPalette.surface)
                .drawBehind { drawStitchTexture() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .navigationBarsPadding(),
            ) {
                // Two equal-width halves around a fixed-width center gap, rather than weighting
                // each tab equally — that keeps the notch (and the FAB floating above it)
                // centered regardless of how the tabs are distributed either side of it.
                Row(modifier = Modifier.weight(1f)) {
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
                }
                Spacer(Modifier.width(72.dp))
                Row(modifier = Modifier.weight(1f)) {
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
        }

        RecordFab(
            onClick = onRecordClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp),
        )
    }
}

@Composable
private fun NavTab(icon: ImageVector, contentDescription: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) ClimbPalette.textPrimary else ClimbPalette.textMuted,
            modifier = Modifier.size(if (selected) 26.dp else 22.dp),
        )
    }
}

@Composable
private fun RecordFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(
                    brush = Brush.radialGradient(colors = listOf(ClimbPalette.chalkDust, Color.Transparent)),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ClimbPalette.chalk)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Record a climb" },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = ClimbPalette.chalkText, fontSize = 26.sp)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStitchTexture() {
    // Fixed-size band regardless of the bar's total height (which grows with the device's
    // real nav-bar inset) — otherwise the diamond tile would stretch into one giant X.
    val bandHeight = 40.dp.toPx()
    val spacing = 10.dp.toPx()
    val strokeWidth = 1.dp.toPx()
    var x = -bandHeight
    while (x < size.width) {
        drawLine(ClimbPalette.border, Offset(x, bandHeight), Offset(x + bandHeight, 0f), strokeWidth = strokeWidth)
        drawLine(ClimbPalette.border, Offset(x, 0f), Offset(x + bandHeight, bandHeight), strokeWidth = strokeWidth)
        x += spacing
    }
}
