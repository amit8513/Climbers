package com.example.climb.ui.clubs.routeregistration

import com.example.climb.clubs.HoldRole
import com.example.climb.colordetection.ReviewedHold

/**
 * Pure hold-role transitions for the draft review/start/finish-selection steps — at most one
 * START and one FINISH hold at a time, matching the future `StartHoldMatcher`'s hard-gate
 * expectations (a route with two "start" holds would be ambiguous to that algorithm).
 */
object RouteRegistrationHoldSelection {

    /** Marks [holdId] as [HoldRole.START], demoting any *other* previously-START hold back to
     * [HoldRole.BODY] — never two START holds at once. */
    fun selectStartHold(holds: List<ReviewedHold>, holdId: Int): List<ReviewedHold> =
        holds.map { hold ->
            when {
                hold.id == holdId -> hold.copy(role = HoldRole.START)
                hold.role == HoldRole.START -> hold.copy(role = HoldRole.BODY)
                else -> hold
            }
        }

    /** Same shape as [selectStartHold], for [HoldRole.FINISH]. */
    fun selectFinishHold(holds: List<ReviewedHold>, holdId: Int): List<ReviewedHold> =
        holds.map { hold ->
            when {
                hold.id == holdId -> hold.copy(role = HoldRole.FINISH)
                hold.role == HoldRole.FINISH -> hold.copy(role = HoldRole.BODY)
                else -> hold
            }
        }

    /** Free-form role correction for the hold-review step — unlike [selectStartHold]/
     * [selectFinishHold], does not demote any other hold, so staff can deliberately create a
     * transient multi-START state mid-correction if they choose; [RouteVersionSnapshotValidator]
     * validation happens later, at save time, not here. */
    fun setRole(holds: List<ReviewedHold>, holdId: Int, role: HoldRole): List<ReviewedHold> =
        holds.map { hold -> if (hold.id == holdId) hold.copy(role = role) else hold }

    /** Drops a false-positive detection entirely. */
    fun removeHold(holds: List<ReviewedHold>, holdId: Int): List<ReviewedHold> =
        holds.filterNot { it.id == holdId }
}
