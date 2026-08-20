package com.example.climb

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.climb.analysis.Visibility
import com.example.climb.clubs.AttemptSource
import com.example.climb.data.ClimbDao
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.data.RouteColor
import com.example.climb.ui.tag.TagScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated Compose UI tests for `TagScreen` (the record -> tag -> save confirm screen), covering:
 *  - Save is gated on a required field (route color) and nothing else.
 *  - Tapping a grade/outcome chip actually updates the composable's own selection state
 *    (including grade's deselect-on-second-tap toggle).
 *  - Tapping Save eventually calls through to the repository with the exact data selected, and
 *    then invokes [onSaved].
 *
 * `ClimbRepository` is a concrete class over the `ClimbDao` *interface*, so — same approach as
 * `PoseAnalysisRetryTest` — this wraps a tiny in-memory [FakeClimbDao] in a real `ClimbRepository`
 * rather than standing up Room. `durationMs = 0` is used throughout so `TrimControl` (which needs a
 * real, playable video file behind `videoPath`) never renders; the trim feature itself is
 * deliberately out of scope for these tests since it requires an ExoPlayer decoding a real file, and
 * TagScreen's own comment on this codepath documents the same `durationMs <= 0` skip used by real
 * imports with unreadable metadata.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class TagScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(dao: FakeClimbDao, onSaved: () -> Unit = {}) {
        composeTestRule.setContent {
            TagScreen(
                videoPath = "/fake/video.mp4",
                durationMs = 0L,
                repository = ClimbRepository(dao),
                currentUid = "test-uid",
                currentUsername = "tester",
                attemptSource = AttemptSource.PHONE_CAMERA,
                onSaved = onSaved,
            )
        }
    }

    @Test
    fun saveIsDisabledUntilARouteColorIsChosen() {
        setContent(FakeClimbDao())

        composeTestRule.onNodeWithText("Save climb").assertIsNotEnabled()

        // Picking a grade or an outcome alone must NOT be enough — routeColor is the only
        // required field per TagScreen's own `enabled = routeColor != null && !saving`.
        composeTestRule.onNodeWithText("V5").performClick()
        composeTestRule.onNodeWithText("FELL").performClick()
        composeTestRule.onNodeWithText("Save climb").assertIsNotEnabled()

        // The color swatches carry no text of their own — RouteColor.entries' first swatch (RED)
        // is the first clickable node in the whole screen at this point, so index [0] is
        // deterministic. Selecting it is Save's only remaining gate.
        composeTestRule.onAllNodes(hasClickAction())[0].performClick()
        composeTestRule.onNodeWithText("Save climb").assertIsEnabled()
    }

    @Test
    fun selectingGradeTogglesItOnAndOffAgain() {
        setContent(FakeClimbDao())

        composeTestRule.onNodeWithText("V5").assertIsNotSelected()
        composeTestRule.onNodeWithText("V5").performClick()
        composeTestRule.onNodeWithText("V5").assertIsSelected()

        // Tapping the same grade again deselects it (TagScreen: `if (vGrade == grade) null else grade`).
        composeTestRule.onNodeWithText("V5").performClick()
        composeTestRule.onNodeWithText("V5").assertIsNotSelected()
    }

    @Test
    fun selectingOutcomeUpdatesSelectionExclusively() {
        setContent(FakeClimbDao())

        // SENT is the initial default.
        composeTestRule.onNodeWithText("SENT").assertIsSelected()
        composeTestRule.onNodeWithText("FELL").assertIsNotSelected()

        composeTestRule.onNodeWithText("FELL").performClick()

        composeTestRule.onNodeWithText("FELL").assertIsSelected()
        composeTestRule.onNodeWithText("SENT").assertIsNotSelected()
    }

    @Test
    fun selectingRouteColor_enablesSave_andShowsItsCheckmark() {
        setContent(FakeClimbDao())

        composeTestRule.onNodeWithText("✓").assertDoesNotExist()

        // RouteColor.entries' first swatch (RED) — the Row of swatches has no other clickable
        // Compose nodes ahead of it, so the first `hasClickAction` match here is deterministic.
        composeTestRule.onAllNodes(hasClickAction())[0].performClick()

        composeTestRule.onNodeWithText("✓").assertExists()
        composeTestRule.onNodeWithText("Save climb").assertIsEnabled()
    }

    @Test
    fun tappingSave_callsRepositoryWithSelectedData_andInvokesOnSaved() {
        val dao = FakeClimbDao()
        var savedCallbackFired = false
        setContent(dao, onSaved = { savedCallbackFired = true })

        // Select the required color (first swatch = RED), a grade, an outcome, notes, and
        // visibility, then save.
        composeTestRule.onAllNodes(hasClickAction())[0].performClick()
        // V7 is the 8th chip in Grade's LazyRow and isn't necessarily composed within the initial
        // viewport, so it must be scrolled into view before it has a semantics node to click.
        composeTestRule.onNodeWithTag("gradeRow").performScrollToNode(hasText("V7"))
        composeTestRule.onNodeWithText("V7").performClick()
        composeTestRule.onNodeWithText("FELL").performClick()
        composeTestRule.onNodeWithText("Notes").performTextInput("Crimpy top-out")
        composeTestRule.onNodeWithText("Friends only").performClick()

        composeTestRule.onNodeWithText("Save climb").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { dao.inserted.isNotEmpty() }

        val saved = dao.inserted.single()
        assertEquals(RouteColor.RED, saved.routeColor)
        assertEquals(7, saved.vGrade)
        assertEquals(ClimbOutcome.FELL, saved.outcome)
        assertEquals("Crimpy top-out", saved.notes)
        assertEquals(Visibility.FRIENDS_ONLY, saved.visibility)
        assertEquals("test-uid", saved.userId)
        // durationMs = 0 means the "did the user trim?" branch is never taken, so the untouched
        // input path (videoPath/durationMs) is exactly what should reach the repository.
        assertEquals("/fake/video.mp4", saved.videoPath)
        assertEquals(0L, saved.durationMs)

        composeTestRule.waitUntil(timeoutMillis = 5_000) { savedCallbackFired }
        // Sanity check: TagScreen's save path only ever calls insert() (a brand-new climb) — an
        // accidental update() call here would mean a real bug in what gets built and passed down.
        assertNull(dao.updated)
    }
}

/**
 * Minimal in-memory [ClimbDao] fake. `insert` records every [ClimbEntity] it's handed (with the
 * autogenerated-id semantics real Room would apply, i.e. an incrementing id) so a test can assert
 * on exactly what `TagScreen` built and passed to the repository, without a real SQLite database.
 */
private class FakeClimbDao : ClimbDao {
    val inserted = mutableListOf<ClimbEntity>()
    var updated: ClimbEntity? = null
    private var nextId = 1L

    override suspend fun insert(climb: ClimbEntity): Long {
        val withId = climb.copy(id = nextId++)
        inserted += withId
        return withId.id
    }

    override suspend fun update(climb: ClimbEntity) {
        updated = climb
    }

    override suspend fun delete(climb: ClimbEntity) {}

    override fun observeAll(userId: String): Flow<List<ClimbEntity>> = MutableStateFlow(emptyList())

    override fun observeById(id: Long, userId: String): Flow<ClimbEntity?> = MutableStateFlow(null)
}
