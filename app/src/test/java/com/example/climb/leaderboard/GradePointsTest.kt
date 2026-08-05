package com.example.climb.leaderboard

import com.example.climb.leaderboard.model.VGrade
import com.example.climb.leaderboard.model.gradePoints
import com.example.climb.leaderboard.scoring.sendPoints
import org.junit.Assert.assertEquals
import org.junit.Test

class GradePointsTest {

    @Test
    fun `gradePoints matches the spec table for V0 through V8`() {
        assertEquals(10, VGrade(0).gradePoints())
        assertEquals(20, VGrade(1).gradePoints())
        assertEquals(30, VGrade(2).gradePoints())
        assertEquals(40, VGrade(3).gradePoints())
        assertEquals(50, VGrade(4).gradePoints())
        assertEquals(60, VGrade(5).gradePoints())
        assertEquals(70, VGrade(6).gradePoints())
        assertEquals(80, VGrade(7).gradePoints())
        assertEquals(90, VGrade(8).gradePoints())
    }

    @Test
    fun `gradePoints supports grades above V8 unmodified`() {
        assertEquals(100, VGrade(9).gradePoints())
        assertEquals(160, VGrade(15).gradePoints())
    }

    @Test
    fun `VGrade rejects a negative numeric value`() {
        assertThrowsIllegalArgument { VGrade(-1) }
    }

    @Test
    fun `flash send applies a 25 percent bonus`() {
        val points = sendPoints(VGrade(4), isFlash = true, successfulAttemptNumber = 1)
        assertEquals(62.5, points, 0.0001)
    }

    @Test
    fun `second-attempt send applies a 15 percent bonus`() {
        val points = sendPoints(VGrade(4), isFlash = false, successfulAttemptNumber = 2)
        assertEquals(57.5, points, 0.0001)
    }

    @Test
    fun `normal send applies no bonus`() {
        val points = sendPoints(VGrade(4), isFlash = false, successfulAttemptNumber = 5)
        assertEquals(50.0, points, 0.0001)
    }

    @Test
    fun `flash bonus takes priority over second-attempt bonus`() {
        val points = sendPoints(VGrade(0), isFlash = true, successfulAttemptNumber = 2)
        assertEquals(12.5, points, 0.0001)
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
