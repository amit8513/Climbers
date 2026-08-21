package com.example.climb.validation

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.EvidenceQuality
import com.example.climb.analysis.contact.HoldContactEvent
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.contact.ReleaseReason
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-debug persistence for a [HoldContactTimeline] — the file the plan doc's original file
 * list anticipated (`HoldContactTimelineJson.kt`), implemented now that Phase 3B's local
 * validation dataset actually needs to save/reload results across app runs. Field order is fixed
 * (declared once, below) and events are serialized in their original list order — the same
 * [HoldContactTimeline] always produces byte-identical JSON, and round-trips back to an equal
 * value. Same `org.json` pattern as `TargetColorModelJson.kt`/`HoldGeometryJson.kt`.
 */
fun HoldContactTimeline.toJson(): String {
    val array = JSONArray()
    for (event in events) {
        array.put(
            JSONObject().apply {
                put("limb", event.limb.name)
                put("holdId", event.holdId)
                put("type", event.type.name)
                put("timestampMs", event.timestampMs)
                put("confidence", event.confidence.toDouble())
                put("evidenceQuality", event.evidenceQuality.name)
                put("releaseReason", event.releaseReason?.name ?: JSONObject.NULL)
            },
        )
    }
    return array.toString()
}

/** A corrupt/shape-mismatched stored value falls back to an empty timeline rather than crashing —
 * same honesty-on-failure convention as `String.toTargetColorModel()`/`String.toReviewedHolds()`. */
fun String.toHoldContactTimeline(): HoldContactTimeline {
    if (isBlank()) return HoldContactTimeline()
    return runCatching {
        val array = JSONArray(this)
        val events = (0 until array.length()).map { index ->
            val eventObject = array.getJSONObject(index)
            HoldContactEvent(
                limb = Limb.valueOf(eventObject.getString("limb")),
                holdId = eventObject.getInt("holdId"),
                type = ContactEventType.valueOf(eventObject.getString("type")),
                timestampMs = eventObject.getLong("timestampMs"),
                confidence = eventObject.getDouble("confidence").toFloat(),
                evidenceQuality = EvidenceQuality.valueOf(eventObject.getString("evidenceQuality")),
                releaseReason = eventObject.opt("releaseReason")?.takeIf { it != JSONObject.NULL }
                    ?.let { ReleaseReason.valueOf(it as String) },
            )
        }
        HoldContactTimeline(events)
    }.getOrDefault(HoldContactTimeline())
}
