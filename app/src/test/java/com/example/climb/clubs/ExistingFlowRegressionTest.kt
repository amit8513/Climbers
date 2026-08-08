package com.example.climb.clubs

import com.example.climb.analysis.ClimbAttemptEntity
import com.example.climb.analysis.Visibility
import com.example.climb.analysis.WallType
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.RouteColor
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression scenarios #2, #3, #6, #8 from the Clubs spec: a normal user uploading/recording a
 * video, or opening a video logged before the Clubs feature existed, never needs an organization,
 * venue, zone, route, or route version — every one of those columns must default to null.
 */
class ExistingFlowRegressionTest {

    @Test
    fun `regression 2, 6, 8 - a climb logged with no gym selection has every club field null`() {
        val climb = ClimbEntity(
            userId = "u1",
            videoPath = "path",
            createdAt = 0L,
            durationMs = 0L,
            vGrade = 4,
            routeColor = RouteColor.RED,
            outcome = ClimbOutcome.SENT,
            notes = "",
        )
        assertNull(climb.organizationId)
        assertNull(climb.venueId)
        assertNull(climb.zoneId)
        assertNull(climb.routeId)
        assertNull(climb.routeVersionId)
    }

    @Test
    fun `regression 3, 4 - an analysis attempt recorded with no route context has every club field null`() {
        val attempt = ClimbAttemptEntity(
            userId = "u1",
            videoPath = "path",
            createdAt = 0L,
            durationMs = 0L,
            vGrade = null,
            wallType = WallType.VERTICAL,
            attemptNumber = 1,
            completed = true,
            flash = false,
            routeName = null,
            gymName = null,
            notes = "",
            visibility = Visibility.PRIVATE,
        )
        assertNull(attempt.organizationId)
        assertNull(attempt.venueId)
        assertNull(attempt.zoneId)
        assertNull(attempt.routeId)
        assertNull(attempt.routeVersionId)
    }
}
