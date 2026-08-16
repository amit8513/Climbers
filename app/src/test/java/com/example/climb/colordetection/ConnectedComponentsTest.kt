package com.example.climb.colordetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedComponentsTest {

    private fun boundsOf(labeling: ConnectedComponents.Labeling, width: Int, componentId: Int): IntArray {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        var area = 0
        for (i in labeling.labels.indices) {
            if (labeling.labels[i] != componentId) continue
            val x = i % width
            val y = i / width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            area++
        }
        return intArrayOf(minX, minY, maxX, maxY, area)
    }

    @Test
    fun `two separate square regions are labeled as two distinct components`() {
        val width = 10
        val height = 10
        val mask = BooleanArray(width * height)
        for (y in 1..3) for (x in 1..3) mask[y * width + x] = true // 3x3 = 9px
        for (y in 6..8) for (x in 6..8) mask[y * width + x] = true // 3x3 = 9px

        val labeling = ConnectedComponents.label(mask, width, height)
        assertEquals(2, labeling.componentCount)

        val (b0, b1) = listOf(0, 1).map { boundsOf(labeling, width, it) }
        val areas = listOf(b0[4], b1[4]).sorted()
        assertEquals(listOf(9, 9), areas)

        // One component's bbox should be exactly (1,1)-(3,3), the other (6,6)-(8,8).
        val bboxes = listOf(b0, b1).map { it.take(4) }
        assertTrue(bboxes.contains(listOf(1, 1, 3, 3)))
        assertTrue(bboxes.contains(listOf(6, 6, 8, 8)))
    }

    @Test
    fun `single rectangle produces one component with correct bounding box and area`() {
        val width = 12
        val height = 12
        val mask = BooleanArray(width * height)
        for (y in 2..7) for (x in 3..9) mask[y * width + x] = true // 7 wide x 6 tall = 42px

        val labeling = ConnectedComponents.label(mask, width, height)
        assertEquals(1, labeling.componentCount)

        val bounds = boundsOf(labeling, width, 0)
        assertEquals(listOf(3, 2, 9, 7), bounds.take(4))
        assertEquals(42, bounds[4])
    }

    @Test
    fun `diagonal-only adjacency does not connect under 4-connectivity`() {
        val width = 2
        val height = 2
        val mask = BooleanArray(width * height)
        mask[0 * width + 0] = true // (0,0)
        mask[1 * width + 1] = true // (1,1) - diagonal neighbor only

        val labeling = ConnectedComponents.label(mask, width, height)
        assertEquals(2, labeling.componentCount)
    }

    @Test
    fun `empty mask yields zero components`() {
        val labeling = ConnectedComponents.label(BooleanArray(25), 5, 5)
        assertEquals(0, labeling.componentCount)
        assertTrue(labeling.labels.all { it == -1 })
    }

    @Test
    fun `fully filled mask yields exactly one component covering every pixel`() {
        val width = 6
        val height = 6
        val mask = BooleanArray(width * height) { true }
        val labeling = ConnectedComponents.label(mask, width, height)
        assertEquals(1, labeling.componentCount)
        assertTrue(labeling.labels.all { it == 0 })
    }
}
