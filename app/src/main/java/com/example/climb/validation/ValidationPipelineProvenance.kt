package com.example.climb.validation

/** Whether one pipeline stage's result for one run came from a local cache or was freshly
 * computed - see [StageProvenance] for how the two combine with [StageProvenance.invalidationReason]
 * to distinguish an ordinary first run from a real cache invalidation. */
enum class CacheOutcome { CACHE_HIT, RECOMPUTED }

/** One pipeline stage's cache provenance for one run. [invalidationReason] is non-null ONLY when
 * [outcome] is RECOMPUTED because a PRIOR cache entry existed but its own key no longer matched the
 * current inputs (a real invalidation) - it stays null when RECOMPUTED simply because no cache
 * entry existed yet at all (an ordinary first run). This lets the debug UI show the simple
 * CACHE HIT / RECOMPUTED status on every run, while still surfacing WHY something was invalidated
 * when that's what actually happened, matching this phase's own worked examples (e.g. "Attribution
 * RECOMPUTED" after a route-definition change is a RECOMPUTED-due-to-invalidation case, distinct
 * from a plain first-run RECOMPUTED). */
data class StageProvenance(
    val outcome: CacheOutcome,
    val invalidationReason: String? = null,
)

/** The full pipeline's provenance for one validation run. [contact]/[attribution] are null when the
 * run never reached that stage at all (e.g. pose extraction itself failed, or the geometry gate
 * rejected the clip before contact analysis could run) - null here means "not applicable this run,"
 * never "recomputed with nothing to show." */
data class ValidationPipelineProvenance(
    val pose: StageProvenance,
    val contact: StageProvenance?,
    val attribution: StageProvenance?,
)
