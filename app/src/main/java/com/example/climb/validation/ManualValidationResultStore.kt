package com.example.climb.validation

import java.io.File

/**
 * Where an already-built [ClipValidationExport] is saved — local-only, exactly the same
 * `ManualValidationSessionStore.kt` pattern this mirrors (one JSON file per id, under a local
 * directory), so a growing dataset of processed clips can be summarized
 * ([ValidationDatasetSummaryBuilder]) without re-running MediaPipe/`HoldContactDetector`/
 * `RouteAttributionEngine` over every clip again. There is no Firestore collection for any of this,
 * and never will be one for this raw debug-export shape - see this package's trust-boundary doc
 * comment on [ManualValidationSession] for why. [LocalJsonManualValidationResultStore] is the only
 * implementation.
 */
interface ManualValidationResultStore {
    fun saveResult(export: ClipValidationExport)
    fun loadResults(): List<ClipValidationExport>
    fun loadResult(validationSessionId: String): ClipValidationExport?
    fun deleteResult(validationSessionId: String)
}

class LocalJsonManualValidationResultStore(private val directory: File) : ManualValidationResultStore {

    override fun saveResult(export: ClipValidationExport) {
        if (!directory.exists()) directory.mkdirs()
        File(directory, "${export.validationSessionId}.json").writeText(export.toJson())
    }

    override fun loadResults(): List<ClipValidationExport> {
        if (!directory.exists()) return emptyList()
        // File system directory listing order is not guaranteed - sort explicitly by a stable key
        // (validationSessionId) rather than relying on incidental listFiles() order.
        return directory.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file -> runCatching { file.readText().toClipValidationExport() }.getOrNull() }
            ?.sortedBy { it.validationSessionId }
            ?: emptyList()
    }

    override fun loadResult(validationSessionId: String): ClipValidationExport? {
        val file = File(directory, "$validationSessionId.json")
        if (!file.exists()) return null
        return runCatching { file.readText().toClipValidationExport() }.getOrNull()
    }

    override fun deleteResult(validationSessionId: String) {
        File(directory, "$validationSessionId.json").delete()
    }
}
