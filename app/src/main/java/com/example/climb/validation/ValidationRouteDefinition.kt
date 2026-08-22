package com.example.climb.validation

import com.example.climb.attribution.RouteCandidate
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.StartPolicy
import com.example.climb.colordetection.NormalizedRect
import org.json.JSONArray
import org.json.JSONObject

/**
 * One manually-defined CANDIDATE route for the debug harness (Phase 4B) — the developer typically
 * defines 2+ competing [ValidationRouteDefinition]s per wall setup so `RouteAttributionEngine`
 * (Phase 4A, `:shared-domain`) has something real to discriminate between, exactly like
 * `RouteAttributionEngineTest`'s own multi-candidate fixtures. Deliberately a local, debug-only
 * shape rather than a reuse of `RouteVisionProfileEntity`/`RouteCandidate` directly — this package
 * never persists to any backend, and [toRouteCandidate] is the one place this local shape is
 * projected into Phase 4A's real scoring input.
 */
data class ValidationRouteDefinition(
    val routeId: Long,
    val name: String,
    val startHoldIds: Set<Int>,
    val startPolicy: StartPolicy,
    val bodyHoldIds: Set<Int> = emptySet(),
    val finishHoldIds: Set<Int> = emptySet(),
    val finishPolicy: FinishPolicy? = null,
    val corridorNormalized: NormalizedRect? = null,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(startHoldIds.isNotEmpty()) { "a route definition must have at least one start hold" }
        require((finishHoldIds.isEmpty()) == (finishPolicy == null)) {
            "finishHoldIds and finishPolicy must both be present or both be absent"
        }
    }
}

/** Projects this debug-only definition into Phase 4A's real [RouteCandidate] scoring input — the
 * one conversion point between this package's local shape and `:shared-domain`'s attribution
 * engine contract. */
fun ValidationRouteDefinition.toRouteCandidate(): RouteCandidate = RouteCandidate(
    routeVersionId = routeId,
    startHoldIds = startHoldIds,
    startPolicy = startPolicy,
    bodyHoldIds = bodyHoldIds,
    finishHoldIds = finishHoldIds,
    finishPolicy = finishPolicy,
    corridorNormalized = corridorNormalized,
)

fun ValidationRouteDefinition.toJsonObject(): JSONObject = JSONObject().apply {
    put("routeId", routeId)
    put("name", name)
    put("startHoldIds", JSONArray(startHoldIds.sorted()))
    put("startPolicy", startPolicy.name)
    put("bodyHoldIds", JSONArray(bodyHoldIds.sorted()))
    put("finishHoldIds", JSONArray(finishHoldIds.sorted()))
    put("finishPolicy", finishPolicy?.name ?: JSONObject.NULL)
    put("corridorNormalized", corridorNormalized?.toJsonObject() ?: JSONObject.NULL)
}

fun JSONObject.toValidationRouteDefinition(): ValidationRouteDefinition {
    val startHoldIds = getJSONArray("startHoldIds").let { array -> (0 until array.length()).map { array.getInt(it) } }.toSet()
    val bodyHoldIds = getJSONArray("bodyHoldIds").let { array -> (0 until array.length()).map { array.getInt(it) } }.toSet()
    val finishHoldIds = getJSONArray("finishHoldIds").let { array -> (0 until array.length()).map { array.getInt(it) } }.toSet()
    val finishPolicyValue = opt("finishPolicy")?.takeIf { it != JSONObject.NULL } as? String
    val corridorValue = opt("corridorNormalized")?.takeIf { it != JSONObject.NULL } as? JSONObject

    return ValidationRouteDefinition(
        routeId = getLong("routeId"),
        name = getString("name"),
        startHoldIds = startHoldIds,
        startPolicy = StartPolicy.valueOf(getString("startPolicy")),
        bodyHoldIds = bodyHoldIds,
        finishHoldIds = finishHoldIds,
        finishPolicy = finishPolicyValue?.let { FinishPolicy.valueOf(it) },
        corridorNormalized = corridorValue?.toNormalizedRect(),
    )
}

private fun NormalizedRect.toJsonObject(): JSONObject = JSONObject().apply {
    put("left", left.toDouble())
    put("top", top.toDouble())
    put("right", right.toDouble())
    put("bottom", bottom.toDouble())
}

private fun JSONObject.toNormalizedRect(): NormalizedRect = NormalizedRect(
    left = getDouble("left").toFloat(),
    top = getDouble("top").toFloat(),
    right = getDouble("right").toFloat(),
    bottom = getDouble("bottom").toFloat(),
)
