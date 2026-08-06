package com.example.climb.ui.analysis

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

/**
 * A stick climber working its way up a line of holds — one hold per analysis phase. The climber
 * moves up a hold each time a phase completes, so progress reads as height gained on a boulder
 * rather than a bar filling up.
 */
@Composable
fun ClimbingProgressIndicator(
    stepCount: Int,
    completedSteps: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val climberHeight by animateFloatAsState(
        targetValue = completedSteps.coerceIn(0, stepCount).toFloat(),
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "climberHeight",
    )

    // Slow reach cycle so the climber looks like it's working the current move rather than
    // frozen between holds. Held at rest when nothing is running.
    val reach = if (active) {
        val transition = rememberInfiniteTransition(label = "reach")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "reachPhase",
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val holds = List(stepCount) { holdCenter(it, stepCount, size.width, size.height) }

        drawRouteLine(holds)

        holds.forEachIndexed { index, center ->
            val reached = index < completedSteps
            val isCurrent = index == completedSteps
            drawHold(center = center, reached = reached, isCurrent = isCurrent)
        }

        // Interpolate between the hold just cleared and the one being worked on.
        val lower = climberHeight.toInt().coerceIn(0, stepCount - 1)
        val upper = (lower + 1).coerceAtMost(stepCount - 1)
        val t = (climberHeight - lower).coerceIn(0f, 1f)
        val anchor = Offset(
            x = holds[lower].x + (holds[upper].x - holds[lower].x) * t,
            y = holds[lower].y + (holds[upper].y - holds[lower].y) * t,
        )

        drawClimber(anchor = anchor, reach = reach)
    }
}

private fun holdCenter(index: Int, stepCount: Int, width: Float, height: Float): Offset {
    val fraction = (index + 0.5f) / stepCount
    // Alternate sides so the route zigzags like a real problem instead of a straight ladder.
    val side = if (index % 2 == 0) -1f else 1f
    return Offset(
        x = width / 2f + side * width * 0.2f,
        y = height * (1f - fraction),
    )
}

private fun DrawScope.drawRouteLine(holds: List<Offset>) {
    if (holds.size < 2) return
    val path = Path().apply {
        moveTo(holds.first().x, holds.first().y)
        holds.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = ClimbPalette.border,
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun DrawScope.drawHold(center: Offset, reached: Boolean, isCurrent: Boolean) {
    val radius = 5.dp.toPx()
    when {
        reached -> drawCircle(color = ClimbPalette.sent, radius = radius, center = center)
        isCurrent -> drawCircle(
            color = ClimbPalette.chalk,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        else -> drawCircle(
            color = ClimbPalette.borderStrong,
            radius = radius * 0.75f,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/**
 * Stick figure hanging off [anchor], which is the hold its leading hand is on. [reach] drives a
 * 0..1 cycle where the free hand stretches for the next hold and the hips shift with it.
 */
private fun DrawScope.drawClimber(anchor: Offset, reach: Float) {
    val stroke = 1.6.dp.toPx()
    val color = ClimbPalette.textPrimary

    // The body hangs below and slightly right of the leading hand.
    val body = Offset(anchor.x + 7.dp.toPx(), anchor.y + 15.dp.toPx())
    val shoulder = Offset(body.x, body.y - 5.dp.toPx())
    val hip = Offset(body.x, body.y + 8.dp.toPx())
    val headCenter = Offset(body.x, body.y - 11.dp.toPx())

    drawCircle(color = color, radius = 3.6.dp.toPx(), center = headCenter, style = Stroke(width = stroke))
    drawLine(color, shoulder, hip, strokeWidth = stroke, cap = StrokeCap.Round)

    // Leading arm stays on the hold; the free arm reaches up and back down.
    drawLine(color, shoulder, anchor, strokeWidth = stroke, cap = StrokeCap.Round)
    val freeHand = Offset(
        x = body.x + 11.dp.toPx(),
        y = body.y - (6.dp.toPx() + reach * 9.dp.toPx()),
    )
    drawLine(color, shoulder, freeHand, strokeWidth = stroke, cap = StrokeCap.Round)

    // Legs push off, the trailing one swinging a little with the reach.
    val leadFoot = Offset(body.x - 7.dp.toPx(), hip.y + 10.dp.toPx())
    val trailFoot = Offset(
        x = body.x + 8.dp.toPx() - reach * 2.dp.toPx(),
        y = hip.y + 8.dp.toPx() + reach * 2.dp.toPx(),
    )
    drawLine(color, hip, leadFoot, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, hip, trailFoot, strokeWidth = stroke, cap = StrokeCap.Round)
}
