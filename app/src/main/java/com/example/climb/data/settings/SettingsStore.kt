package com.example.climb.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.palette

private const val PREFS_NAME = "climb_settings"
private const val KEY_THEME = "theme_option"
private const val KEY_HOME_VIDEO_BACKGROUND = "home_video_background_enabled"
private const val KEY_HOME_VIDEO_OPACITY = "home_video_opacity"
private const val KEY_HOME_VIDEO_MONTAGE_STYLE = "home_video_montage_style"

/** How visible the video is through its darkening scrim — 0 hides it entirely behind the scrim,
 * 1 removes the scrim altogether. Defaults higher than a "safe middle" so the montage actually
 * reads as video rather than a mostly-black screen; text still has the scrim's low end as a
 * floor (see [com.example.climb.ui.home.HomeVideoBackground]) so it never becomes unreadable. */
private const val DEFAULT_HOME_VIDEO_OPACITY = 0.75f

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

    /** On by default — the Home background montage is opt-out, not opt-in. */
    var homeVideoBackgroundEnabled: Boolean by mutableStateOf(prefs.getBoolean(KEY_HOME_VIDEO_BACKGROUND, true))
        private set

    var homeVideoOpacity: Float by mutableStateOf(prefs.getFloat(KEY_HOME_VIDEO_OPACITY, DEFAULT_HOME_VIDEO_OPACITY))
        private set

    var homeVideoMontageStyle: HomeVideoMontageStyle by mutableStateOf(
        HomeVideoMontageStyle.fromStorageKey(prefs.getString(KEY_HOME_VIDEO_MONTAGE_STYLE, null)),
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

    fun updateHomeVideoBackgroundEnabled(enabled: Boolean) {
        homeVideoBackgroundEnabled = enabled
        prefs.edit().putBoolean(KEY_HOME_VIDEO_BACKGROUND, enabled).apply()
    }

    fun updateHomeVideoOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        homeVideoOpacity = clamped
        prefs.edit().putFloat(KEY_HOME_VIDEO_OPACITY, clamped).apply()
    }

    fun selectHomeVideoMontageStyle(style: HomeVideoMontageStyle) {
        homeVideoMontageStyle = style
        prefs.edit().putString(KEY_HOME_VIDEO_MONTAGE_STYLE, style.storageKey).apply()
    }
}
