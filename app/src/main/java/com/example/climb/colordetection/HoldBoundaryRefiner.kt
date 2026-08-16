package com.example.climb.colordetection

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Phase 4's entry point: edge-aware boundary refinement over Phase 3's [HoldComponentDetector]
 * output. Per hold: bounded, ring-by-ring region growing recovers chalk/shadow/specular-highlight
 * pixels that failed Phase 3's strict per-pixel color gate but are legitimately part of the same
 * physical hold. Growth is excluded from crossing into a neighboring, hue-distinct hold (the
 * CIEDE2000-to-median-color gate plus the edge gate reject it), but is NOT guaranteed to stay off a
 * flat, low-saturation wall of similar lightness to the hold — the achromatic-bridge exception that
 * recovers chalk/highlights admits that wall too, so wall-adjacent holds get a small bounded "halo"
 * into the background by design tradeoff, capped by [RouteColorDetectionConfig.MAX_GROWTH_RADIUS_PX] (see this phase's
 * reported limitations). Grown masks that end up touching (4-connectivity — a genuine shared edge,
 * not just a diagonal graze, matching [ConnectedComponents]' own false-merge-avoidance reasoning)
 * are merged — this is the concrete fix for Phase 3's documented cutting-band fragmentation gap.
 * Finally, each surviving hold's outer boundary is traced via [MooreBoundaryTracer] and populated
 * into [DetectedHold.contour].
 *
 * Deliberately does NOT reach for OpenCV/GrabCut — see this phase's design notes: there is no pure
 * -Kotlin GrabCut, and OpenCV's Java bindings require `System.loadLibrary`, which this project's
 * plain JVM unit tests (no Robolectric anywhere, per Phases 2-3) cannot load. Bounded edge-aware
 * region growing is a locally-scoped, per-hold problem — cheap, pure-Kotlin, and fully unit
 * -testable — and handles this phase's actual requirement without GrabCut's global bi-label energy
 * minimization over the whole frame.
 */
object HoldBoundaryRefiner {

    /**
     * @return one refined [DetectedHold] per final (possibly merged) object, ids renumbered
     * sequentially `0..n-1` in arbitrary order (post-merge, Phase 3's original ids may have gaps
     * or be many-to-one) — each with [DetectedHold.contour] populated and its mask/stats
     * recomputed from the final, possibly-grown-and-merged pixel membership.
     */
    fun refineBoundaries(
        buffer: PixelBuffer,
        targetModel: TargetColorModel,
        candidates: List<DetectedHold>,
    ): List<DetectedHold> {
        if (candidates.isEmpty()) return emptyList()

        val growthResults = candidates.map { growHold(buffer, it) }

        val mergedGroups = mergeTouchingGroups(growthResults, buffer.width)

        val unionedIndicesPerGroup = mergedGroups.map { group ->
            val unionIndices = ArrayList<Int>()
            val seen = HashSet<Int>()
            for (memberIndex in group) {
                for (i in growthResults[memberIndex].grownGlobalIndices) {
                    if (seen.add(i)) unionIndices += i
                }
            }
            unionIndices
        }

        // Ids renumbered sequentially 0..n-1 only over groups that actually produced a hold (in
        // practice every group does, since every input hold's own mask is non-empty) — no gaps.
        return unionedIndicesPerGroup
            .mapNotNull { HoldComponentDetector.statsFromMemberIndices(buffer, it, targetModel, id = 0) }
            .mapIndexed { index, hold ->
                val contourLocal = MooreBoundaryTracer.traceOuterContour(hold.mask, hold.boundingBox.width, hold.boundingBox.height)
                val contourGlobal = contourLocal.map { point ->
                    Centroid(point.x + hold.boundingBox.x0, point.y + hold.boundingBox.y0)
                }
                hold.copy(id = index, contour = contourGlobal)
            }
    }

    // --- Per-hold bounded, edge-aware region growing ---

    private class GrowthResult(val grownGlobalIndices: Set<Int>)

    private fun growHold(buffer: PixelBuffer, hold: DetectedHold): GrowthResult {
        val bbox = hold.boundingBox
        val growthRadiusPx = growthRadiusPxFor(bbox)

        val windowX0 = max(0, bbox.x0 - growthRadiusPx)
        val windowY0 = max(0, bbox.y0 - growthRadiusPx)
        val windowX1 = min(buffer.width - 1, bbox.x1 + growthRadiusPx)
        val windowY1 = min(buffer.height - 1, bbox.y1 + growthRadiusPx)
        val windowWidth = windowX1 - windowX0 + 1
        val windowHeight = windowY1 - windowY0 + 1

        fun windowIndex(globalX: Int, globalY: Int) = (globalY - windowY0) * windowWidth + (globalX - windowX0)

        val labField = arrayOfNulls<LabColor>(windowWidth * windowHeight)
        val hsvField = arrayOfNulls<HsvColor>(windowWidth * windowHeight)
        val labL = DoubleArray(windowWidth * windowHeight)
        for (y in windowY0..windowY1) {
            for (x in windowX0..windowX1) {
                val rgb = buffer.rgbAt(x, y)
                val lab = ColorSpace.rgbToLab(rgb)
                val hsv = ColorSpace.rgbToHsv(rgb)
                val idx = windowIndex(x, y)
                labField[idx] = lab
                hsvField[idx] = hsv
                labL[idx] = lab.l
            }
        }
        val gradientMagnitude = SobelEdgeDetector.gradientMagnitudeField(labL, windowWidth, windowHeight)

        // grown[] = window-local membership (starts as the hold's existing mask, translated into
        // window coordinates); visited[] = grown OR already-tested-and-rejected, so a rejected
        // pixel is never re-proposed by a later ring (deterministic admission test, see class doc).
        val grown = BooleanArray(windowWidth * windowHeight)
        val visited = BooleanArray(windowWidth * windowHeight)
        var frontier = HashSet<Int>()
        for (localY in 0 until bbox.height) {
            for (localX in 0 until bbox.width) {
                if (!hold.mask[localY * bbox.width + localX]) continue
                val globalX = bbox.x0 + localX
                val globalY = bbox.y0 + localY
                val idx = windowIndex(globalX, globalY)
                grown[idx] = true
                visited[idx] = true
            }
        }
        // Seed the initial frontier: 4-neighbors of the existing mask, inside the window, not yet visited.
        for (y in windowY0..windowY1) {
            for (x in windowX0..windowX1) {
                val idx = windowIndex(x, y)
                if (!grown[idx]) continue
                for ((nx, ny) in fourNeighbors(x, y)) {
                    if (nx < windowX0 || nx > windowX1 || ny < windowY0 || ny > windowY1) continue
                    val nIdx = windowIndex(nx, ny)
                    if (!visited[nIdx]) {
                        visited[nIdx] = true
                        frontier.add(nIdx)
                    }
                }
            }
        }

        var ring = 0
        while (ring < growthRadiusPx && frontier.isNotEmpty()) {
            ring++
            val nextFrontier = HashSet<Int>()
            var admittedCount = 0
            for (idx in frontier) {
                val pixelLab = labField[idx]!!
                val pixelHsv = hsvField[idx]!!
                val edgeOk = gradientMagnitude[idx] < RouteColorDetectionConfig.EDGE_GRADIENT_MAGNITUDE_THRESHOLD
                val colorOk = Ciede2000DistanceMetric.distance(pixelLab, hold.medianLab) <=
                    RouteColorDetectionConfig.LOOSE_DELTA_E_THRESHOLD ||
                    pixelHsv.s <= RouteColorDetectionConfig.ACHROMATIC_SATURATION_CEILING
                if (edgeOk && colorOk) {
                    grown[idx] = true
                    admittedCount++
                    val x = windowX0 + idx % windowWidth
                    val y = windowY0 + idx / windowWidth
                    for ((nx, ny) in fourNeighbors(x, y)) {
                        if (nx < windowX0 || nx > windowX1 || ny < windowY0 || ny > windowY1) continue
                        val nIdx = windowIndex(nx, ny)
                        if (!visited[nIdx]) {
                            visited[nIdx] = true
                            nextFrontier.add(nIdx)
                        }
                    }
                }
                // Rejected pixels stay `visited` (already marked) but never propagate — that
                // growth branch stops, per this phase's region-growing rule.
            }
            if (admittedCount == 0) break // a ring adding zero new pixels stops growth early
            frontier = nextFrontier
        }

        val grownGlobalIndices = HashSet<Int>()
        for (y in windowY0..windowY1) {
            for (x in windowX0..windowX1) {
                if (grown[windowIndex(x, y)]) grownGlobalIndices.add(buffer.indexOf(x, y))
            }
        }
        return GrowthResult(grownGlobalIndices)
    }

    private fun fourNeighbors(x: Int, y: Int): List<Pair<Int, Int>> = listOf(
        x - 1 to y,
        x + 1 to y,
        x to y - 1,
        x to y + 1,
    )

    /** growthRadiusPx = clamp(round(GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT * min(width, height)),
     * MIN_GROWTH_RADIUS_PX, MAX_GROWTH_RADIUS_PX) — bounded proportionally to the hold's own
     * on-screen size, per this phase's region-growing rule. */
    private fun growthRadiusPxFor(bbox: BoundingBox): Int {
        val extent = min(bbox.width, bbox.height)
        val raw = round(RouteColorDetectionConfig.GROWTH_RADIUS_FRACTION_OF_HOLD_EXTENT * extent).toInt()
        return raw.coerceIn(RouteColorDetectionConfig.MIN_GROWTH_RADIUS_PX, RouteColorDetectionConfig.MAX_GROWTH_RADIUS_PX)
    }

    // --- Union-find merge of touching grown masks ---

    /**
     * @return groups of indices into [growthResults] (each group = one final merged hold),
     * covering every input index exactly once. Uses a bounding-box-overlap-or-adjacency prefilter
     * before any exact pixel-level touch check, to avoid O(n^2) full mask comparisons across a
     * whole frame's holds.
     */
    private fun mergeTouchingGroups(growthResults: List<GrowthResult>, bufferWidth: Int): List<List<Int>> {
        val n = growthResults.size
        val parent = IntArray(n) { it }

        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val bboxes = growthResults.map { boundingBoxOf(it.grownGlobalIndices, bufferWidth) }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (find(i) == find(j)) continue
                val bi = bboxes[i] ?: continue
                val bj = bboxes[j] ?: continue
                if (!expandedBoxesOverlap(bi, bj)) continue
                if (touches4Connected(growthResults[i].grownGlobalIndices, growthResults[j].grownGlobalIndices, bufferWidth)) {
                    union(i, j)
                }
            }
        }

        val groups = LinkedHashMap<Int, MutableList<Int>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { ArrayList() }.add(i)
        }
        return groups.values.toList()
    }

    private data class SimpleBox(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

    private fun boundingBoxOf(globalIndices: Set<Int>, bufferWidth: Int): SimpleBox? {
        if (globalIndices.isEmpty()) return null
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (i in globalIndices) {
            val x = i % bufferWidth
            val y = i / bufferWidth
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return SimpleBox(minX, minY, maxX, maxY)
    }

    /** Expanded by 1px on each side so a prefilter pass never rejects a pair that could still
     * touch at an edge (4-connectivity) at their nearest border. Slightly more permissive than the
     * exact 4-connected check strictly needs (it would also admit a purely-diagonal pair through),
     * which is fine for a prefilter — [touches4Connected] is the actual gate. */
    private fun expandedBoxesOverlap(a: SimpleBox, b: SimpleBox): Boolean {
        return a.minX - 1 <= b.maxX && a.maxX + 1 >= b.minX &&
            a.minY - 1 <= b.maxY && a.maxY + 1 >= b.minY
    }

    /** Exact 4-connectivity touch test: true iff some pixel in [a] and some pixel in [b] are equal
     * or share an edge (up/down/left/right — no diagonals). 4-, not 8-, connectivity specifically
     * for this merge decision: an 8-connected "touch" allows two grown masks to meet at a single
     * diagonal pixel pair with no shared edge, which [MooreBoundaryTracer] then traces as a
     * self-touching, non-simple polygon (a repeated vertex at the pinch point) — a real, reachable
     * bug caught during Phase 4 review. Requiring a genuine shared edge before merging, matching
     * [ConnectedComponents]' own false-merge-avoidance policy for the same reason, prevents that
     * pinched topology from ever being constructed. */
    internal fun touches4Connected(a: Set<Int>, b: Set<Int>, bufferWidth: Int): Boolean {
        val (smaller, larger) = if (a.size <= b.size) a to b else b to a
        for (i in smaller) {
            val x = i % bufferWidth
            val y = i / bufferWidth
            if (i in larger) return true
            for ((nx, ny) in fourNeighbors(x, y)) {
                if (nx < 0 || nx >= bufferWidth || ny < 0) continue
                if ((ny * bufferWidth + nx) in larger) return true
            }
        }
        return false
    }
}
