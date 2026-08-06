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
import androidx.compose.ui.geometry.lerp
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
        val handHold = lerp(holds[lower], holds[upper], t)

        // The heel hooks whichever hold sits one below the hands. On the very first hold there
        // is nothing below to hook, so the leg just hangs.
        val heelHold = if (lower >= 1) {
            lerp(holds[lower - 1], holds[(upper - 1).coerceAtLeast(0)], t)
        } else {
            null
        }

        drawClimber(handHold = handHold, heelHold = heelHold, reach = reach)
    }
}

private fun holdCenter(index: Int, stepCount: Int, width: Float, height: Float): Offset {
    val fraction = (index + 0.5f) / stepCount
    // Alternate sides so the route zigzags like a real problem instead of a straight ladder,
    // but keep the offset small enough that the climber can span two holds believably.
    val side = if (index % 2 == 0) -1f else 1f
    return Offset(
        x = width / 2f + side * width * 0.14f,
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
 * Stick figure matched on [handHold] with both hands, heel-hooking [heelHold] one hold below.
 * [reach] drives a 0..1 cycle that bobs the body as it pulls in against the hook — both hands
 * are committed, so the motion comes from the core rather than a free arm waving.
 */
private fun DrawScope.drawClimber(handHold: Offset, heelHold: Offset?, reach: Float) {
    val stroke = 1.6.dp.toPx()
    val color = ClimbPalette.textPrimary
    fun px(dp: Float) = dp.dp.toPx()

    // Matched hands either side of the hold's centre.
    val leftHand = Offset(handHold.x - px(7f), handHold.y + px(1f))
    val rightHand = Offset(handHold.x + px(7f), handHold.y + px(1f))

    // Pulling in against the hook lifts the body slightly. The shoulder hangs far enough below
    // the hold that the arms read as two lines and the head clears the hold itself.
    val pull = reach * px(3f)
    val shoulder = Offset(handHold.x, handHold.y + px(21f) - pull)
    val hip = Offset(handHold.x + px(1f), shoulder.y + px(14f))
    val headCenter = Offset(handHold.x, shoulder.y - px(5.5f))

    drawLine(color, shoulder, leftHand, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, shoulder, rightHand, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, shoulder, hip, strokeWidth = stroke, cap = StrokeCap.Round)

    if (heelHold != null) {
        // Knee bows out toward the hooked hold so the bent leg reads as a hook rather than a
        // straight stand-up.
        val outward = if (heelHold.x < hip.x) -1f else 1f
        val knee = Offset(
            x = (hip.x + heelHold.x) / 2f + outward * px(8f),
            y = (hip.y + heelHold.y) / 2f + px(2f),
        )
        drawLine(color, hip, knee, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, knee, heelHold, strokeWidth = stroke, cap = StrokeCap.Round)
        // Toe kicks back up over the hold — the detail that makes it a heel hook.
        val toe = Offset(heelHold.x + outward * px(5f), heelHold.y - px(6f))
        drawLine(color, heelHold, toe, strokeWidth = stroke, cap = StrokeCap.Round)
    }

    // Trailing leg hangs off the wall, swinging a little with the pull.
    val trailingSide = if (heelHold != null && heelHold.x < hip.x) 1f else -1f
    val trailingKnee = Offset(hip.x + trailingSide * px(7f), hip.y + px(9f))
    val trailingFoot = Offset(
        x = trailingKnee.x + trailingSide * px(3f) + reach * px(2f),
        y = trailingKnee.y + px(9f),
    )
    drawLine(color, hip, trailingKnee, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, trailingKnee, trailingFoot, strokeWidth = stroke, cap = StrokeCap.Round)

    // Head goes on last and filled, so the arm and torso lines converging behind it are hidden
    // rather than crossing through an outlined circle and reading as a scribble.
    drawCircle(color = color, radius = px(3.4f), center = headCenter)
}
