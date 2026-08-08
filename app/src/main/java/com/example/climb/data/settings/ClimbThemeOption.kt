package com.example.climb.data.settings

/** The 3 selectable full UI themes — background, surfaces, text, and accent all differ between
 * them (see [com.example.climb.ui.theme.ClimbColorPalette]/`palette()`); status colors like
 * sent/fell/gold don't vary here since they carry meaning independent of cosmetic theme. */
enum class ClimbThemeOption(val storageKey: String, val label: String) {
    DARK_STONE("dark_stone", "Dark Stone"),
    NIGHT_ASCENT("night_ascent", "Night Ascent"),
    VOLCANIC("volcanic", "Volcanic"),
    ;

    companion object {
        val DEFAULT = DARK_STONE
        fun fromStorageKey(key: String?): ClimbThemeOption = entries.find { it.storageKey == key } ?: DEFAULT
    }
}
