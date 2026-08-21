package com.example.climb.validation

import com.example.climb.analysis.contact.Limb
import com.example.climb.colordetection.Point2D
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where a [ManualValidationSession] (and, once processed, its [ManualValidationReport]) is saved
 * — local-only, deliberately. There is no Firestore collection for any of this, and never will be
 * one for the raw session/report shape itself (see this package's trust-boundary doc comment on
 * [ManualValidationSession]) — [LocalJsonManualValidationSessionStore] is the only implementation,
 * writing plain JSON files under a local directory so a growing dataset of clips survives across
 * app runs without ever touching a backend.
 */
interface ManualValidationSessionStore {
    fun saveSession(session: ManualValidationSession)
    fun loadSessions(): List<ManualValidationSession>
    fun loadSession(validationSessionId: String): ManualValidationSession?
    fun deleteSession(validationSessionId: String)
}

class LocalJsonManualValidationSessionStore(private val directory: File) : ManualValidationSessionStore {

    override fun saveSession(session: ManualValidationSession) {
        if (!directory.exists()) directory.mkdirs()
        File(directory, "${session.validationSessionId}.json").writeText(session.toJson())
    }

    override fun loadSessions(): List<ManualValidationSession> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file -> runCatching { file.readText().toManualValidationSession() }.getOrNull() }
            ?.sortedByDescending { it.createdAtEpochMs }
            ?: emptyList()
    }

    override fun loadSession(validationSessionId: String): ManualValidationSession? {
        val file = File(directory, "$validationSessionId.json")
        if (!file.exists()) return null
        return runCatching { file.readText().toManualValidationSession() }.getOrNull()
    }

    override fun deleteSession(validationSessionId: String) {
        File(directory, "$validationSessionId.json").delete()
    }
}

fun ManualValidationSession.toJson(): String = JSONObject().apply {
    put("validationSessionId", validationSessionId)
    put("referenceImagePath", referenceImagePath)
    put("videoPath", videoPath)
    put("wallOrFixtureId", wallOrFixtureId)
    put("cameraGeometryProfileVersion", cameraGeometryProfileVersion)
    put("annotatedHolds", JSONArray(annotatedHolds.map { it.toJsonObject() }))
    put("startHoldIds", JSONArray(startHoldIds))
    put("finishHoldIds", JSONArray(finishHoldIds))
    put("groundTruthContacts", JSONArray(groundTruthContacts.map { it.toJsonObject() }))
    put("notes", notes ?: JSONObject.NULL)
    put("createdAtEpochMs", createdAtEpochMs)
}.toString()

fun String.toManualValidationSession(): ManualValidationSession {
    val o = JSONObject(this)
    val holdsArray = o.getJSONArray("annotatedHolds")
    val holds = (0 until holdsArray.length()).map { holdsArray.getJSONObject(it).toValidationHoldAnnotation() }
    val startHolds = o.getJSONArray("startHoldIds").let { array -> (0 until array.length()).map { array.getInt(it) } }
    val finishHolds = o.getJSONArray("finishHoldIds").let { array -> (0 until array.length()).map { array.getInt(it) } }
    val groundTruthArray = o.getJSONArray("groundTruthContacts")
    val groundTruth = (0 until groundTruthArray.length()).map { groundTruthArray.getJSONObject(it).toGroundTruthContactAnnotation() }

    return ManualValidationSession(
        validationSessionId = o.getString("validationSessionId"),
        referenceImagePath = o.getString("referenceImagePath"),
        videoPath = o.getString("videoPath"),
        wallOrFixtureId = o.getString("wallOrFixtureId"),
        cameraGeometryProfileVersion = o.getInt("cameraGeometryProfileVersion"),
        annotatedHolds = holds,
        startHoldIds = startHolds,
        finishHoldIds = finishHolds,
        groundTruthContacts = groundTruth,
        notes = o.opt("notes")?.takeIf { it != JSONObject.NULL } as? String,
        createdAtEpochMs = o.getLong("createdAtEpochMs"),
    )
}

private fun ValidationHoldAnnotation.toJsonObject(): JSONObject = JSONObject().apply {
    put("holdId", holdId)
    put("contourNormalized", JSONArray(contourNormalized.map { point -> JSONObject().apply { put("x", point.x.toDouble()); put("y", point.y.toDouble()) } }))
}

private fun JSONObject.toValidationHoldAnnotation(): ValidationHoldAnnotation {
    val contourArray = getJSONArray("contourNormalized")
    val contour = (0 until contourArray.length()).map { index ->
        val pointObject = contourArray.getJSONObject(index)
        Point2D(pointObject.getDouble("x").toFloat(), pointObject.getDouble("y").toFloat())
    }
    return ValidationHoldAnnotation(holdId = getInt("holdId"), contourNormalized = contour)
}

private fun GroundTruthContactAnnotation.toJsonObject(): JSONObject = JSONObject().apply {
    put("limb", limb.name)
    put("holdId", holdId)
    put("approxTimestampMs", approxTimestampMs)
    put("note", note ?: JSONObject.NULL)
}

private fun JSONObject.toGroundTruthContactAnnotation(): GroundTruthContactAnnotation = GroundTruthContactAnnotation(
    limb = Limb.valueOf(getString("limb")),
    holdId = getInt("holdId"),
    approxTimestampMs = getLong("approxTimestampMs"),
    note = opt("note")?.takeIf { it != JSONObject.NULL } as? String,
)
