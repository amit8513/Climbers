package com.example.climb.analysis.contact

import com.example.climb.colordetection.Point2D
import kotlin.math.sqrt

/** One hold's normalized polygon contour, in the exact `WallReferenceSpace` coordinate space a
 * `WallCalibrationEntity`'s reference frame defines — never raw capture-frame pixels. [holdId]
 * matches `RouteVisionProfileEntity`'s stable, staff-confirmation-time-assigned hold identity
 * (plan doc §11) — this type doesn't itself assign ids, just carries whichever one it was given. */
data class HoldShape(val holdId: Int, val contourNormalized: List<Point2D>) {
    init {
        require(contourNormalized.size >= 3) { "a hold contour needs at least 3 vertices, got ${contourNormalized.size}" }
    }
}

fun Point2D.distanceTo(other: Point2D): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

/**
 * Pure contact-geometry math: a limb proxy point [inside a hold's mask has distance zero; outside
 * it, distance to the nearest point on the hold's boundary/contour] — per
 * docs/ROUTE_ATTRIBUTION_PLAN.md's corrected §7 rule (a limb pressed into the middle of a hold
 * must never report a nonzero "distance to contour" the way a naive nearest-vertex/edge-only
 * check would). All inputs/outputs are `WallReferenceSpace`-normalized coordinates.
 */
object HoldGeometryMath {

    /** Standard ray-casting point-in-polygon test. */
    fun isInsideHold(point: Point2D, hold: HoldShape): Boolean {
        val vertices = hold.contourNormalized
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val vi = vertices[i]
            val vj = vertices[j]
            if ((vi.y > point.y) != (vj.y > point.y)) {
                val edgeXAtPointY = vj.x + (point.y - vj.y) / (vi.y - vj.y) * (vi.x - vj.x)
                if (point.x < edgeXAtPointY) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Zero when [point] is inside [hold]'s mask; otherwise the distance to the nearest point on
     * [hold]'s boundary. */
    fun distanceToHold(point: Point2D, hold: HoldShape): Float {
        if (isInsideHold(point, hold)) return 0f
        return distanceToPolygonBoundary(point, hold.contourNormalized)
    }

    private fun distanceToPolygonBoundary(point: Point2D, vertices: List<Point2D>): Float {
        var minDistance = Float.MAX_VALUE
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            val distance = distancePointToSegment(point, a, b)
            if (distance < minDistance) minDistance = distance
        }
        return minDistance
    }

    private fun distancePointToSegment(point: Point2D, a: Point2D, b: Point2D): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSquared = abx * abx + aby * aby
        val t = if (lengthSquared > 0f) {
            (((point.x - a.x) * abx + (point.y - a.y) * aby) / lengthSquared).coerceIn(0f, 1f)
        } else {
            0f
        }
        val closest = Point2D(a.x + t * abx, a.y + t * aby)
        return point.distanceTo(closest)
    }
}
