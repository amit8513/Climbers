package com.example.climb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.RouteColor
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.darkAccent

/** Asymmetric rounding so the badge reads as a climbing hold rather than a plain chip. */
private val holdShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 20.dp,
    bottomEnd = 18.dp,
    bottomStart = 14.dp,
)

@Composable
fun HoldBadge(
    grade: Int?,
    routeColor: RouteColor,
    modifier: Modifier = Modifier,
    width: Int = 46,
    height: Int = 44,
    fontSize: Int = 15,
) {
    val accent = routeColor.darkAccent()
    Box(
        modifier = modifier
            .size(width = width.dp, height = height.dp)
            .clip(holdShape)
            .background(Color(routeColor.hex))
            .background(Brush.radialGradient(colors = listOf(ClimbPalette.holdSheen, Color.Transparent)))
            .border(1.dp, accent, holdShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = grade?.let { "V$it" } ?: "?",
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = fontSize.sp,
        )
    }
}

@Composable
fun OutcomePill(outcome: ClimbOutcome, modifier: Modifier = Modifier) {
    val color = when (outcome) {
        ClimbOutcome.SENT -> ClimbPalette.sent
        ClimbOutcome.FELL -> ClimbPalette.fell
        ClimbOutcome.PROJECT -> ClimbPalette.project
    }
    Text(
        text = outcome.name,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Bordered surface card with a small uppercase section label, as used on Progress and Detail. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ClimbPalette.surface)
            .border(1.dp, ClimbPalette.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = ClimbPalette.textMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}
