package com.example.climb.ui.validation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.climb.analysis.contact.Limb
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.MediaPipePoseEstimator
import com.example.climb.pose.PoseAnalysisProgress
import com.example.climb.validation.GroundTruthContactAnnotation
import com.example.climb.validation.ImageDimensions
import com.example.climb.validation.LocalJsonManualValidationSessionStore
import com.example.climb.validation.ManualValidationOutcome
import com.example.climb.validation.ManualValidationPipeline
import com.example.climb.validation.ManualValidationReport
import com.example.climb.validation.ManualValidationReportBuilder
import com.example.climb.validation.ManualValidationSession
import com.example.climb.validation.ValidationHoldAnnotation
import com.example.climb.validation.ValidationMediaImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** The current wall-camera geometry profile version the manual validation harness assumes every
 * new session was captured under — matches `com.example.climb.edge.CameraGeometryProfile()`'s own
 * default version (1). A real integration would read this from that shared type directly; kept as
 * a plain literal here since `:app` already mirrors similar defaults elsewhere (see
 * `FixedCameraRouteRegistrationConfig`'s own doc comment on the same tradeoff). */
private const val EXPECTED_GEOMETRY_PROFILE_VERSION = 1

data class ValidationDebugUiState(
    val wallOrFixtureId: String = "",
    val cameraGeometryProfileVersion: Int = EXPECTED_GEOMETRY_PROFILE_VERSION,
    val notes: String = "",
    val referenceImagePath: String? = null,
    val referenceImageDimensions: ImageDimensions? = null,
    val videoPath: String? = null,
    val holds: List<ValidationHoldAnnotation> = emptyList(),
    val inProgressHoldVertices: List<Point2D> = emptyList(),
    val startHoldIds: Set<Int> = emptySet(),
    val finishHoldIds: Set<Int> = emptySet(),
    val groundTruthContacts: List<GroundTruthContactAnnotation> = emptyList(),
    val isProcessing: Boolean = false,
    val progress: PoseAnalysisProgress? = null,
    val outcome: ManualValidationOutcome? = null,
    val report: ManualValidationReport? = null,
    val savedSessions: List<ManualValidationSession> = emptyList(),
    val scrubPositionMs: Long = 0L,
    val statusMessage: String? = null,
) {
    val nextHoldId: Int get() = ((holds.maxOfOrNull { it.holdId } ?: 0) + 1)
}

class ValidationDebugViewModel(private val appContext: Context) : ViewModel() {

    private val poseEstimator = MediaPipePoseEstimator(appContext)
    private val sessionStore = LocalJsonManualValidationSessionStore(File(appContext.filesDir, "validation_sessions"))
    private val mediaDirectory = File(appContext.filesDir, "validation_media")

    private val _state = MutableStateFlow(ValidationDebugUiState(savedSessions = sessionStore.loadSessions()))
    val state: StateFlow<ValidationDebugUiState> = _state.asStateFlow()

    fun updateWallOrFixtureId(value: String) = _state.update { it.copy(wallOrFixtureId = value) }
    fun updateGeometryProfileVersion(value: Int) = _state.update { it.copy(cameraGeometryProfileVersion = value) }
    fun updateNotes(value: String) = _state.update { it.copy(notes = value) }
    fun updateScrubPosition(ms: Long) = _state.update { it.copy(scrubPositionMs = ms) }

    fun importReferenceImage(uri: Uri) {
        val file = ValidationMediaImport.importFile(appContext, uri, mediaDirectory, "ref_${UUID.randomUUID()}.jpg") ?: run {
            _state.update { it.copy(statusMessage = "Could not import that reference image") }
            return
        }
        val dimensions = ValidationMediaImport.readImageDimensions(file.absolutePath)
        _state.update { it.copy(referenceImagePath = file.absolutePath, referenceImageDimensions = dimensions, statusMessage = null) }
    }

    fun importVideo(uri: Uri) {
        val file = ValidationMediaImport.importFile(appContext, uri, mediaDirectory, "clip_${UUID.randomUUID()}.mp4") ?: run {
            _state.update { it.copy(statusMessage = "Could not import that video") }
            return
        }
        _state.update { it.copy(videoPath = file.absolutePath, outcome = null, report = null, statusMessage = null) }
    }

    fun addHoldVertex(point: Point2D) = _state.update { it.copy(inProgressHoldVertices = it.inProgressHoldVertices + point) }

    fun clearInProgressHold() = _state.update { it.copy(inProgressHoldVertices = emptyList()) }

    fun finishCurrentHold() {
        val current = _state.value
        if (current.inProgressHoldVertices.size < 3) return
        val newHold = ValidationHoldAnnotation(holdId = current.nextHoldId, contourNormalized = current.inProgressHoldVertices)
        _state.update { it.copy(holds = it.holds + newHold, inProgressHoldVertices = emptyList()) }
    }

    fun removeHold(holdId: Int) = _state.update {
        it.copy(
            holds = it.holds.filterNot { hold -> hold.holdId == holdId },
            startHoldIds = it.startHoldIds - holdId,
            finishHoldIds = it.finishHoldIds - holdId,
        )
    }

    fun toggleStartHold(holdId: Int) = _state.update {
        it.copy(startHoldIds = if (holdId in it.startHoldIds) it.startHoldIds - holdId else it.startHoldIds + holdId)
    }

    fun toggleFinishHold(holdId: Int) = _state.update {
        it.copy(finishHoldIds = if (holdId in it.finishHoldIds) it.finishHoldIds - holdId else it.finishHoldIds + holdId)
    }

    fun addGroundTruthContact(limb: Limb, holdId: Int, timestampMs: Long, note: String?) = _state.update {
        it.copy(groundTruthContacts = it.groundTruthContacts + GroundTruthContactAnnotation(limb, holdId, timestampMs, note))
    }

    fun removeGroundTruthContact(index: Int) = _state.update {
        it.copy(groundTruthContacts = it.groundTruthContacts.filterIndexed { i, _ -> i != index })
    }

    private fun buildSessionOrNull(): ManualValidationSession? {
        val current = _state.value
        val referenceImagePath = current.referenceImagePath ?: return null
        val videoPath = current.videoPath ?: return null
        if (current.wallOrFixtureId.isBlank()) return null
        return ManualValidationSession(
            validationSessionId = UUID.randomUUID().toString(),
            referenceImagePath = referenceImagePath,
            videoPath = videoPath,
            wallOrFixtureId = current.wallOrFixtureId,
            cameraGeometryProfileVersion = current.cameraGeometryProfileVersion,
            annotatedHolds = current.holds,
            startHoldIds = current.startHoldIds.toList(),
            finishHoldIds = current.finishHoldIds.toList(),
            groundTruthContacts = current.groundTruthContacts,
            notes = current.notes.ifBlank { null },
            createdAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun runAnalysis() {
        val session = buildSessionOrNull() ?: run {
            _state.update { it.copy(statusMessage = "Import a reference image + video and set a wall/fixture id first") }
            return
        }
        val referenceDimensions = _state.value.referenceImageDimensions ?: run {
            _state.update { it.copy(statusMessage = "Could not read the reference image's dimensions") }
            return
        }

        _state.update { it.copy(isProcessing = true, outcome = null, report = null, statusMessage = null) }
        viewModelScope.launch {
            val outcome = ManualValidationPipeline.run(
                session = session,
                poseEstimator = poseEstimator,
                referenceImageDimensions = referenceDimensions,
                expectedGeometryProfileVersion = EXPECTED_GEOMETRY_PROFILE_VERSION,
                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
            )
            val report = (outcome as? ManualValidationOutcome.Processed)?.let {
                ManualValidationReportBuilder.build(it.frameDiagnostics, it.timeline, session.groundTruthContacts)
            }
            _state.update { it.copy(isProcessing = false, outcome = outcome, report = report) }
        }
    }

    fun saveCurrentSession() {
        val session = buildSessionOrNull() ?: return
        sessionStore.saveSession(session)
        _state.update { it.copy(savedSessions = sessionStore.loadSessions(), statusMessage = "Saved as ${session.validationSessionId}") }
    }

    fun loadSession(session: ManualValidationSession) {
        _state.update {
            it.copy(
                wallOrFixtureId = session.wallOrFixtureId,
                cameraGeometryProfileVersion = session.cameraGeometryProfileVersion,
                notes = session.notes.orEmpty(),
                referenceImagePath = session.referenceImagePath,
                referenceImageDimensions = ValidationMediaImport.readImageDimensions(session.referenceImagePath),
                videoPath = session.videoPath,
                holds = session.annotatedHolds,
                inProgressHoldVertices = emptyList(),
                startHoldIds = session.startHoldIds.toSet(),
                finishHoldIds = session.finishHoldIds.toSet(),
                groundTruthContacts = session.groundTruthContacts,
                outcome = null,
                report = null,
                statusMessage = "Loaded ${session.validationSessionId}",
            )
        }
    }

    fun deleteSession(validationSessionId: String) {
        sessionStore.deleteSession(validationSessionId)
        _state.update { it.copy(savedSessions = sessionStore.loadSessions()) }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ValidationDebugViewModel(appContext) as T
        }
    }
}
