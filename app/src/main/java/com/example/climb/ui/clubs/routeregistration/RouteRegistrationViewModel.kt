package com.example.climb.ui.clubs.routeregistration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.climb.clubs.FinishPolicy
import com.example.climb.clubs.HoldRole
import com.example.climb.clubs.RouteColorConflictChecker
import com.example.climb.clubs.RouteVersionSnapshotValidator
import com.example.climb.clubs.StartPolicy
import com.example.climb.clubs.WallEntity
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.data.RouteColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns one wizard pass's [RouteRegistrationDraftState] and forwards every UI event to the pure
 * functions in this package ([RouteRegistrationHoldSelection], [RouteRegistrationDraftBuilder],
 * [RouteColorConflictChecker], [RouteVersionSnapshotValidator]) — this class itself has no
 * validation/business logic of its own, so those stay independently unit-testable without a
 * ViewModel/Compose runtime.
 */
class RouteRegistrationViewModel(
    private val organizationId: Long,
    private val setterUserId: String,
    private val draftStore: RouteRegistrationDraftStore = InMemoryRouteRegistrationDraftStore(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(RouteRegistrationDraftState(organizationId = organizationId))
    val state: StateFlow<RouteRegistrationDraftState> = _state.asStateFlow()

    val availableWalls: List<WallEntity> = RouteRegistrationFixtures.availableWalls(organizationId)

    private var localIdCounter = 0L

    /** Negative, unmistakably-not-a-real-id values — see [RouteRegistrationDraftBuilder]'s doc
     * comment on why Phase 2A never allocates a real `ClubRepository.nextId()`. */
    private fun nextLocalId(): Long {
        localIdCounter -= 1
        return localIdCounter
    }

    fun selectWall(wall: WallEntity) {
        _state.update { it.copy(wall = wall, capturedFrame = null, holds = emptyList()) }
    }

    fun requestReferenceFrame() {
        val wall = _state.value.wall ?: return
        val frame = RouteRegistrationFixtures.requestReferenceFrame(organizationId, wall)
        _state.update { it.copy(capturedFrame = frame, holds = RouteRegistrationFixtures.detectedHoldsFixture()) }
    }

    fun updateWallRoi(roi: NormalizedRect) = _state.update { it.copy(wallRoiNormalized = roi) }

    fun selectColor(routeColor: RouteColor) = _state.update { it.copy(candidateColorHex = routeColor.hex) }

    /** Live conflict feedback for the color/grade step — same check [buildAndValidateDraft] runs
     * again at save time, exposed early so staff sees a blocked color before finishing the wizard. */
    fun currentColorConflict(): RouteColorConflictChecker.ConflictCheckResult {
        val current = _state.value
        val colorHex = current.candidateColorHex
        val wallId = current.wall?.id
        if (colorHex == null || wallId == null) return RouteColorConflictChecker.ConflictCheckResult(false, emptyList())
        return RouteColorConflictChecker.checkConflicts(colorHex, RouteRegistrationFixtures.activeColorHexesForWall(wallId))
    }

    fun updateGrade(grade: Int?) = _state.update { it.copy(grade = grade) }

    fun updatePublicNumberOrName(value: String?) =
        _state.update { it.copy(publicNumberOrName = value?.trim()?.takeIf { trimmed -> trimmed.isNotEmpty() }) }

    fun updateStartPolicy(policy: StartPolicy?) = _state.update { it.copy(startPolicy = policy) }
    fun updateFinishPolicy(policy: FinishPolicy?) = _state.update { it.copy(finishPolicy = policy) }

    fun selectStartHold(holdId: Int) =
        _state.update { it.copy(holds = RouteRegistrationHoldSelection.selectStartHold(it.holds, holdId)) }

    fun selectFinishHold(holdId: Int) =
        _state.update { it.copy(holds = RouteRegistrationHoldSelection.selectFinishHold(it.holds, holdId)) }

    fun setHoldRole(holdId: Int, role: HoldRole) =
        _state.update { it.copy(holds = RouteRegistrationHoldSelection.setRole(it.holds, holdId, role)) }

    fun removeHold(holdId: Int) =
        _state.update { it.copy(holds = RouteRegistrationHoldSelection.removeHold(it.holds, holdId)) }

    /** Builds the draft entities and runs both validations against them — the same combination a
     * unit test exercises directly against [RouteRegistrationDraftBuilder]/
     * [RouteVersionSnapshotValidator]/[RouteColorConflictChecker], just driven from live wizard
     * state here. Returns `null` only if the draft can't even be constructed (no wall/reference
     * frame yet) — an incomplete-but-constructible draft still returns a result, with
     * [RouteRegistrationValidationSummary.canSaveDraft] `false`. */
    fun buildAndValidateDraft(): Pair<RouteRegistrationDraftResult, RouteRegistrationValidationSummary>? {
        val current = _state.value
        val result = RouteRegistrationDraftBuilder.build(
            state = current,
            setterUserId = setterUserId,
            localIdAllocator = ::nextLocalId,
            nowEpochMs = clock(),
        ) ?: return null

        val snapshotValidation = RouteVersionSnapshotValidator.validateDraft(result.routeVersion)
        val colorConflict = result.routeVersion.colorHex?.let { colorHex ->
            RouteColorConflictChecker.checkConflicts(colorHex, RouteRegistrationFixtures.activeColorHexesForWall(result.wallCalibration.wallId))
        } ?: RouteColorConflictChecker.ConflictCheckResult(false, emptyList())

        return result to RouteRegistrationValidationSummary(snapshotValidation, colorConflict)
    }

    /** Only ever called once [RouteRegistrationValidationSummary.canSaveDraft] is true — saves to
     * [draftStore] (in-memory only this phase, see its own doc comment). Never activates
     * anything: [result]'s `routeVersion.registrationStatus` is always
     * `RouteRegistrationStatus.DRAFT`, set by [RouteRegistrationDraftBuilder]. */
    fun saveDraft(result: RouteRegistrationDraftResult) = draftStore.saveDraft(result)

    companion object {
        fun factory(organizationId: Long, setterUserId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RouteRegistrationViewModel(organizationId, setterUserId) as T
            }
    }
}
