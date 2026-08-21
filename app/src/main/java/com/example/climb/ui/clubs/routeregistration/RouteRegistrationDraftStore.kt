package com.example.climb.ui.clubs.routeregistration

/**
 * Where a saved [RouteRegistrationDraftResult] goes. Phase 2A is explicitly hardware-independent
 * UI/domain-flow work — there is no Firestore `walls`/`wallCalibrations`/`routeVisionProfiles`
 * collection, staff-access-checked repository method, or security rule for any of this yet (see
 * `ClubRepository.routeVersionFromMap`/`toFirestoreMap`'s own doc comment: "not yet wired into
 * createRoute"). [InMemoryRouteRegistrationDraftStore] is the only implementation for this phase —
 * a real backend-store swap is a later, separate step.
 */
interface RouteRegistrationDraftStore {
    fun saveDraft(result: RouteRegistrationDraftResult)
    fun loadDrafts(): List<RouteRegistrationDraftResult>
}

class InMemoryRouteRegistrationDraftStore : RouteRegistrationDraftStore {
    private val drafts = mutableListOf<RouteRegistrationDraftResult>()

    override fun saveDraft(result: RouteRegistrationDraftResult) {
        drafts.add(result)
    }

    override fun loadDrafts(): List<RouteRegistrationDraftResult> = drafts.toList()
}
