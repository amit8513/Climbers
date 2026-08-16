package com.example.climb.colordetection

/**
 * Moore-neighbor boundary tracing (8-connected walk, Jacob's stopping criterion) over a single
 * connected blob in a `width x height` [BooleanArray] mask — a pure, general-purpose, mask-in/
 * points-out utility with no [PixelBuffer]/[DetectedHold] dependency (translation from mask-local
 * to frame-global coordinates is [HoldBoundaryRefiner]'s job, not this tracer's).
 *
 * Explicitly out of scope: inner-hole contours. A mask with an enclosed non-member hole (e.g. a
 * leftover un-bridged chalk fleck) traces only its outer polygon — this phase is "boundary
 * refinement," not "full topology."
 */
object MooreBoundaryTracer {

    // 8 compass directions in clockwise order (image coordinates, y increases downward), each 45°
    // clockwise from the previous: N, NE, E, SE, S, SW, W, NW.
    private val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private const val WEST_INDEX = 6

    /**
     * @return boundary pixel-center points ([Centroid] with `x + 0.5, y + 0.5`), in mask-local
     * coordinates, in walk order, with no explicit repeat of the start point. Behavior is
     * undefined if [mask] is empty (all `false`) or spans more than one connected blob — callers
     * are expected to pass a single already-connected component's mask (matching how
     * [HoldBoundaryRefiner] uses this, post-growth/merge, on one hold at a time).
     */
    fun traceOuterContour(mask: BooleanArray, width: Int, height: Int): List<Centroid> {
        require(mask.size == width * height) {
            "mask.size (${mask.size}) must equal width*height (${width * height})"
        }

        fun isForeground(x: Int, y: Int): Boolean {
            if (x < 0 || x >= width || y < 0 || y >= height) return false
            return mask[y * width + x]
        }

        val startIndex = mask.indices.firstOrNull { mask[it] } ?: return emptyList()
        val startX = startIndex % width
        val startY = startIndex / width

        // Special case: a lone pixel has no true 8-neighbor, so the general walk would find
        // nothing and immediately terminate anyway — handled explicitly for clarity/robustness.
        val hasAnyNeighbor = (0 until 8).any { isForeground(startX + DX[it], startY + DY[it]) }
        if (!hasAnyNeighbor) {
            return listOf(Centroid(startX + 0.5, startY + 0.5))
        }

        fun directionIndexFromTo(fromX: Int, fromY: Int, toX: Int, toY: Int): Int {
            val dx = toX - fromX
            val dy = toY - fromY
            for (i in 0 until 8) {
                if (DX[i] == dx && DY[i] == dy) return i
            }
            error("($fromX,$fromY) -> ($toX,$toY) is not an 8-neighbor step")
        }

        // First clockwise foreground neighbor of (x, y), searching starting just after the
        // direction toward (backtrackX, backtrackY) — the one primitive step this whole tracer is
        // built from; used both for the normal walk and for the stop-criterion's one-step lookahead.
        fun searchNeighbor(x: Int, y: Int, backtrackX: Int, backtrackY: Int): Pair<Int, Int>? {
            val dirIdx = directionIndexFromTo(x, y, backtrackX, backtrackY)
            for (step in 1..8) {
                val idx = (dirIdx + step) % 8
                val candidateX = x + DX[idx]
                val candidateY = y + DY[idx]
                if (isForeground(candidateX, candidateY)) return candidateX to candidateY
            }
            return null
        }

        val boundary = ArrayList<Pair<Int, Int>>()
        boundary += startX to startY

        // Backtrack starts as the west neighbor of start — guaranteed background, since start was
        // found as the first true cell in a top-to-bottom, left-to-right scan.
        var backtrackX = startX + DX[WEST_INDEX]
        var backtrackY = startY + DY[WEST_INDEX]
        var currentX = startX
        var currentY = startY

        var firstBoundaryNeighbor: Pair<Int, Int>? = null
        var isFirstIteration = true

        while (true) {
            val found = searchNeighbor(currentX, currentY, backtrackX, backtrackY)
                ?: break // defensive: current pixel has no foreground neighbor at all
            val (foundX, foundY) = found

            if (foundX == startX && foundY == startY && !isFirstIteration) {
                // We're about to step onto the start pixel again. Rather than blindly appending it
                // (which, for a start pixel that sits ON the perimeter, would duplicate it in the
                // output), look one step further ahead: if continuing from start would immediately
                // reproduce the very first outward transition (start -> firstBoundaryNeighbor),
                // the contour has genuinely closed here — Jacob's stopping criterion — so stop
                // without appending the duplicate. Otherwise this is a legitimate mid-walk pass
                // through the start pixel (e.g. a single-pixel-wide spur strictly wider than one
                // blob) and the walk must continue.
                val hypotheticalNext = searchNeighbor(startX, startY, currentX, currentY)
                if (hypotheticalNext != null &&
                    hypotheticalNext.first == firstBoundaryNeighbor!!.first &&
                    hypotheticalNext.second == firstBoundaryNeighbor.second
                ) {
                    break
                }
            }

            if (isFirstIteration) {
                firstBoundaryNeighbor = found
                isFirstIteration = false
            }

            boundary += found
            backtrackX = currentX
            backtrackY = currentY
            currentX = foundX
            currentY = foundY
        }

        return boundary.map { (x, y) -> Centroid(x + 0.5, y + 0.5) }
    }
}
