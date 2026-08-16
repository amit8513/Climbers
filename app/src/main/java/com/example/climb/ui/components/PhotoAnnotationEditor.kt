package com.example.climb.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.climb.colordetection.DebugCoordinateMapper
import com.example.climb.ui.theme.ClimbPalette

/** A hand-drawn stroke, captured in the editor's own displayed coordinate space (not the source
 * bitmap's native pixels — [flattenAnnotations] maps to native pixels once, at export time). */
private data class AnnotationStroke(val points: List<Offset>, val tool: AnnotationTool)

enum class AnnotationTool(val label: String, val color: Color, val strokeWidthDp: Float, val alpha: Float) {
    PEN("Pen", Color(0xFFE53935), 6f, 1f),
    ARROW_INK("Marker", Color(0xFF3DA9FC), 8f, 1f),
    HIGHLIGHTER("Highlight", Color(0xFFFFD23D), 26f, 0.45f),
}

/**
 * Full-screen photo markup — circle a hold, draw an arrow, highlight a section of wall, all as
 * freehand strokes (no shape-recognition attempted; the user draws the shape they mean). Wraps
 * its own [Dialog] with [DialogProperties] forced away from the platform defaults for the same
 * reason `CalibrationPickerDialog` needed it: `usePlatformDefaultWidth = true` only converges to
 * final bounds across the first frame(s) after showing, and `dismissOnClickOutside = true` can
 * misclassify an early tap against those not-yet-settled bounds.
 */
@Composable
fun PhotoAnnotationDialog(bitmap: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        PhotoAnnotationEditor(bitmap = bitmap, onCancel = onCancel, onDone = onDone)
    }
}

@Composable
private fun PhotoAnnotationEditor(bitmap: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var displayedSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTool by remember { mutableStateOf(AnnotationTool.PEN) }
    val strokes = remember { mutableStateOf(listOf<AnnotationStroke>()) }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }

    Column(modifier = Modifier.fillMaxSize().background(ClimbPalette.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cancel",
                color = ClimbPalette.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onCancel),
            )
            Text(
                text = "Undo",
                color = if (strokes.value.isNotEmpty()) ClimbPalette.textSecondary else ClimbPalette.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(enabled = strokes.value.isNotEmpty()) {
                    strokes.value = strokes.value.dropLast(1)
                },
            )
            Text(
                text = "Done",
                color = ClimbPalette.chalk,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    onDone(flattenAnnotations(bitmap, strokes.value, displayedSize))
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .onSizeChanged { displayedSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> currentPoints = listOf(offset) },
                        onDrag = { change, _ -> currentPoints = currentPoints + change.position },
                        onDragEnd = {
                            if (currentPoints.size > 1) strokes.value = strokes.value + AnnotationStroke(currentPoints, selectedTool)
                            currentPoints = emptyList()
                        },
                    )
                },
        ) {
            Image(bitmap = imageBitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(modifier = Modifier.fillMaxSize()) {
                strokes.value.forEach { stroke -> drawStroke(stroke.points, stroke.tool) }
                if (currentPoints.size > 1) drawStroke(currentPoints, selectedTool)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnnotationTool.entries.forEach { tool ->
                ToolSwatch(tool = tool, selected = tool == selectedTool, onClick = { selectedTool = tool })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(points: List<Offset>, tool: AnnotationTool) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = tool.color.copy(alpha = tool.alpha),
        style = Stroke(width = tool.strokeWidthDp.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
}

@Composable
private fun ToolSwatch(tool: AnnotationTool, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ClimbPalette.surfaceRaised)
                .then(if (selected) Modifier.padding(2.dp) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (selected) 24.dp else 18.dp)
                    .clip(CircleShape)
                    .background(tool.color.copy(alpha = tool.alpha)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = tool.label, color = if (selected) ClimbPalette.textPrimary else ClimbPalette.textMuted, fontSize = 10.sp)
    }
}

/** Maps every stroke from the editor's displayed coordinate space back to the source bitmap's
 * native pixels (same [DebugCoordinateMapper.unmapPoint] the tap-to-calibrate feature uses for
 * exactly this kind of "point I captured on screen -> point in the real image" conversion), then
 * draws them onto a mutable copy of the original bitmap so what gets uploaded is a single flat
 * image — no separate stroke data to keep in sync with it later. */
private fun flattenAnnotations(source: Bitmap, strokes: List<AnnotationStroke>, displayedSize: IntSize): Bitmap {
    if (strokes.isEmpty() || displayedSize.width <= 0 || displayedSize.height <= 0) return source
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(result)
    val scale = source.width.toFloat() / displayedSize.width.toFloat()

    strokes.forEach { stroke ->
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            color = stroke.tool.color.toArgb(stroke.tool.alpha)
            strokeWidth = stroke.tool.strokeWidthDp * scale
        }
        val nativePoints = stroke.points.map { point ->
            DebugCoordinateMapper.unmapPoint(
                targetX = point.x,
                targetY = point.y,
                sourceWidth = source.width,
                sourceHeight = source.height,
                targetWidth = displayedSize.width.toFloat(),
                targetHeight = displayedSize.height.toFloat(),
            )
        }
        val path = android.graphics.Path().apply {
            moveTo(nativePoints.first().x, nativePoints.first().y)
            nativePoints.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(path, paint)
    }
    return result
}

private fun Color.toArgb(alphaOverride: Float): Int {
    val a = (alphaOverride * 255).toInt().coerceIn(0, 255)
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
