package com.example.climb.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.palette

private const val PREFS_NAME = "climb_settings"
private const val KEY_THEME = "theme_option"

/**
 * Device-local (not synced to Firestore) preferences. [themeOption] is backed by [mutableStateOf]
 * rather than a [kotlinx.coroutines.flow.Flow] so any composable reading it recomposes the moment
 * [selectTheme] is called anywhere else in the tree — no ViewModel or callback threading needed.
 * Applies the persisted theme to [ClimbPalette] immediately on construction (not lazily on first
 * screen read) so the very first frame already renders in the right theme instead of flashing the
 * default and then switching.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeOption: ClimbThemeOption by mutableStateOf(
        ClimbThemeOption.fromStorageKey(prefs.getString(KEY_THEME, null)),
    )
        private set

    init {
        ClimbPalette.applyPalette(themeOption.palette())
    }

    fun selectTheme(option: ClimbThemeOption) {
        themeOption = option
        ClimbPalette.applyPalette(option.palette())
        prefs.edit().putString(KEY_THEME, option.storageKey).apply()
    }
}
