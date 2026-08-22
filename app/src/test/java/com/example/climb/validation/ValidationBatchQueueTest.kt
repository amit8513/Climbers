package com.example.climb.validation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationBatchQueueTest {

    /** One recorded call to `onItemStatusChanged`, in the order it happened. */
    private data class StatusChange(val validationSessionId: String, val status: ClipBatchStatus, val errorMessage: String?)

    @Test
    fun `all items succeed, every item ends COMPLETE and onProgress reports counts in order`() = runBlocking {
        val ids = listOf("a", "b", "c")
        val statusChanges = mutableListOf<StatusChange>()
        val progressCalls = mutableListOf<Pair<Int, Int>>()
        val processedIds = mutableListOf<String>()

        ValidationBatchQueue.run(
            sessionIds = ids,
            isCancelled = { false },
            onItemStatusChanged = { id, status, error -> statusChanges += StatusChange(id, status, error) },
            onProgress = { completed, total -> progressCalls += completed to total },
            processOne = { id, _ -> processedIds += id },
        )

        assertEquals(listOf("a", "b", "c"), processedIds)
        assertEquals(
            listOf(
                StatusChange("a", ClipBatchStatus.COMPLETE, null),
                StatusChange("b", ClipBatchStatus.COMPLETE, null),
                StatusChange("c", ClipBatchStatus.COMPLETE, null),
            ),
            statusChanges,
        )
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progressCalls)
    }

    @Test
    fun `one item throwing in the middle is marked FAILED without aborting items before or after it`() = runBlocking {
        val ids = listOf("a", "b", "c")
        val statusChanges = mutableListOf<StatusChange>()
        val progressCalls = mutableListOf<Pair<Int, Int>>()

        ValidationBatchQueue.run(
            sessionIds = ids,
            isCancelled = { false },
            onItemStatusChanged = { id, status, error -> statusChanges += StatusChange(id, status, error) },
            onProgress = { completed, total -> progressCalls += completed to total },
            processOne = { id, _ -> if (id == "b") throw IllegalStateException("boom") },
        )

        assertEquals(
            listOf(
                StatusChange("a", ClipBatchStatus.COMPLETE, null),
                StatusChange("b", ClipBatchStatus.FAILED, "boom"),
                StatusChange("c", ClipBatchStatus.COMPLETE, null),
            ),
            statusChanges,
        )
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progressCalls)
    }

    @Test
    fun `a thrown exception with no message is reported as Unknown error`() = runBlocking {
        val statusChanges = mutableListOf<StatusChange>()

        ValidationBatchQueue.run(
            sessionIds = listOf("a"),
            isCancelled = { false },
            onItemStatusChanged = { id, status, error -> statusChanges += StatusChange(id, status, error) },
            onProgress = { _, _ -> },
            processOne = { _, _ -> throw IllegalStateException() },
        )

        assertEquals(listOf(StatusChange("a", ClipBatchStatus.FAILED, "Unknown error")), statusChanges)
    }

    @Test
    fun `cancellation signaled before any item starts cancels every item without invoking processOne`() = runBlocking {
        val ids = listOf("a", "b", "c")
        val statusChanges = mutableListOf<StatusChange>()
        val progressCalls = mutableListOf<Pair<Int, Int>>()
        val processedIds = mutableListOf<String>()

        ValidationBatchQueue.run(
            sessionIds = ids,
            isCancelled = { true },
            onItemStatusChanged = { id, status, error -> statusChanges += StatusChange(id, status, error) },
            onProgress = { completed, total -> progressCalls += completed to total },
            processOne = { id, _ -> processedIds += id },
        )

        assertTrue("processOne must never be invoked once cancelled", processedIds.isEmpty())
        assertEquals(
            listOf(
                StatusChange("a", ClipBatchStatus.CANCELLED, null),
                StatusChange("b", ClipBatchStatus.CANCELLED, null),
                StatusChange("c", ClipBatchStatus.CANCELLED, null),
            ),
            statusChanges,
        )
        assertEquals(listOf(0 to 3), progressCalls)
    }

    @Test
    fun `cancellation signaled partway through keeps completed items COMPLETE and cancels the rest`() = runBlocking {
        val ids = listOf("a", "b", "c")
        var isCancelledCallCount = 0
        val statusChanges = mutableListOf<StatusChange>()
        val progressCalls = mutableListOf<Pair<Int, Int>>()
        val processedIds = mutableListOf<String>()

        ValidationBatchQueue.run(
            sessionIds = ids,
            isCancelled = {
                isCancelledCallCount++
                isCancelledCallCount > 1 // false for the first check ("a"), true from then on
            },
            onItemStatusChanged = { id, status, error -> statusChanges += StatusChange(id, status, error) },
            onProgress = { completed, total -> progressCalls += completed to total },
            processOne = { id, _ -> processedIds += id },
        )

        assertEquals(listOf("a"), processedIds)
        assertEquals(
            listOf(
                StatusChange("a", ClipBatchStatus.COMPLETE, null),
                StatusChange("b", ClipBatchStatus.CANCELLED, null),
                StatusChange("c", ClipBatchStatus.CANCELLED, null),
            ),
            statusChanges,
        )
        assertEquals(listOf(1 to 3, 1 to 3), progressCalls)
    }

    @Test
    fun `onStageChanged from processOne is forwarded through onItemStatusChanged before the final COMPLETE`() = runBlocking {
        val statusChanges = mutableListOf<StatusChange>()

        ValidationBatchQueue.run(
            sessionIds = listOf("a"),
            isCancelled = { false },
            onItemStatusChanged = { id, status, error -> statusChanges += StatusChange(id, status, error) },
            onProgress = { _, _ -> },
            processOne = { _, onStageChanged ->
                onStageChanged(ClipBatchStatus.EXTRACTING_POSE)
                onStageChanged(ClipBatchStatus.CONTACT_ANALYSIS)
                onStageChanged(ClipBatchStatus.ATTRIBUTION)
            },
        )

        assertEquals(
            listOf(
                StatusChange("a", ClipBatchStatus.EXTRACTING_POSE, null),
                StatusChange("a", ClipBatchStatus.CONTACT_ANALYSIS, null),
                StatusChange("a", ClipBatchStatus.ATTRIBUTION, null),
                StatusChange("a", ClipBatchStatus.COMPLETE, null),
            ),
            statusChanges,
        )
    }
}
