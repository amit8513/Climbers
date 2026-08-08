package com.example.climb.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.climb.data.RouteColor
import com.example.climb.data.settings.ClimbThemeOption

/** Everything that varies between the app's 3 selectable themes — background, surfaces, text,
 * and the "chalk" accent (record button, selected nav icon, primary buttons). Status colors
 * (sent/fell/gold/silver/...) carry real meaning (send vs. fall, leaderboard medal) and stay
 * fixed across every theme so switching themes never changes what a color *means*. */
data class ClimbColorPalette(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val chalk: Color,
    val chalkText: Color,
    val rockFace: Color,
    val wall: Color,
    val wall2: Color,
)

/** The original fixed palette — warm near-black shell, cream text/accent. */
val DarkStonePalette = ClimbColorPalette(
    bg = Color(0xFF16130F),
    surface = Color(0xFF1E1A14),
    surfaceRaised = Color(0xFF241F18),
    border = Color(0xFF2E2A21),
    borderStrong = Color(0xFF443D2E),
    textPrimary = Color(0xFFF1EBDC),
    textSecondary = Color(0xFF9C9280),
    textMuted = Color(0xFF6B6252),
    chalk = Color(0xFFF1EBDC),
    chalkText = Color(0xFF16130F),
    rockFace = Color(0xFF241F18),
    wall = Color(0xFF2B2823),
    wall2 = Color(0xFF35312A),
)

/** Cool blue-black shell, icy text, sky-blue accent. */
val NightAscentPalette = ClimbColorPalette(
    bg = Color(0xFF0E1420),
    surface = Color(0xFF161D2C),
    surfaceRaised = Color(0xFF1C2536),
    border = Color(0xFF2A3548),
    borderStrong = Color(0xFF3E4E68),
    textPrimary = Color(0xFFE7EEF7),
    textSecondary = Color(0xFF8C9BB5),
    textMuted = Color(0xFF5C6A82),
    chalk = Color(0xFF7FC7E8),
    chalkText = Color(0xFF0E1420),
    rockFace = Color(0xFF1C2536),
    wall = Color(0xFF212B3D),
    wall2 = Color(0xFF2B3750),
)

/** Deep charcoal shell with warm red/orange undertones, warm accent. */
val VolcanicPalette = ClimbColorPalette(
    bg = Color(0xFF1A1210),
    surface = Color(0xFF241813),
    surfaceRaised = Color(0xFF2E1E17),
    border = Color(0xFF3D2820),
    borderStrong = Color(0xFF573424),
    textPrimary = Color(0xFFF5E6DC),
    textSecondary = Color(0xFFB08E7C),
    textMuted = Color(0xFF7A5C4C),
    chalk = Color(0xFFE8734A),
    chalkText = Color(0xFF1A1210),
    rockFace = Color(0xFF2E1E17),
    wall = Color(0xFF32211B),
    wall2 = Color(0xFF432B21),
)

fun ClimbThemeOption.palette(): ClimbColorPalette = when (this) {
    ClimbThemeOption.DARK_STONE -> DarkStonePalette
    ClimbThemeOption.NIGHT_ASCENT -> NightAscentPalette
    ClimbThemeOption.VOLCANIC -> VolcanicPalette
}

/**
 * A theme-switchable palette exposed as the same static-looking `ClimbPalette.xxx` properties
 * every screen already reads — each field is backed by [mutableStateOf], so a plain (non-Composable)
 * property read still returns the current value, and reading it from inside a `@Composable`
 * (which is exactly how every consuming screen already uses it, e.g. `Text(color = ClimbPalette.textPrimary)`)
 * is tracked by Compose's snapshot system just like any other observable state. That means
 * [applyPalette] can swap every color out from under the whole app, and every screen that already
 * existed before theming was added recomposes with the new colors automatically — no call site
 * needed to change.
 *
 * Status colors below (sent/fell/gold/silver/bronze/positive/negative) are deliberately *not*
 * part of [ClimbColorPalette] — they carry meaning (send vs. fall, leaderboard medal) that
 * shouldn't be reinterpreted just because the cosmetic theme changed.
 */
object ClimbPalette {
    var bg by mutableStateOf(DarkStonePalette.bg)
        private set
    var surface by mutableStateOf(DarkStonePalette.surface)
        private set
    var surfaceRaised by mutableStateOf(DarkStonePalette.surfaceRaised)
        private set
    var border by mutableStateOf(DarkStonePalette.border)
        private set
    var borderStrong by mutableStateOf(DarkStonePalette.borderStrong)
        private set
    var textPrimary by mutableStateOf(DarkStonePalette.textPrimary)
        private set
    var textSecondary by mutableStateOf(DarkStonePalette.textSecondary)
        private set
    var textMuted by mutableStateOf(DarkStonePalette.textMuted)
        private set
    var chalk by mutableStateOf(DarkStonePalette.chalk)
        private set
    var chalkText by mutableStateOf(DarkStonePalette.chalkText)
        private set

    /** Dark stone surface used where a chalk-white mark needs to read on top of it. */
    var rockFace by mutableStateOf(DarkStonePalette.rockFace)
        private set
    var wall by mutableStateOf(DarkStonePalette.wall)
        private set
    var wall2 by mutableStateOf(DarkStonePalette.wall2)
        private set

    val sent = Color(0xFF7EA86B)
    val fell = Color(0xFFB5654F)
    val project = Color(0xFF8F8560)
    val holdSheen = Color.White.copy(alpha = 0.32f)
    val gold = Color(0xFFD5A62E)
    val silver = Color(0xFFA9AAAC)
    val bronze = Color(0xFFB8753E)
    val positive = Color(0xFF3D9A61)
    val negative = Color(0xFFD54B4B)

    // Derived from textPrimary, so these must stay computed rather than captured once — otherwise
    // they'd freeze at whichever theme was active when the app first read them.
    val textureDot: Color get() = textPrimary.copy(alpha = 0.05f)
    val chalkDust: Color get() = textPrimary.copy(alpha = 0.4f)

    fun applyPalette(palette: ClimbColorPalette) {
        bg = palette.bg
        surface = palette.surface
        surfaceRaised = palette.surfaceRaised
        border = palette.border
        borderStrong = palette.borderStrong
        textPrimary = palette.textPrimary
        textSecondary = palette.textSecondary
        textMuted = palette.textMuted
        chalk = palette.chalk
        chalkText = palette.chalkText
        rockFace = palette.rockFace
        wall = palette.wall
        wall2 = palette.wall2
    }
}

/** A darkened, desaturation-safe tone of a route color, for text/borders drawn on top of it. */
fun RouteColor.darkAccent(): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(hex.toInt(), hsv)
    hsv[2] = (hsv[2] * 0.28f).coerceAtLeast(0.1f)
    return Color(AndroidColor.HSVToColor(hsv))
}
