package com.example.climb.validation

/**
 * The state a single clip's batch processing has reached. Deliberately generic — this file has no
 * knowledge of pose extraction, contact analysis, or attribution as concrete operations; the
 * intermediate stage names below are just labels a caller's [ValidationBatchQueue.run]
 * `onStageChanged` callback can report through, useful for showing per-clip progress in the debug
 * UI while a batch is running.
 */
enum class ClipBatchStatus { NOT_RUN, EXTRACTING_POSE, CONTACT_ANALYSIS, ATTRIBUTION, COMPLETE, FAILED, CANCELLED }

/** One clip's current position in a batch run — a plain snapshot for driving UI state, not
 * persisted anywhere by this file. */
data class BatchQueueItem(
    val validationSessionId: String,
    val status: ClipBatchStatus = ClipBatchStatus.NOT_RUN,
    val errorMessage: String? = null,
)

/**
 * A GENERIC, pure sequential batch coordinator — no dependency on pose/contact/attribution caching
 * internals at all. The caller supplies the actual per-clip work as a lambda ([processOne]), so
 * this object is trivially unit-testable with fakes and stays reusable regardless of what
 * "processing one clip" ends up meaning.
 */
object ValidationBatchQueue {

    /**
     * Processes [sessionIds] ONE AT A TIME, in list order — deliberately never in parallel
     * (multiple concurrent MediaPipe videos is explicitly out of scope for this POC; predictable
     * sequential processing is preferred). Before starting each item, checks [isCancelled] — if
     * true, marks that item and every remaining not-yet-started item CANCELLED (via
     * [onItemStatusChanged]) and returns immediately without processing them. If [processOne]
     * throws for one item, that item is marked FAILED (with the exception's message, or "Unknown
     * error" if the message is null) via [onItemStatusChanged], and the loop CONTINUES to the next
     * item — a single failed/corrupt clip must never abort the rest of the batch. [onProgress] is
     * called after every item finishes (success, failure, or cancellation) with (completedCount,
     * totalCount).
     */
    suspend fun run(
        sessionIds: List<String>,
        isCancelled: () -> Boolean,
        onItemStatusChanged: (validationSessionId: String, status: ClipBatchStatus, errorMessage: String?) -> Unit,
        onProgress: (completed: Int, total: Int) -> Unit,
        processOne: suspend (validationSessionId: String, onStageChanged: (ClipBatchStatus) -> Unit) -> Unit,
    ) {
        val total = sessionIds.size
        for ((index, sessionId) in sessionIds.withIndex()) {
            if (isCancelled()) {
                for (remainingId in sessionIds.subList(index, sessionIds.size)) {
                    onItemStatusChanged(remainingId, ClipBatchStatus.CANCELLED, null)
                }
                onProgress(index, total)
                return
            }

            try {
                processOne(sessionId) { stage -> onItemStatusChanged(sessionId, stage, null) }
                onItemStatusChanged(sessionId, ClipBatchStatus.COMPLETE, null)
            } catch (e: Exception) {
                onItemStatusChanged(sessionId, ClipBatchStatus.FAILED, e.message ?: "Unknown error")
            }
            onProgress(index + 1, total)
        }
    }
}
