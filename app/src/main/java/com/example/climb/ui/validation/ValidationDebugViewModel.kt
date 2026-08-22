package com.example.climb.ui.validation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.climb.analysis.contact.Limb
import com.example.climb.analysis.metrics.HoldContactConfig
import com.example.climb.attribution.AttributionResult
import com.example.climb.attribution.RouteAttributionScoringConfig
import com.example.climb.clubs.AttemptResult
import com.example.climb.colordetection.Point2D
import com.example.climb.pose.MediaPipePoseEstimator
import com.example.climb.pose.PoseAnalysisProgress
import com.example.climb.validation.AttributionCacheStore
import com.example.climb.validation.BatchQueueItem
import com.example.climb.validation.ClipBatchStatus
import com.example.climb.validation.ClipValidationExport
import com.example.climb.validation.ClipValidationExportBuilder
import com.example.climb.validation.ContactAnalysisStore
import com.example.climb.validation.GroundTruthContactAnnotation
import com.example.climb.validation.ImageDimensions
import com.example.climb.validation.LocalJsonAttributionCacheStore
import com.example.climb.validation.LocalJsonContactAnalysisStore
import com.example.climb.validation.LocalJsonManualValidationResultStore
import com.example.climb.validation.LocalJsonManualValidationSessionStore
import com.example.climb.validation.LocalJsonPoseArtifactStore
import com.example.climb.validation.LocalJsonValidationWallSetupStore
import com.example.climb.validation.ManualValidationAttributionEvaluator
import com.example.climb.validation.ManualValidationOutcome
import com.example.climb.validation.ManualValidationReport
import com.example.climb.validation.ManualValidationSession
import com.example.climb.validation.PoseArtifactStore
import com.example.climb.validation.ValidationBatchQueue
import com.example.climb.validation.ValidationDatasetSummary
import com.example.climb.validation.ValidationDatasetSummaryBuilder
import com.example.climb.validation.ValidationHoldAnnotation
import com.example.climb.validation.ValidationMediaImport
import com.example.climb.validation.ValidationPipelineError
import com.example.climb.validation.ValidationPipelineProvenance
import com.example.climb.validation.ValidationPipelineRunner
import com.example.climb.validation.ValidationPreflightCheck
import com.example.climb.validation.ValidationRouteDefinition
import com.example.climb.validation.ValidationWallSetup
import com.example.climb.validation.videoFingerprint
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
    /** Phase 4B: candidate routes being edited for the CURRENT session, wired to
     * `com.example.climb.attribution.RouteAttributionEngine` via
     * [com.example.climb.validation.ManualValidationAttributionRunner]. */
    val routeDefinitions: List<ValidationRouteDefinition> = emptyList(),
    val attemptStartTimestampMs: Long = 0L,
    /** Kept as a raw text field value, same pattern as [wallOrFixtureId] - parsed to a `Long?` only
     * when [ManualValidationSession.expectedRouteId] is actually built. */
    val expectedRouteId: String = "",
    val expectedResult: AttemptResult = AttemptResult.UNKNOWN,
    val attributionResult: AttributionResult? = null,
    /** Which candidate's holds/foreign-contacts are highlighted in the video overlay - `null`
     * means "no route focused yet". */
    val focusedRouteId: Long? = null,
    val savedWallSetups: List<ValidationWallSetup> = emptyList(),
    /** Which saved [ValidationWallSetup], if any, the CURRENT session is being built from - pure
     * traceability metadata copied into [ManualValidationSession.wallSetupId], never a runtime
     * lookup dependency (see [ValidationWallSetup]'s own doc comment). */
    val buildingFromWallSetupId: String? = null,
    /** The last export built for the current session, for on-screen preview. */
    val currentExport: ClipValidationExport? = null,
    val datasetSummary: ValidationDatasetSummary? = null,
    /** Phase 4C's "can Run Analysis be pressed right now" checklist. Deliberately left `null` and
     * never assigned by any mutation function below — kept as a plain settable field only so a test
     * or a future caller can stash one on the state directly, but the real, always-fresh checklist
     * is [ValidationDebugViewModel.currentPreflightCheck], which recomputes it on demand from
     * whatever is in state (plus the pose cache) right now rather than risking this field silently
     * going stale after a mutation this class didn't specifically know to refresh it for. */
    val preflightCheck: ValidationPreflightCheck? = null,
    /** The imported video's real decoded dimensions - read once, right after import, via
     * [ValidationMediaImport.readVideoDimensions] - `null` when unreadable, matching
     * [importVideo]'s own null-tolerant style. */
    val videoDimensions: ImageDimensions? = null,
    /** Cache provenance (hit/recomputed/invalidated, per stage) for the most recent
     * [ValidationPipelineRunner.run] call - `null` before the first run this session. */
    val pipelineProvenance: ValidationPipelineProvenance? = null,
    /** Non-null exactly when the most recent pipeline run ended in
     * [ManualValidationOutcome.Rejected] (or failed unexpectedly) - `null` on a successful run. */
    val pipelineError: ValidationPipelineError? = null,
    /** Which saved sessions are currently checked for the next [ValidationDebugViewModel.runBatch]
     * call. */
    val batchSelectedSessionIds: Set<String> = emptySet(),
    /** One entry per clip the most recent (or currently running) batch has touched, in queue order -
     * see [BatchQueueItem]. */
    val batchItems: List<BatchQueueItem> = emptyList(),
    /** (completed, total) for the batch currently running, or the last one that ran - `null` when no
     * batch has run yet this session. */
    val batchProgress: Pair<Int, Int>? = null,
    val isBatchRunning: Boolean = false,
) {
    val nextHoldId: Int get() = ((holds.maxOfOrNull { it.holdId } ?: 0) + 1)
}

class ValidationDebugViewModel(private val appContext: Context) : ViewModel() {

    private val poseEstimator = MediaPipePoseEstimator(appContext)
    private val sessionStore = LocalJsonManualValidationSessionStore(File(appContext.filesDir, "validation_sessions"))
    private val wallSetupStore = LocalJsonValidationWallSetupStore(File(appContext.filesDir, "validation_wall_setups"))
    private val resultStore = LocalJsonManualValidationResultStore(File(appContext.filesDir, "validation_results"))
    private val mediaDirectory = File(appContext.filesDir, "validation_media")

    // Phase 4C's three local caching layers - same appContext.filesDir pattern as every store
    // above, one directory per stage so each can be inspected/cleared independently.
    private val poseArtifactStore: PoseArtifactStore =
        LocalJsonPoseArtifactStore(File(appContext.filesDir, "validation_pose_cache"))
    private val contactAnalysisStore: ContactAnalysisStore =
        LocalJsonContactAnalysisStore(File(appContext.filesDir, "validation_contact_cache"))
    private val attributionCacheStore: AttributionCacheStore =
        LocalJsonAttributionCacheStore(File(appContext.filesDir, "validation_attribution_cache"))

    private val _state = MutableStateFlow(
        ValidationDebugUiState(savedSessions = sessionStore.loadSessions(), savedWallSetups = wallSetupStore.loadWallSetups()),
    )
    val state: StateFlow<ValidationDebugUiState> = _state.asStateFlow()

    /** Read by [runBatch]'s `isCancelled` lambda - [cancelBatch] is the only writer. Plain
     * `@Volatile`, matching this phase's own guidance, since it's a simple one-shot flag read from a
     * coroutine that isn't otherwise synchronized with the writer. */
    @Volatile
    private var isBatchCancelled: Boolean = false

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
        val dimensions = ValidationMediaImport.readVideoDimensions(file.absolutePath)
        _state.update {
            it.copy(
                videoPath = file.absolutePath,
                videoDimensions = dimensions,
                outcome = null,
                report = null,
                statusMessage = null,
            )
        }
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

    fun addRouteDefinition(definition: ValidationRouteDefinition) = _state.update {
        it.copy(routeDefinitions = it.routeDefinitions + definition)
    }

    fun removeRouteDefinition(routeId: Long) = _state.update {
        it.copy(
            routeDefinitions = it.routeDefinitions.filterNot { definition -> definition.routeId == routeId },
            focusedRouteId = if (it.focusedRouteId == routeId) null else it.focusedRouteId,
        )
    }

    fun markAttemptStartAtScrub() = _state.update { it.copy(attemptStartTimestampMs = it.scrubPositionMs) }

    fun updateExpectedRouteId(value: String) = _state.update { it.copy(expectedRouteId = value) }

    fun updateExpectedResult(value: AttemptResult) = _state.update { it.copy(expectedResult = value) }

    fun setFocusedRoute(routeId: Long?) = _state.update { it.copy(focusedRouteId = routeId) }

    /** Builds a [ValidationWallSetup] from the current reference image / holds / candidate routes
     * so this wall doesn't need to be re-annotated for every new clip filmed against it - see that
     * type's own doc comment. Fails gracefully with a [ValidationDebugUiState.statusMessage] (never
     * throws) when the required fields aren't populated yet, the same pattern [buildSessionOrNull]
     * already uses for its own null-checks. */
    fun saveCurrentWallSetup(wallSetupId: String = UUID.randomUUID().toString()) {
        val current = _state.value
        val referenceImagePath = current.referenceImagePath ?: run {
            _state.update { it.copy(statusMessage = "Import a reference image before saving a wall setup") }
            return
        }
        if (current.wallOrFixtureId.isBlank()) {
            _state.update { it.copy(statusMessage = "Set a wallOrFixtureId before saving a wall setup") }
            return
        }
        val setup = ValidationWallSetup(
            wallSetupId = wallSetupId,
            wallOrFixtureId = current.wallOrFixtureId,
            referenceImagePath = referenceImagePath,
            cameraGeometryProfileVersion = current.cameraGeometryProfileVersion,
            annotatedHolds = current.holds,
            routeDefinitions = current.routeDefinitions,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        wallSetupStore.saveWallSetup(setup)
        _state.update {
            it.copy(
                savedWallSetups = wallSetupStore.loadWallSetups(),
                buildingFromWallSetupId = wallSetupId,
                statusMessage = "Saved wall setup $wallSetupId",
            )
        }
    }

    /** Copies [setup]'s fields into the current UI state, mirroring [ValidationWallSetup.applyTo]'s
     * field copying - but into UI state fields rather than constructing a [ManualValidationSession]
     * directly, since the video hasn't been imported yet at this point in the flow. */
    fun applyWallSetup(setup: ValidationWallSetup) {
        val dimensions = ValidationMediaImport.readImageDimensions(setup.referenceImagePath)
        _state.update {
            it.copy(
                referenceImagePath = setup.referenceImagePath,
                referenceImageDimensions = dimensions,
                holds = setup.annotatedHolds,
                inProgressHoldVertices = emptyList(),
                cameraGeometryProfileVersion = setup.cameraGeometryProfileVersion,
                routeDefinitions = setup.routeDefinitions,
                wallOrFixtureId = setup.wallOrFixtureId,
                buildingFromWallSetupId = setup.wallSetupId,
                statusMessage = "Applied wall setup ${setup.wallSetupId}",
            )
        }
    }

    fun deleteWallSetup(wallSetupId: String) {
        wallSetupStore.deleteWallSetup(wallSetupId)
        _state.update {
            it.copy(
                savedWallSetups = wallSetupStore.loadWallSetups(),
                buildingFromWallSetupId = if (it.buildingFromWallSetupId == wallSetupId) null else it.buildingFromWallSetupId,
            )
        }
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
            routeDefinitions = current.routeDefinitions,
            attemptStartTimestampMs = current.attemptStartTimestampMs,
            wallSetupId = current.buildingFromWallSetupId,
            expectedRouteId = current.expectedRouteId.toLongOrNull(),
            expectedResult = current.expectedResult,
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

        _state.update {
            it.copy(
                isProcessing = true,
                outcome = null,
                report = null,
                attributionResult = null,
                currentExport = null,
                statusMessage = null,
                pipelineProvenance = null,
                pipelineError = null,
            )
        }
        viewModelScope.launch {
            // Phase 4C: routed through the caching-and-provenance orchestrator instead of calling
            // ManualValidationPipeline.run + ManualValidationAttributionRunner.run separately - same
            // real calls underneath (see ValidationPipelineRunner's own doc comment), just
            // consulted-through-cache-first. holdContactConfig/routeAttributionScoringConfig are
            // always their own real defaults here, never tuned, matching every other call site in
            // this package.
            val result = ValidationPipelineRunner.run(
                session = session,
                poseEstimator = poseEstimator,
                referenceImageDimensions = referenceDimensions,
                expectedGeometryProfileVersion = EXPECTED_GEOMETRY_PROFILE_VERSION,
                holdContactConfig = HoldContactConfig(),
                routeAttributionScoringConfig = RouteAttributionScoringConfig(),
                poseArtifactStore = poseArtifactStore,
                contactAnalysisStore = contactAnalysisStore,
                attributionCacheStore = attributionCacheStore,
                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
            )
            _state.update {
                it.copy(
                    isProcessing = false,
                    outcome = result.outcome,
                    report = result.report,
                    attributionResult = result.attributionResult,
                    pipelineProvenance = result.provenance,
                    pipelineError = result.error,
                )
            }
        }
    }

    fun saveCurrentSession() {
        val session = buildSessionOrNull() ?: return
        sessionStore.saveSession(session)
        _state.update { it.copy(savedSessions = sessionStore.loadSessions(), statusMessage = "Saved as ${session.validationSessionId}") }
        buildAndPersistExport(session)
    }

    /** Public entry point for the debug UI's "Preview Export" button. Builds a session from
     * whatever is currently in [ValidationDebugUiState] (not necessarily already saved via
     * [saveCurrentSession]) and, when a [ManualValidationReport]/`AttributionResult` are already
     * available in state (i.e. [runAnalysis] has already run for this clip), builds + locally
     * persists + previews a [ClipValidationExport] for it via the private overload below. A quiet
     * no-op (aside from the same missing-inputs [ValidationDebugUiState.statusMessage] used by
     * [runAnalysis]/[saveCurrentSession]) when the required inputs aren't ready yet. */
    fun buildAndPersistExport() {
        val session = buildSessionOrNull() ?: run {
            _state.update { it.copy(statusMessage = "Import a reference image + video and set a wall/fixture id first") }
            return
        }
        buildAndPersistExport(session)
    }

    /** Builds and locally persists a [ClipValidationExport] for [session] whenever a [report] and
     * [attributionResult] are already available in state (i.e. [runAnalysis] has actually run for
     * this clip) - skips silently, with no error and no [ValidationDebugUiState.statusMessage]
     * change, when either is missing. [session] must be the exact same instance [saveCurrentSession]
     * just persisted - never rebuilt here, since [buildSessionOrNull] mints a fresh
     * `validationSessionId` on every call and the export must correlate with the session actually
     * saved to disk. */
    private fun buildAndPersistExport(session: ManualValidationSession) {
        val current = _state.value
        val report = current.report ?: return
        val attributionResult = current.attributionResult ?: return
        val evaluation = ManualValidationAttributionEvaluator.evaluate(session, attributionResult)
        val export = ClipValidationExportBuilder.build(
            session = session,
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            exportedAtEpochMs = System.currentTimeMillis(),
        )
        resultStore.saveResult(export)
        _state.update { it.copy(currentExport = export) }
    }

    /** Rolls up every locally-saved [ClipValidationExport] into one [ValidationDatasetSummary] -
     * pure tallying, never a re-run of the resolver (see that type's own doc comment). */
    fun computeDatasetSummary() {
        val summary = ValidationDatasetSummaryBuilder.build(resultStore.loadResults())
        _state.update { it.copy(datasetSummary = summary) }
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
                videoDimensions = ValidationMediaImport.readVideoDimensions(session.videoPath),
                pipelineProvenance = null,
                pipelineError = null,
                holds = session.annotatedHolds,
                inProgressHoldVertices = emptyList(),
                startHoldIds = session.startHoldIds.toSet(),
                finishHoldIds = session.finishHoldIds.toSet(),
                groundTruthContacts = session.groundTruthContacts,
                outcome = null,
                report = null,
                routeDefinitions = session.routeDefinitions,
                attemptStartTimestampMs = session.attemptStartTimestampMs,
                expectedRouteId = session.expectedRouteId?.toString().orEmpty(),
                expectedResult = session.expectedResult ?: AttemptResult.UNKNOWN,
                attributionResult = null,
                currentExport = null,
                buildingFromWallSetupId = session.wallSetupId,
                focusedRouteId = null,
                statusMessage = "Loaded ${session.validationSessionId}",
            )
        }
    }

    fun deleteSession(validationSessionId: String) {
        sessionStore.deleteSession(validationSessionId)
        _state.update { it.copy(savedSessions = sessionStore.loadSessions()) }
    }

    /** Phase 4C's live "can Run Analysis be pressed right now" checklist - recomputed fresh from
     * whatever is in [state] (plus a cheap pose-cache existence check) every time this is called,
     * rather than trusting [ValidationDebugUiState.preflightCheck] to have been kept in sync (see
     * that field's own doc comment for why it deliberately never is). Meant to be called from the
     * Composable layer wherever the checklist needs to be displayed. */
    fun currentPreflightCheck(): ValidationPreflightCheck {
        val current = _state.value
        val poseArtifactCached = current.videoPath?.let { path ->
            runCatching { poseArtifactStore.hasAnyEntryFor(videoFingerprint(File(path))) }.getOrDefault(false)
        } ?: false
        return ValidationPreflightCheck.evaluate(
            referenceImageDimensions = current.referenceImageDimensions,
            holds = current.holds,
            routeDefinitions = current.routeDefinitions,
            expectedRouteId = current.expectedRouteId.toLongOrNull(),
            videoDimensions = current.videoDimensions,
            cameraGeometryProfileVersion = current.cameraGeometryProfileVersion,
            expectedGeometryProfileVersion = EXPECTED_GEOMETRY_PROFILE_VERSION,
            poseArtifactCached = poseArtifactCached,
        )
    }

    fun toggleBatchSelection(validationSessionId: String) = _state.update {
        it.copy(
            batchSelectedSessionIds = if (validationSessionId in it.batchSelectedSessionIds) {
                it.batchSelectedSessionIds - validationSessionId
            } else {
                it.batchSelectedSessionIds + validationSessionId
            },
        )
    }

    fun selectAllForBatch(sessionIds: List<String>) = _state.update { it.copy(batchSelectedSessionIds = sessionIds.toSet()) }

    fun clearBatchSelection() = _state.update { it.copy(batchSelectedSessionIds = emptySet()) }

    /** Runs [ValidationPipelineRunner.run] (with local caching) for every saved session currently in
     * [ValidationDebugUiState.batchSelectedSessionIds], one at a time via [ValidationBatchQueue.run] -
     * never in parallel, matching that object's own doc comment. A clip that fails to load, fails
     * geometry, or throws for any other reason is marked FAILED and the batch continues with the
     * next clip - a single bad clip must never abort the rest of an overnight-sized batch. */
    fun runBatch() {
        val sessionIds = _state.value.batchSelectedSessionIds.toList()
        if (sessionIds.isEmpty()) return

        isBatchCancelled = false
        _state.update {
            it.copy(
                isBatchRunning = true,
                batchItems = sessionIds.map { id -> BatchQueueItem(id, ClipBatchStatus.NOT_RUN) },
                batchProgress = 0 to sessionIds.size,
            )
        }

        viewModelScope.launch {
            ValidationBatchQueue.run(
                sessionIds = sessionIds,
                isCancelled = { isBatchCancelled },
                onItemStatusChanged = { id, status, errorMessage ->
                    _state.update { it.copy(batchItems = it.batchItems.withStatus(id, status, errorMessage)) }
                },
                onProgress = { completed, total -> _state.update { it.copy(batchProgress = completed to total) } },
                processOne = { id, onStageChanged -> processSessionForBatch(id, onStageChanged) },
            )
            _state.update { it.copy(isBatchRunning = false) }
        }
    }

    /** Read by [runBatch]'s `isCancelled` lambda on the very next item boundary - every already
     * in-flight or already-completed item is unaffected, matching [ValidationBatchQueue.run]'s own
     * doc comment on how cancellation is applied. */
    fun cancelBatch() {
        isBatchCancelled = true
    }

    /** Re-runs the exact same per-clip processing [runBatch] uses for just [validationSessionId],
     * outside of a batch run - e.g. after fixing whatever made it FAILED. Updates that one entry in
     * [ValidationDebugUiState.batchItems] (adding it if it wasn't already present) so the same
     * per-clip status UI reflects the retry. */
    fun retryClip(validationSessionId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(batchItems = it.batchItems.withStatus(validationSessionId, ClipBatchStatus.NOT_RUN, null))
            }
            try {
                processSessionForBatch(validationSessionId) { stage ->
                    _state.update { it.copy(batchItems = it.batchItems.withStatus(validationSessionId, stage, null)) }
                }
                _state.update {
                    it.copy(batchItems = it.batchItems.withStatus(validationSessionId, ClipBatchStatus.COMPLETE, null))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        batchItems = it.batchItems.withStatus(
                            validationSessionId,
                            ClipBatchStatus.FAILED,
                            e.message ?: "Unknown error",
                        ),
                    )
                }
            }
        }
    }

    /** The shared per-clip work both [runBatch] (via [ValidationBatchQueue.run]'s `processOne`) and
     * [retryClip] use: load the session fresh from disk (never the currently-open editor state, since
     * a batch clip is very likely NOT the session currently loaded into [ValidationDebugUiState]),
     * re-derive its own reference image dimensions, run it through [ValidationPipelineRunner.run] with
     * the same real defaults [runAnalysis] uses, and - on a successful [ManualValidationOutcome.Processed]
     * run - build and persist a [ClipValidationExport] for it, mirroring [buildAndPersistExport]'s own
     * build-then-save shape but sourced from this call's own freshly-computed [ValidationPipelineRunResult]
     * rather than from [ValidationDebugUiState] (which may be showing a completely different session).
     * Throws (never swallows) on any failure so [ValidationBatchQueue.run]/[retryClip]'s own try/catch
     * can mark the clip FAILED - a missing session or an unreadable reference image is exactly as much
     * a per-clip failure as a pipeline rejection is. */
    private suspend fun processSessionForBatch(validationSessionId: String, onStageChanged: (ClipBatchStatus) -> Unit) {
        val session = sessionStore.loadSession(validationSessionId)
            ?: error("No saved session found for $validationSessionId")
        val referenceDimensions = ValidationMediaImport.readImageDimensions(session.referenceImagePath)
            ?: error("Could not read reference image dimensions for $validationSessionId")

        onStageChanged(ClipBatchStatus.EXTRACTING_POSE)
        val result = ValidationPipelineRunner.run(
            session = session,
            poseEstimator = poseEstimator,
            referenceImageDimensions = referenceDimensions,
            expectedGeometryProfileVersion = EXPECTED_GEOMETRY_PROFILE_VERSION,
            holdContactConfig = HoldContactConfig(),
            routeAttributionScoringConfig = RouteAttributionScoringConfig(),
            poseArtifactStore = poseArtifactStore,
            contactAnalysisStore = contactAnalysisStore,
            attributionCacheStore = attributionCacheStore,
            onStageChanged = onStageChanged,
        )

        val report = result.report
        val attributionResult = result.attributionResult
        if (report == null || attributionResult == null) {
            val reason = (result.outcome as? ManualValidationOutcome.Rejected)?.reason
                ?: result.error?.message
                ?: "pipeline did not produce a result"
            error(reason)
        }

        val evaluation = ManualValidationAttributionEvaluator.evaluate(session, attributionResult)
        val export = ClipValidationExportBuilder.build(
            session = session,
            report = report,
            attributionResult = attributionResult,
            evaluation = evaluation,
            exportedAtEpochMs = System.currentTimeMillis(),
            provenance = result.provenance,
            lowPoseCoverage = result.lowPoseCoverage,
            wasRejectedBeforeAttribution = false,
        )
        resultStore.saveResult(export)
    }

    /** Replaces [validationSessionId]'s entry in this list with [status]/[errorMessage] (appending a
     * new [BatchQueueItem] if it wasn't already present) - keeps [ValidationDebugUiState.batchItems]
     * a plain, order-preserving snapshot regardless of whether the caller is [runBatch]'s queue
     * callback or [retryClip] touching a single id outside of any queue. */
    private fun List<BatchQueueItem>.withStatus(
        validationSessionId: String,
        status: ClipBatchStatus,
        errorMessage: String?,
    ): List<BatchQueueItem> {
        val updated = BatchQueueItem(validationSessionId, status, errorMessage)
        val existingIndex = indexOfFirst { it.validationSessionId == validationSessionId }
        return if (existingIndex >= 0) {
            toMutableList().also { it[existingIndex] = updated }
        } else {
            this + updated
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ValidationDebugViewModel(appContext) as T
        }
    }
}
