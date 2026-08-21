package com.example.climb.colordetection

import com.example.climb.clubs.HoldRole
import org.json.JSONArray
import org.json.JSONObject

/**
 * One hold as staff reviewed/corrected it during route registration (Phase 2A) — deliberately
 * much lighter than [DetectedHold] (no pixel mask/contour/confidence fields): only stable
 * identity, a normalized position, and a [HoldRole] are what survive into
 * `RouteVisionProfileEntity.holdGeometryJson`. Real per-pixel hold detection stays
 * [HoldComponentDetector]'s job; this type is the reviewed/corrected *output* of that process (or,
 * in Phase 2A's hardware-independent flow, of a canned fixture standing in for it).
 */
data class ReviewedHold(
    val id: Int,
    val centroidNormalized: Point2D,
    val role: HoldRole,
)

/** Serializes a confirmed hold list into [RouteVisionProfileEntity.holdGeometryJson]'s storage
 * shape. Uses `org.json` (see `TargetColorModelJson.kt` for the same established pattern in this
 * package) rather than any other format. */
fun List<ReviewedHold>.toHoldGeometryJson(): String {
    val array = JSONArray()
    forEach { hold ->
        array.put(
            JSONObject().apply {
                put("id", hold.id)
                put("centroidX", hold.centroidNormalized.x.toDouble())
                put("centroidY", hold.centroidNormalized.y.toDouble())
                put("role", hold.role.name)
            },
        )
    }
    return array.toString()
}

/** A corrupt/shape-mismatched stored value falls back to an empty list rather than crashing —
 * same honesty-on-failure convention as `String.toTargetColorModel()`. */
fun String.toReviewedHolds(): List<ReviewedHold> {
    if (isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(this)
        (0 until array.length()).map { index ->
            val holdObject = array.getJSONObject(index)
            ReviewedHold(
                id = holdObject.getInt("id"),
                centroidNormalized = Point2D(
                    x = holdObject.getDouble("centroidX").toFloat(),
                    y = holdObject.getDouble("centroidY").toFloat(),
                ),
                role = HoldRole.valueOf(holdObject.getString("role")),
            )
        }
    }.getOrDefault(emptyList())
}
