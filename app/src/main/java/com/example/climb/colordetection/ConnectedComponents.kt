package com.example.climb.colordetection

/**
 * Labels connected regions of `true` cells in a boolean mask over a `width x height` grid.
 *
 * Iterative, stack-based flood fill — deliberately not recursive, since a single hold-shaped
 * region in a real video frame can span many thousands of connected pixels and a recursive
 * per-pixel visit would risk a stack overflow long before that.
 *
 * Uses 4-connectivity (up/down/left/right only, no diagonals). 8-connectivity would be more
 * forgiving of a single diagonal noise/chalk pixel bridging two holds that don't actually touch —
 * exactly the false-merge failure mode later phases (boundary refinement, per-object validation)
 * need to be able to catch, not have papered over here by an overly permissive connectivity rule.
 */
object ConnectedComponents {

    /** @param labels Per-pixel component id, same length/row-major layout as the input mask;
     * `-1` means "not part of any component" (mask was `false` there). */
    data class Labeling(val labels: IntArray, val componentCount: Int)

    fun label(mask: BooleanArray, width: Int, height: Int): Labeling {
        require(mask.size == width * height) { "mask.size (${mask.size}) must equal width*height (${width * height})" }

        val labels = IntArray(mask.size) { -1 }
        var nextLabel = 0
        val stack = ArrayDeque<Int>()

        for (start in mask.indices) {
            if (!mask[start] || labels[start] != -1) continue

            val currentLabel = nextLabel++
            labels[start] = currentLabel
            stack.addLast(start)

            while (stack.isNotEmpty()) {
                val index = stack.removeLast()
                val x = index % width
                val y = index / width

                if (x > 0) visit(mask, labels, stack, width, x - 1, y, currentLabel)
                if (x < width - 1) visit(mask, labels, stack, width, x + 1, y, currentLabel)
                if (y > 0) visit(mask, labels, stack, width, x, y - 1, currentLabel)
                if (y < height - 1) visit(mask, labels, stack, width, x, y + 1, currentLabel)
            }
        }

        return Labeling(labels, nextLabel)
    }

    private fun visit(mask: BooleanArray, labels: IntArray, stack: ArrayDeque<Int>, width: Int, x: Int, y: Int, currentLabel: Int) {
        val index = y * width + x
        if (mask[index] && labels[index] == -1) {
            labels[index] = currentLabel
            stack.addLast(index)
        }
    }
}
