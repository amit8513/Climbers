package com.example.climb.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ClimbThemeOptionTest {

    @Test
    fun `round-trips every option through its storage key`() {
        for (option in ClimbThemeOption.entries) {
            assertEquals(option, ClimbThemeOption.fromStorageKey(option.storageKey))
        }
    }

    @Test
    fun `falls back to the default for an unknown or missing key`() {
        assertEquals(ClimbThemeOption.DEFAULT, ClimbThemeOption.fromStorageKey(null))
        assertEquals(ClimbThemeOption.DEFAULT, ClimbThemeOption.fromStorageKey("not-a-real-key"))
    }
}
