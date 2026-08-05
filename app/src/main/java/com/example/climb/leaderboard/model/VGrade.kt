package com.example.climb.leaderboard.model

/**
 * A proper type instead of a raw "V5" string throughout the codebase. [numericValue] must be
 * >= 0; there's no upper bound, so grades above V8 are supported automatically wherever this
 * type is used.
 */
data class VGrade(val numericValue: Int) : Comparable<VGrade> {
    init {
        require(numericValue >= 0) { "V grade must be >= 0, was $numericValue" }
    }

    val displayName: String get() = "V$numericValue"

    override fun compareTo(other: VGrade): Int = numericValue.compareTo(other.numericValue)

    override fun toString(): String = displayName
}

/** gradePoints(vGrade) = (numericGrade + 1) * 10 — V0=10 .. V8=90, unbounded above. */
fun VGrade.gradePoints(): Int = (numericValue + 1) * 10
