package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.clubs.RouteEntity
import com.example.climb.ui.theme.ClimbPalette

/** Shared with [ClubOverviewScreen] and [ClubsScreen]'s zone route list — same package, so no
 * import needed at either use site. */
internal const val NEW_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/**
 * A route row — replaces the plain `Text` rows [ZoneDetailContent] used to list routes. Shows only
 * what's actually on [RouteEntity] today (grade, name, beta presence, how recently it was set) —
 * no route color/style/setter here yet since those live on the separate, not-yet-fetched-in-this-list
 * [com.example.climb.clubs.RouteVersionEntity]; wiring that through (and swapping the grade box for
 * a real [com.example.climb.ui.components.HoldBadge]) is a natural follow-up once a route's list
 * item has its version already loaded.
 */
@Composable
fun RouteCard(route: RouteEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isNew = System.currentTimeMillis() - route.createdAt < NEW_WINDOW_MS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ClimbPalette.surface)
            .border(1.dp, ClimbPalette.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ClimbPalette.wall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = route.vGrade?.let { "V$it" } ?: "?",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = route.name, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (isNew || route.betaVideoUrl != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) {
                        Text(text = "NEW", color = ClimbPalette.sent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                        if (route.betaVideoUrl != null) Spacer(Modifier.width(8.dp))
                    }
                    if (route.betaVideoUrl != null) {
                        Text(text = "▶ Beta", color = ClimbPalette.chalk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "›", color = ClimbPalette.textMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
