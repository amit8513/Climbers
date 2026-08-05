package com.example.climb.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmarkType
import com.example.climb.ui.theme.ClimbPalette

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
fun SkeletonOverlay(frame: PoseFrame?, modifier: Modifier = Modifier) {
    if (frame == null || frame.landmarks.isEmpty()) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 3.dp.toPx()
        val pointRadius = 5.dp.toPx()

        for ((a, b) in BONES) {
            val la = frame.landmark(a)
            val lb = frame.landmark(b)
            if (la != null && lb != null) {
                val alpha = ((la.presence + lb.presence) / 2f).coerceIn(0.15f, 1f)
                drawLine(
                    color = ClimbPalette.chalk.copy(alpha = alpha),
                    start = Offset(la.normalizedX * w, la.normalizedY * h),
                    end = Offset(lb.normalizedX * w, lb.normalizedY * h),
                    strokeWidth = strokeWidth,
                )
            }
        }

        for (landmark in frame.landmarks) {
            val color = if (landmark.presence < LOW_CONFIDENCE_THRESHOLD) ClimbPalette.fell else ClimbPalette.sent
            drawCircle(
                color = color.copy(alpha = landmark.presence.coerceIn(0.25f, 1f)),
                radius = pointRadius,
                center = Offset(landmark.normalizedX * w, landmark.normalizedY * h),
            )
        }
    }
}
