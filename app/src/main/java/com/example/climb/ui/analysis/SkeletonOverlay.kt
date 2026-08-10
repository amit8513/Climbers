package com.example.climb.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType
import com.example.climb.ui.theme.ClimbPalette

/** None hides any overlay entirely; Skeleton draws the current frame's full body rig;
 * BodyPartTracking draws only a short, fading hand/foot "rig" trail. */
enum class OverlayMode { NONE, SKELETON, BODY_PART_TRACKING }

private const val LOW_CONFIDENCE_THRESHOLD = 0.5f

private val BONES = listOf(
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.LEFT_ELBOW,
    PoseLandmarkType.LEFT_ELBOW to PoseLandmarkType.LEFT_WRIST,
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.RIGHT_ELBOW to PoseLandmarkType.RIGHT_WRIST,
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.LEFT_HIP,
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_HIP to PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_HIP to PoseLandmarkType.LEFT_KNEE,
    PoseLandmarkType.LEFT_KNEE to PoseLandmarkType.LEFT_ANKLE,
    PoseLandmarkType.RIGHT_HIP to PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.RIGHT_KNEE to PoseLandmarkType.RIGHT_ANKLE,
    PoseLandmarkType.LEFT_ANKLE to PoseLandmarkType.LEFT_HEEL,
    PoseLandmarkType.LEFT_HEEL to PoseLandmarkType.LEFT_FOOT_INDEX,
    PoseLandmarkType.LEFT_ANKLE to PoseLandmarkType.LEFT_FOOT_INDEX,
    PoseLandmarkType.RIGHT_ANKLE to PoseLandmarkType.RIGHT_HEEL,
    PoseLandmarkType.RIGHT_HEEL to PoseLandmarkType.RIGHT_FOOT_INDEX,
    PoseLandmarkType.RIGHT_ANKLE to PoseLandmarkType.RIGHT_FOOT_INDEX,
)

/**
 * Draws the nearest [PoseFrame] to the current playback position, scaled to whatever size this
 * is placed at — callers must size it to exactly match the displayed video area (not the raw
 * source resolution) so normalized landmark coordinates line up with what's on screen.
 * Low-presence landmarks/bones fade out rather than disappearing outright, so an occluded limb
 * reads as "uncertain" instead of silently vanishing.
 */
@Composable
fun SkeletonOverlay(frame: PoseFrame?, modifier: Modifier = Modifier, highlightedLandmarks: Set<PoseLandmarkType> = emptySet()) {
    if (frame == null || frame.landmarks.isEmpty()) return

    // Captured here because the Canvas draw lambda isn't a composable scope.
    val boneColor = ClimbPalette.chalk
    val lowConfidenceColor = ClimbPalette.fell
    val confidentColor = ClimbPalette.sent

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 3.dp.toPx()
        val pointRadius = 5.dp.toPx()
        val highlightRadius = 11.dp.toPx()
        val highlightStrokeWidth = 2.dp.toPx()

        for ((a, b) in BONES) {
            val la = frame.landmark(a)
            val lb = frame.landmark(b)
            if (la != null && lb != null) {
                val alpha = ((la.presence + lb.presence) / 2f).coerceIn(0.15f, 1f)
                drawLine(
                    color = boneColor.copy(alpha = alpha),
                    start = Offset(la.normalizedX * w, la.normalizedY * h),
                    end = Offset(lb.normalizedX * w, lb.normalizedY * h),
                    strokeWidth = strokeWidth,
                )
            }
        }

        for (landmark in frame.landmarks) {
            val color = if (landmark.presence < LOW_CONFIDENCE_THRESHOLD) lowConfidenceColor else confidentColor
            drawCircle(
                color = color.copy(alpha = landmark.presence.coerceIn(0.25f, 1f)),
                radius = pointRadius,
                center = Offset(landmark.normalizedX * w, landmark.normalizedY * h),
            )
        }

        for (landmark in frame.landmarks) {
            if (landmark.type !in highlightedLandmarks) continue
            drawCircle(
                color = ClimbPalette.gold,
                radius = highlightRadius,
                center = Offset(landmark.normalizedX * w, landmark.normalizedY * h),
                style = Stroke(width = highlightStrokeWidth),
            )
        }
    }
}

private data class TrackedLimb(val type: PoseLandmarkType, val color: Color, val label: String)

private val TRACKED_LIMBS = listOf(
    TrackedLimb(PoseLandmarkType.LEFT_WRIST, ClimbPalette.gold, "Left hand"),
    TrackedLimb(PoseLandmarkType.RIGHT_WRIST, ClimbPalette.silver, "Right hand"),
    TrackedLimb(PoseLandmarkType.LEFT_FOOT_INDEX, ClimbPalette.sent, "Left foot"),
    TrackedLimb(PoseLandmarkType.RIGHT_FOOT_INDEX, ClimbPalette.fell, "Right foot"),
)

/**
 * Draws a short "rig" trail for each hand and foot — only the last [fadeWindowMs] of movement
 * relative to [currentPositionMs], fading from full opacity at the current moment down to
 * nothing at the edge of the window, rather than the whole climb's accumulated path. A full-climb
 * trail turns into unreadable crossed-over scribble within seconds on any real climb; a short
 * fading tail still shows *how* the limb is moving right now without the clutter. Only reliable
 * frames contribute a point, and a gap larger than [maxGapMs] between two reliable points breaks
 * the trail instead of drawing a straight line across an occlusion or tracking dropout.
 */
@Composable
fun BodyPartTrackingOverlay(
    frames: List<PoseFrame>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
    maxGapMs: Long = 700L,
    fadeWindowMs: Long = 2_000L,
    highlightedLandmarks: Set<PoseLandmarkType> = emptySet(),
) {
    if (frames.isEmpty()) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        for (limb in TRACKED_LIMBS) {
            val isHighlighted = limb.type in highlightedLandmarks
            val strokeWidth = (if (isHighlighted) 4f else 2.5f).dp.toPx()
            val headRadius = (if (isHighlighted) 8f else 6f).dp.toPx()
            val baseAlpha = if (isHighlighted) 1f else 0.85f

            var previousTimestampMs: Long? = null
            var lastPoint: Offset? = null
            for (frame in frames) {
                if (frame.timestampMs > currentPositionMs) break
                if (!frame.isReliable) {
                    previousTimestampMs = null
                    lastPoint = null
                    continue
                }
                val landmark = frame.landmark(limb.type) ?: continue
                val point = Offset(landmark.normalizedX * w, landmark.normalizedY * h)
                val gapTooLarge = previousTimestampMs != null && frame.timestampMs - previousTimestampMs!! > maxGapMs
                val ageMs = currentPositionMs - frame.timestampMs
                if (lastPoint != null && !gapTooLarge && ageMs <= fadeWindowMs) {
                    val fadeAlpha = (1f - ageMs.toFloat() / fadeWindowMs).coerceIn(0f, 1f)
                    drawLine(color = limb.color.copy(alpha = baseAlpha * fadeAlpha), start = lastPoint, end = point, strokeWidth = strokeWidth)
                }
                lastPoint = point
                previousTimestampMs = frame.timestampMs
            }
            lastPoint?.let { drawCircle(color = limb.color.copy(alpha = baseAlpha), radius = headRadius, center = it) }
        }
    }
}
