package com.example.climb.validation

import com.example.climb.colordetection.Point2D
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where a [ValidationWallSetup] is saved — local-only, mirroring
 * [ManualValidationSessionStore]'s exact pattern. There is no Firestore collection for this, and
 * never will be one — [LocalJsonValidationWallSetupStore] is the only implementation, writing
 * plain JSON files under a local directory so a growing library of annotated wall setups survives
 * across app runs without ever touching a backend.
 */
interface ValidationWallSetupStore {
    fun saveWallSetup(setup: ValidationWallSetup)
    fun loadWallSetups(): List<ValidationWallSetup>
    fun loadWallSetup(wallSetupId: String): ValidationWallSetup?
    fun deleteWallSetup(wallSetupId: String)
}

class LocalJsonValidationWallSetupStore(private val directory: File) : ValidationWallSetupStore {

    override fun saveWallSetup(setup: ValidationWallSetup) {
        if (!directory.exists()) directory.mkdirs()
        File(directory, "${setup.wallSetupId}.json").writeText(setup.toJson())
    }

    override fun loadWallSetups(): List<ValidationWallSetup> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file -> runCatching { file.readText().toValidationWallSetup() }.getOrNull() }
            ?.sortedByDescending { it.createdAtEpochMs }
            ?: emptyList()
    }

    override fun loadWallSetup(wallSetupId: String): ValidationWallSetup? {
        val file = File(directory, "$wallSetupId.json")
        if (!file.exists()) return null
        return runCatching { file.readText().toValidationWallSetup() }.getOrNull()
    }

    override fun deleteWallSetup(wallSetupId: String) {
        File(directory, "$wallSetupId.json").delete()
    }
}

fun ValidationWallSetup.toJson(): String = JSONObject().apply {
    put("wallSetupId", wallSetupId)
    put("wallOrFixtureId", wallOrFixtureId)
    put("referenceImagePath", referenceImagePath)
    put("cameraGeometryProfileVersion", cameraGeometryProfileVersion)
    put("annotatedHolds", JSONArray(annotatedHolds.map { it.toJsonObjectForWallSetup() }))
    put("routeDefinitions", JSONArray(routeDefinitions.map { it.toJsonObject() }))
    put("createdAtEpochMs", createdAtEpochMs)
}.toString()

fun String.toValidationWallSetup(): ValidationWallSetup {
    val o = JSONObject(this)
    val holdsArray = o.getJSONArray("annotatedHolds")
    val holds = (0 until holdsArray.length()).map { holdsArray.getJSONObject(it).toValidationHoldAnnotationForWallSetup() }
    val routeDefinitionsArray = o.getJSONArray("routeDefinitions")
    val routeDefinitions = (0 until routeDefinitionsArray.length()).map { routeDefinitionsArray.getJSONObject(it).toValidationRouteDefinition() }

    return ValidationWallSetup(
        wallSetupId = o.getString("wallSetupId"),
        wallOrFixtureId = o.getString("wallOrFixtureId"),
        referenceImagePath = o.getString("referenceImagePath"),
        cameraGeometryProfileVersion = o.getInt("cameraGeometryProfileVersion"),
        annotatedHolds = holds,
        routeDefinitions = routeDefinitions,
        createdAtEpochMs = o.getLong("createdAtEpochMs"),
    )
}

// [ValidationHoldAnnotation]'s JSON helpers already exist in ManualValidationSessionStore.kt but
// are declared `private` to that file, so this is a distinctly named equivalent pair rather than a
// duplicate declaration.
private fun ValidationHoldAnnotation.toJsonObjectForWallSetup(): JSONObject = JSONObject().apply {
    put("holdId", holdId)
    put("contourNormalized", JSONArray(contourNormalized.map { point -> JSONObject().apply { put("x", point.x.toDouble()); put("y", point.y.toDouble()) } }))
}

private fun JSONObject.toValidationHoldAnnotationForWallSetup(): ValidationHoldAnnotation {
    val contourArray = getJSONArray("contourNormalized")
    val contour = (0 until contourArray.length()).map { index ->
        val pointObject = contourArray.getJSONObject(index)
        Point2D(pointObject.getDouble("x").toFloat(), pointObject.getDouble("y").toFloat())
    }
    return ValidationHoldAnnotation(holdId = getInt("holdId"), contourNormalized = contour)
}
