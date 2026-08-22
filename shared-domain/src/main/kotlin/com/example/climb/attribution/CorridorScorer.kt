package com.example.climb.attribution

import com.example.climb.analysis.contact.ContactEventType
import com.example.climb.analysis.contact.HoldContactTimeline
import com.example.climb.analysis.contact.HoldShape
import com.example.climb.colordetection.NormalizedRect
import com.example.climb.colordetection.Point2D

/**
 * Corridor scoring measures, of the holds this candidate's own contacts actually touched, what
 * fraction were within the wall's plausible corridor for this route — a sanity/plausibility
 * signal, not a coverage signal (that's `ContactCoverageScorer`'s job: how much of the ROUTE got
 * touched). A candidate whose climber stayed entirely within the expected wall corridor scores
 * `1f` here regardless of how few or many of the route's holds were actually covered.
 *
 * [RouteCandidate.corridorNormalized] is independently optional (see that field's doc comment) —
 * `null` means "this route has no corridor defined yet," which is structurally different from "the
 * climber's contacts scored zero against a real corridor," so [score] returns `null` rather than a
 * low score in that case, letting the engine renormalize [RouteAttributionScoringConfig.corridorWeight]
 * away entirely instead of unfairly penalizing a route that simply hasn't had this evidence
 * populated yet.
 */
object CorridorScorer {

    fun score(candidate: RouteCandidate, timeline: HoldContactTimeline, holds: List<HoldShape>): Float? {
        val corridor = candidate.corridorNormalized ?: return null

        val holdsById = holds.associateBy { it.holdId }
        val establishedHoldIds = candidate.allHoldIds.filter { holdId ->
            timeline.events.any { it.type == ContactEventType.ESTABLISHED && it.holdId == holdId }
        }
        if (establishedHoldIds.isEmpty()) return 0f

        val withinCorridorCount = establishedHoldIds.count { holdId ->
            val hold = holdsById[holdId] ?: return@count false
            corridor.contains(centroidOf(hold))
        }

        return withinCorridorCount.toFloat() / establishedHoldIds.size
    }

    /** A plain arithmetic mean of a hold's contour vertices — a simple approximation, not a true
     * polygon-area centroid (same honesty standard the codebase already uses elsewhere for
     * POC-level approximations). */
    private fun centroidOf(hold: HoldShape): Point2D {
        val vertices = hold.contourNormalized
        val meanX = vertices.sumOf { it.x.toDouble() } / vertices.size
        val meanY = vertices.sumOf { it.y.toDouble() } / vertices.size
        return Point2D(x = meanX.toFloat(), y = meanY.toFloat())
    }

    private fun NormalizedRect.contains(point: Point2D): Boolean =
        point.x in left..right && point.y in top..bottom
}
