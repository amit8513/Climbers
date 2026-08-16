package com.example.climb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun OutcomePill(outcome: ClimbOutcome, modifier: Modifier = Modifier, backgroundColor: Color? = null) {
    val color = when (outcome) {
        ClimbOutcome.SENT -> ClimbPalette.sent
        ClimbOutcome.FELL -> ClimbPalette.fell
        ClimbOutcome.PROJECT -> ClimbPalette.project
    }
    val shape = RoundedCornerShape(50)
    Text(
        text = outcome.name,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .then(if (backgroundColor != null) Modifier.background(backgroundColor, shape) else Modifier)
            .border(1.dp, color, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Centered placeholder for a screen/section with no content yet — e.g. "No clubs to join yet." */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = ClimbPalette.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Centered placeholder for a failed load, with an optional retry action. */
@Composable
fun ErrorState(
    message: String = "Something went wrong.",
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = ClimbPalette.fell,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Try again", color = ClimbPalette.chalk, fontWeight = FontWeight.Bold) }
        }
    }
}

/** A bordered surface block standing in for content that's still loading — same shape language as
 * [SectionCard], so a loading list doesn't jump around once real content replaces it. */
@Composable
fun LoadingCard(modifier: Modifier = Modifier, height: Int = 72) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClimbPalette.surface)
            .border(1.dp, ClimbPalette.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ClimbPalette.textMuted)
    }
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
