package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * A small filled, fully-rounded text pill. This single shape covers every "colored tag" in the
 * Live Send spec that is just [text-on-a-colored-background] with no other structure: the
 * dashboard's "● Club Mode" mode badge, its "Switch" button (pass [onClick]), the home feed's
 * "🔥 3-day streak" ribbon, and the community screen's "Admin"/"Member" role pills — same shape,
 * different [containerColor]/[contentColor]/[fontSize] per call site.
 */
@Composable
fun LiveSendBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = ClimbPalette.liveSendSurfaceRaised,
    contentColor: Color = ClimbPalette.liveSendTextPrimary,
    fontSize: Int = 11,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text,
        color = contentColor,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * A small colored grade chip (e.g. "V7") — the rounded-rect badge next to route thumbnails and
 * grade rows across HomeFeed, RouteDetail, Explore, and Progress. Unlike [LiveSendBadge] (fully
 * pill-rounded) this uses a rounded-rect corner that defaults to Explore's spec'd 10dp — pass
 * [cornerRadius] for call sites spec'd differently (e.g. Progress's PeakCard badge at 16dp). Also
 * defaults to the fixed [ClimbPalette.liveSendCta] used for the hardest/featured grade — pass a
 * route's own color for a route-specific badge (e.g. the pink/yellow rows in ExploreScreen).
 */
@Composable
fun GradeBadge(
    grade: String,
    modifier: Modifier = Modifier,
    containerColor: Color = ClimbPalette.liveSendCta,
    contentColor: Color = ClimbPalette.liveSendTextPrimary,
    cornerRadius: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = grade, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/**
 * A small solid color dot — the route-color marker in ExploreScreen's route rows and the "live
 * now" indicator next to a venue name on HomeFeed. Purely a colored [CircleShape]; the caller
 * supplies meaning via [color] (a route's own color, or [ClimbPalette.liveSendInfo] for "live").
 */
@Composable
fun LiveIndicatorDot(color: Color, modifier: Modifier = Modifier, size: Int = 10) {
    Box(modifier = modifier.size(size.dp).clip(CircleShape).background(color))
}
