package com.example.climb.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces Phase 3B's hard trust-boundary requirement: a manual validation session/result can
 * never become, or be confused with, trusted wall-camera capture data. Two independent checks:
 * a structural one (reflection — [ManualValidationSession] has no field of a type that could ever
 * hold `AttemptSource.WALL_CAMERA` or a `WallCaptureSession`) and a source-level one (this whole
 * package never references the official persistence path at all — checked directly against the
 * real source files, not just by omission in these tests, so a future edit that reintroduces the
 * dependency fails this test even before anyone writes a test exercising the new behavior).
 */
class ManualValidationTrustBoundaryTest {

    @Test
    fun `ManualValidationSession has no field of type AttemptSource or WallCaptureSession`() {
        val fieldTypeNames = ManualValidationSession::class.java.declaredFields.map { it.type.name }

        assertTrue(
            "ManualValidationSession's fields: $fieldTypeNames",
            fieldTypeNames.none { it.contains("AttemptSource") || it.contains("WallCaptureSession") },
        )
    }

    @Test
    fun `no file in the validation package references ClubRepository, WallCaptureSession, or official Firestore writes`() {
        val validationSourceDir = findValidationSourceDirectory()
        val kotlinFiles = validationSourceDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("expected to find .kt files under $validationSourceDir", kotlinFiles.isNotEmpty())

        val forbiddenTokens = listOf(
            "ClubRepository",
            "WallCaptureSession",
            "AttemptSource.WALL_CAMERA",
            "attemptAttributions",
            "RouteAttributionResultEntity",
            "com.google.firebase",
        )

        for (file in kotlinFiles) {
            val code = file.readText().stripKotlinComments()
            for (token in forbiddenTokens) {
                assertFalse(
                    "${file.name} must never reference '$token' outside a comment - the manual validation path " +
                        "must stay structurally separate from official club-camera persistence",
                    code.contains(token),
                )
            }
        }
    }

    @Test
    fun `no file in the validation UI package references ClubRepository or official Firestore writes`() {
        val validationUiDir = findValidationUiSourceDirectory()
        if (!validationUiDir.exists()) return // UI not created yet is fine; nothing to check
        val kotlinFiles = validationUiDir.walkTopDown().filter { it.extension == "kt" }.toList()

        val forbiddenTokens = listOf("ClubRepository", "WallCaptureSession", "AttemptSource.WALL_CAMERA", "attemptAttributions")
        for (file in kotlinFiles) {
            val code = file.readText().stripKotlinComments()
            for (token in forbiddenTokens) {
                assertFalse(
                    "${file.name} must never reference '$token' outside a comment",
                    code.contains(token),
                )
            }
        }
    }

    /** Strips `/** ... */`, `/* ... */`, and `// ...` comments so the trust-boundary check only
     * ever inspects real code — explanatory prose (e.g. "structurally unrelated to
     * ClubRepository/WallCaptureSession") is exactly what these doc comments are expected to say,
     * and must not itself trip the check. Not a full Kotlin lexer (doesn't special-case these
     * token sequences appearing inside string literals) — good enough for this codebase's actual
     * source, which contains no such string literals. */
    private fun String.stripKotlinComments(): String =
        replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { line -> line.substringBefore("//") }

    /** Gradle unit tests for `:app` run with the module directory as the working directory - this
     * walks up defensively in case that assumption ever changes, rather than hardcoding a path
     * that would silently stop checking anything if it's ever wrong. */
    private fun findValidationSourceDirectory(): File {
        val relative = "src/main/java/com/example/climb/validation"
        var dir = File(".").absoluteFile
        repeat(4) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: dir
        }
        throw AssertionError("Could not locate $relative from working directory ${File(".").absolutePath}")
    }

    private fun findValidationUiSourceDirectory(): File {
        val relative = "src/main/java/com/example/climb/ui/validation"
        var dir = File(".").absoluteFile
        repeat(4) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: dir
        }
        return File(relative)
    }
}
