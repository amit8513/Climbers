package com.example.climb

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.climb.analysis.AnalysisDao
import com.example.climb.analysis.AnalysisRepository
import com.example.climb.analysis.AnalysisStatus
import com.example.climb.analysis.AnalysisStatusSummary
import com.example.climb.analysis.AnalysisTimingSummary
import com.example.climb.analysis.ClimbAnalysisEntity
import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.analysis.Visibility
import com.example.climb.analysis.WallType
import com.example.climb.ui.detail.PoseAnalysisSection
import com.example.climb.ui.detail.PoseAnalysisStatusRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated Compose UI tests for the pose-analysis section of `DetailScreen.kt`, specifically the
 * "Retry" action on a FAILED analysis.
 *
 * Approach: `AnalysisRepository` is a concrete class, but it's a thin wrapper over the `AnalysisDao`
 * *interface* — so instead of standing up a real Room database (or mocking WorkManager), this test
 * builds a tiny in-memory [FakeAnalysisDao] and wraps it in a real `AnalysisRepository`. That gets
 * real repository logic under test with zero Room/SQLite/WorkManager involved.
 *
 * The harder problem was the "Retry" tap itself: the production code calls
 * `WorkManager.getInstance(context).enqueueUniqueWork(...)` directly inside the composable, with no
 * seam to observe from a test. `PoseAnalysisStatusRow` (promoted from `private` to `internal` in
 * `DetailScreen.kt` for exactly this purpose) now takes an `enqueueRetry: (attemptId: Long) -> Unit`
 * parameter that defaults to the real WorkManager call but can be substituted here with a fake that
 * just records the attemptId it was invoked with — so the test observes the retry firing (and with
 * the right id) without touching a real WorkManager/WorkerFactory/Room stack.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class PoseAnalysisRetryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeAttempt(id: Long = 42L) = ClimbAttemptEntity(
        id = id,
        userId = "test-uid",
        sourceClimbId = 7L,
        videoPath = "/fake/path.mp4",
        createdAt = 0L,
        durationMs = 5_000L,
        vGrade = 4,
        wallType = WallType.OVERHANG,
        attemptNumber = 1,
        completed = true,
        flash = false,
        routeName = "Test route",
        gymName = "Test gym",
        notes = "",
        visibility = Visibility.PRIVATE,
    )

    @Test
    fun failedAnalysis_showsRetry_andTappingItEnqueuesRetryForTheRightAttemptAndOpensProgress() {
        val attempt = fakeAttempt(id = 42L)
        val summaryFlow = MutableStateFlow<AnalysisStatusSummary?>(
            AnalysisStatusSummary(id = 9L, status = AnalysisStatus.FAILED, failureReason = "Pose model crashed"),
        )
        val repository = AnalysisRepository(FakeAnalysisDao(latestAnalysis = summaryFlow))

        var retriedAttemptId: Long? = null
        var viewProgressAttemptId: Long? = null

        composeTestRule.setContent {
            PoseAnalysisStatusRow(
                attempt = attempt,
                analysisRepository = repository,
                onViewProgress = { id -> viewProgressAttemptId = id },
                onViewResult = { },
                enqueueRetry = { id -> retriedAttemptId = id },
            )
        }

        // Failure reason is genuinely rendered, and "Retry" is a real, tappable element.
        composeTestRule.onNodeWithText("Analysis failed: Pose model crashed").assertExists()
        composeTestRule.onNodeWithText("Retry").assertExists().performClick()

        composeTestRule.waitForIdle()

        assertEquals("enqueueRetry should fire for this exact attempt", 42L, retriedAttemptId)
        assertEquals("Tapping Retry should navigate to the progress screen, same as a first-time analysis", 42L, viewProgressAttemptId)
    }

    @Test
    fun failedAnalysis_retryIsDisabledAfterFirstTap_soADoubleTapCannotDoubleEnqueue() {
        val attempt = fakeAttempt(id = 1L)
        val summaryFlow = MutableStateFlow<AnalysisStatusSummary?>(
            AnalysisStatusSummary(id = 1L, status = AnalysisStatus.FAILED, failureReason = "boom"),
        )
        val repository = AnalysisRepository(FakeAnalysisDao(latestAnalysis = summaryFlow))
        var enqueueCount = 0

        composeTestRule.setContent {
            PoseAnalysisStatusRow(
                attempt = attempt,
                analysisRepository = repository,
                onViewProgress = { },
                onViewResult = { },
                enqueueRetry = { enqueueCount++ },
            )
        }

        composeTestRule.onNodeWithText("Retry").performClick()
        composeTestRule.waitForIdle()
        // Since the fake repository's flow never emits a new analysis id, isRetrying stays true —
        // the node should now report itself as disabled and a second click should be a no-op.
        composeTestRule.onNodeWithText("Retry").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Retry").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, enqueueCount)
    }

    @Test
    fun completeAnalysis_showsViewAnalysis_andTappingItInvokesOnViewResultWithAnalysisId() {
        val attempt = fakeAttempt(id = 5L)
        val summaryFlow = MutableStateFlow<AnalysisStatusSummary?>(
            AnalysisStatusSummary(id = 77L, status = AnalysisStatus.COMPLETE, failureReason = null),
        )
        val repository = AnalysisRepository(FakeAnalysisDao(latestAnalysis = summaryFlow))
        var viewedAnalysisId: Long? = null

        composeTestRule.setContent {
            PoseAnalysisStatusRow(
                attempt = attempt,
                analysisRepository = repository,
                onViewProgress = { },
                onViewResult = { id -> viewedAnalysisId = id },
                enqueueRetry = { },
            )
        }

        composeTestRule.onNodeWithText("View analysis").performClick()
        assertEquals(77L, viewedAnalysisId)
    }

    @Test
    fun inProgressAnalysis_isTappableAndInvokesOnViewProgress() {
        val attempt = fakeAttempt(id = 3L)
        val summaryFlow = MutableStateFlow<AnalysisStatusSummary?>(
            AnalysisStatusSummary(id = 2L, status = AnalysisStatus.EXTRACTING_FRAMES, failureReason = null),
        )
        val repository = AnalysisRepository(FakeAnalysisDao(latestAnalysis = summaryFlow))
        var viewProgressAttemptId: Long? = null

        composeTestRule.setContent {
            PoseAnalysisStatusRow(
                attempt = attempt,
                analysisRepository = repository,
                onViewProgress = { id -> viewProgressAttemptId = id },
                onViewResult = { },
                enqueueRetry = { },
            )
        }

        composeTestRule.onNodeWithText("Analysis in progress — tap to view").performClick()
        assertEquals(3L, viewProgressAttemptId)
    }

    @Test
    fun noAttemptYet_showsAnalyzePrompt_andTappingInvokesOnStartAnalysisWithRealClimbData() {
        val repository = AnalysisRepository(FakeAnalysisDao(latestAttemptForSourceClimb = MutableStateFlow(null)))
        var startedVideoPath: String? = null
        var startedDurationMs: Long? = null
        var startedClimbId: Long? = null

        composeTestRule.setContent {
            PoseAnalysisSection(
                climbId = 123L,
                videoPath = "/fake/video.mp4",
                durationMs = 9_000L,
                analysisRepository = repository,
                onStartAnalysis = { videoPath, durationMs, sourceClimbId ->
                    startedVideoPath = videoPath
                    startedDurationMs = durationMs
                    startedClimbId = sourceClimbId
                },
                onViewProgress = { },
                onViewResult = { },
            )
        }

        composeTestRule.onNodeWithText("Analyze this climb").performClick()

        assertEquals("/fake/video.mp4", startedVideoPath)
        assertEquals(9_000L, startedDurationMs)
        assertEquals(123L, startedClimbId)
    }
}

/**
 * Minimal in-memory [AnalysisDao] fake. Only [observeLatestAnalysis] and
 * [observeLatestAttemptForSourceClimb] are exercised by `PoseAnalysisSection`/
 * `PoseAnalysisStatusRow`; every other member is a harmless stub since these tests never call it.
 */
private class FakeAnalysisDao(
    private val latestAnalysis: MutableStateFlow<AnalysisStatusSummary?> = MutableStateFlow(null),
    private val latestAttemptForSourceClimb: MutableStateFlow<ClimbAttemptEntity?> = MutableStateFlow(null),
) : AnalysisDao {
    override suspend fun insertAttempt(attempt: ClimbAttemptEntity): Long = 0L
    override suspend fun insertAnalysis(analysis: ClimbAnalysisEntity): Long = 0L
    override suspend fun updateAnalysis(analysis: ClimbAnalysisEntity) {}
    override fun observeAttempt(id: Long): Flow<ClimbAttemptEntity?> = MutableStateFlow(null)
    override suspend fun getAttempt(id: Long): ClimbAttemptEntity? = null
    override fun observeAnalysis(id: Long): Flow<ClimbAnalysisEntity?> = MutableStateFlow(null)
    override suspend fun getAnalysis(id: Long): ClimbAnalysisEntity? = null
    override suspend fun getLatestAnalysis(attemptId: Long): ClimbAnalysisEntity? = null
    override fun observeLatestAttemptForSourceClimb(sourceClimbId: Long): Flow<ClimbAttemptEntity?> = latestAttemptForSourceClimb
    override fun observeLatestAnalysis(attemptId: Long): Flow<AnalysisStatusSummary?> = latestAnalysis
    override suspend fun getPreviousAttemptForRoute(
        userId: String,
        routeName: String,
        excludeAttemptId: Long,
        beforeCreatedAt: Long,
    ): ClimbAttemptEntity? = null
    override fun observeAttemptsForUserAndOrganization(userId: String, organizationId: Long): Flow<List<ClimbAttemptEntity>> =
        MutableStateFlow(emptyList())
    override suspend fun getTimingsForAttempts(attemptIds: List<Long>): List<AnalysisTimingSummary> = emptyList()
}
