package com.example.climb.analysis.metrics

import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

data class Point2D(val x: Float, val y: Float)

fun distance(a: Point2D, b: Point2D): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun PoseFrame.midpoint(a: PoseLandmarkType, b: PoseLandmarkType): Point2D? {
    val la = landmark(a) ?: return null
    val lb = landmark(b) ?: return null
    return Point2D((la.normalizedX + lb.normalizedX) / 2f, (la.normalizedY + lb.normalizedY) / 2f)
}

fun PoseFrame.hipCenter(): Point2D? = midpoint(PoseLandmarkType.LEFT_HIP, PoseLandmarkType.RIGHT_HIP)

fun PoseFrame.shoulderCenter(): Point2D? = midpoint(PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER)

fun PoseFrame.ankleCenter(): Point2D? = midpoint(PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE)

fun PoseFrame.shoulderWidth(): Float? {
    val l = landmark(PoseLandmarkType.LEFT_SHOULDER) ?: return null
    val r = landmark(PoseLandmarkType.RIGHT_SHOULDER) ?: return null
    return distance(Point2D(l.normalizedX, l.normalizedY), Point2D(r.normalizedX, r.normalizedY))
}

/** Shoulder-center-to-ankle-center distance as a rough, cheap body-height proxy in normalized
 * frame units — not an anatomical measurement, just enough to normalize movement distances so
 * a climber close to camera and one far away produce comparable numbers. */
fun PoseFrame.bodyHeightEstimate(): Float? {
    val shoulders = shoulderCenter() ?: return null
    val ankles = ankleCenter() ?: return null
    return distance(shoulders, ankles).takeIf { it > 0.01f }
}

/** Angle at [elbow] between the shoulder→elbow and wrist→elbow vectors, in degrees.
 * 180° is a fully straight arm, smaller angles are more bent. */
fun elbowAngleDegrees(shoulder: PoseLandmark, elbow: PoseLandmark, wrist: PoseLandmark): Float {
    val v1x = shoulder.normalizedX - elbow.normalizedX
    val v1y = shoulder.normalizedY - elbow.normalizedY
    val v2x = wrist.normalizedX - elbow.normalizedX
    val v2y = wrist.normalizedY - elbow.normalizedY
    val dot = v1x * v2x + v1y * v2y
    val mag1 = sqrt(v1x * v1x + v1y * v1y)
    val mag2 = sqrt(v2x * v2x + v2y * v2y)
    if (mag1 < 1e-4f || mag2 < 1e-4f) return 180f
    val cos = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cos.toDouble())).toFloat()
}

/** Angle at [knee] between the hip→knee and ankle→knee vectors, in degrees — same geometry as
 * [elbowAngleDegrees], named separately for readability at leg call sites. 180° is a fully
 * straight leg, smaller angles are more bent. */
fun kneeAngleDegrees(hip: PoseLandmark, knee: PoseLandmark, ankle: PoseLandmark): Float =
    elbowAngleDegrees(hip, knee, ankle)

/** Angle of the line between two landmarks against horizontal, in degrees — used for
 * shoulder-line / hip-line tilt indicators. 0° is level. */
fun lineAngleDegrees(a: PoseLandmark, b: PoseLandmark): Float =
    Math.toDegrees(atan2((b.normalizedY - a.normalizedY).toDouble(), (b.normalizedX - a.normalizedX).toDouble())).toFloat()
