package com.example.climb.clubs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression test for a real, previously-confirmed trust bug: a manually recorded/imported video
 * with a manually-picked gym route used to trigger the same official Firestore writes
 * ([ClubRepository.recordClubAttempt]/[ClubRepository.recordRouteAttempt]/
 * [ClubRepository.recordRouteCompletion]) a verified capture would need, with zero distinction
 * between the two — and a manual local video file could upload itself as a club attempt
 * ([ClubRepository.shareAttemptVideo]) merely by having an organizationId/routeId set. Neither
 * [ClubRepository] (a thin Firestore/Storage wrapper, not fakeable without a real backend) nor the
 * Compose screens that used to call these functions are meaningfully unit-testable for behavior,
 * so this asserts the fix structurally: the source files that used to call these functions must
 * never reference them again.
 */
class TrustBoundaryRegressionTest {

    @Test
    fun `manual video screen never calls official club-write functions`() {
        val source = projectSourceFile("app/src/main/java/com/example/climb/ui/analysis/ClimbDetailsInputScreen.kt")
        assertFalse("ClimbDetailsInputScreen must never call recordClubAttempt", source.contains("recordClubAttempt("))
        assertFalse("ClimbDetailsInputScreen must never call recordRouteAttempt", source.contains("recordRouteAttempt("))
        assertFalse("ClimbDetailsInputScreen must never call recordRouteCompletion", source.contains("recordRouteCompletion("))
    }

    @Test
    fun `club attempt video screen never uploads a local file as a club share`() {
        val source = projectSourceFile("app/src/main/java/com/example/climb/ui/clubs/ClubAttemptVideoScreen.kt")
        assertFalse("ClubAttemptVideoScreen must never call shareAttemptVideo", source.contains("shareAttemptVideo("))
    }

    @Test
    fun `personal analysis and route tagging paths are preserved`() {
        val source = projectSourceFile("app/src/main/java/com/example/climb/ui/analysis/ClimbDetailsInputScreen.kt")
        // The personal attempt (with its optional, unverified route tag) must still be created and
        // still enqueue pose analysis — only the official club-aggregate side effects were removed.
        assertTrue("must still create the local ClimbAttemptEntity", source.contains("analysisRepository.createAttempt("))
        assertTrue("must still enqueue pose analysis", source.contains("PoseAnalysisWorker.buildRequest("))
        assertTrue("must still let a user link an optional, personal gym-route tag", source.contains("GymRoutePicker("))
    }

    /** Resolves a repo-relative path regardless of whether the JVM test's working directory is the
     * repo root or the `app` module directory (both occur depending on how Gradle is invoked). */
    private fun projectSourceFile(repoRelativePath: String): String {
        val strippedOfAppPrefix = repoRelativePath.removePrefix("app/")
        val candidates = listOf(
            File(repoRelativePath),
            File(strippedOfAppPrefix),
            File("../$repoRelativePath"),
        )
        val file = candidates.firstOrNull { it.exists() }
        checkNotNull(file) {
            "Could not locate $repoRelativePath from working dir ${File(".").absolutePath}; tried: " +
                candidates.joinToString { it.absolutePath }
        }
        return file.readText()
    }
}
