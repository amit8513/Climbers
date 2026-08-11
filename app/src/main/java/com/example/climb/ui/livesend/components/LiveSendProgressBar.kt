package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

/**
 * A track+fill progress bar — Progress screen's "Send Rate by Grade" rows (Track1/Fill1,
 * Track2/Fill2, one per grade). [progress] is 0f..1f; [fillColor] defaults to the fixed
 * [ClimbPalette.liveSendAccent] the spec uses for every fill regardless of the grade's own color.
 */
@Composable
fun LiveSendProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = ClimbPalette.liveSendSurfaceRaised,
    fillColor: Color = ClimbPalette.liveSendAccent,
    height: Int = 10,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(shape)
                .background(fillColor),
        )
    }
}

/**
 * A single bar in the "Hardest Send by Week" bar chart on Progress — 5 of these side by side.
 * Exposed as a single bar rather than a full chart composable so callers control bar spacing and
 * can label individual bars (week numbers) as needed; [heightFraction] is 0f..1f of [maxHeight].
 */
@Composable
fun LiveSendChartBar(
    heightFraction: Float,
    modifier: Modifier = Modifier,
    width: Int = 30,
    maxHeight: Int = 90,
    color: Color = ClimbPalette.liveSendAccent,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height((maxHeight * heightFraction.coerceIn(0f, 1f)).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}
