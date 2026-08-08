package com.example.climb.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.analysis.scoring.CategoryScore
import com.example.climb.ui.theme.ClimbPalette
import kotlin.math.cos
import kotlin.math.sin

private const val RING_COUNT = 4

/**
 * A small, dependency-free hexagon radar chart for the six [CategoryScore] categories — built as
 * a plain Compose [Canvas] rather than pulling in a charting library for one chart shape.
 * [overallScore], when provided, is drawn large in the center (used by the fullscreen view;
 * the inline card omits it since the number is already shown next to the chart there).
 */
@Composable
fun PerformanceRadarChart(
    categoryScores: List<CategoryScore>,
    modifier: Modifier = Modifier,
    overallScore: Int? = null,
    labelTextSizeSp: Float = 11f,
) {
    Canvas(modifier = modifier) {
        drawRadarChart(categoryScores, overallScore, labelTextSizeSp)
    }
}

private fun DrawScope.drawRadarChart(categoryScores: List<CategoryScore>, overallScore: Int?, labelTextSizeSp: Float) {
    val axisCount = categoryScores.size
    if (axisCount < 3) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val labelPadding = 36.dp.toPx()
    val maxRadius = (minOf(size.width, size.height) / 2f - labelPadding).coerceAtLeast(1f)
    val angleStep = (2 * Math.PI / axisCount).toFloat()

    fun pointAt(index: Int, radius: Float): Offset {
        val angle = -(Math.PI / 2f).toFloat() + index * angleStep
        return Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
    }

    for (ring in 1..RING_COUNT) {
        val ringRadius = maxRadius * ring / RING_COUNT
        val ringPath = Path()
        for (i in 0 until axisCount) {
            val point = pointAt(i, ringRadius)
            if (i == 0) ringPath.moveTo(point.x, point.y) else ringPath.lineTo(point.x, point.y)
        }
        ringPath.close()
        drawPath(ringPath, color = ClimbPalette.border, style = Stroke(width = 1.dp.toPx()))
    }

    for (i in 0 until axisCount) {
        drawLine(ClimbPalette.border, center, pointAt(i, maxRadius), strokeWidth = 1.dp.toPx())
    }

    val scorePath = Path()
    for (i in categoryScores.indices) {
        val point = pointAt(i, maxRadius * categoryScores[i].score.coerceIn(0, 100) / 100f)
        if (i == 0) scorePath.moveTo(point.x, point.y) else scorePath.lineTo(point.x, point.y)
    }
    scorePath.close()
    drawPath(scorePath, color = ClimbPalette.chalk.copy(alpha = 0.22f))
    drawPath(scorePath, color = ClimbPalette.chalk, style = Stroke(width = 2.dp.toPx()))

    for (i in categoryScores.indices) {
        val point = pointAt(i, maxRadius * categoryScores[i].score.coerceIn(0, 100) / 100f)
        drawCircle(ClimbPalette.chalk, radius = 3.dp.toPx(), center = point)
    }

    drawIntoCanvas { canvas ->
        val labelPaint = android.graphics.Paint().apply {
            color = ClimbPalette.textSecondary.toArgb()
            textSize = labelTextSizeSp.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in categoryScores.indices) {
            val labelPoint = pointAt(i, maxRadius + labelPadding * 0.62f)
            canvas.nativeCanvas.drawText(
                categoryScores[i].category.displayName,
                labelPoint.x,
                labelPoint.y - (labelPaint.descent() + labelPaint.ascent()) / 2f,
                labelPaint,
            )
        }

        if (overallScore != null) {
            val scorePaint = android.graphics.Paint().apply {
                color = ClimbPalette.chalk.toArgb()
                textSize = 30.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            canvas.nativeCanvas.drawText(
                overallScore.toString(),
                center.x,
                center.y - (scorePaint.descent() + scorePaint.ascent()) / 2f,
                scorePaint,
            )
        }
    }
}
