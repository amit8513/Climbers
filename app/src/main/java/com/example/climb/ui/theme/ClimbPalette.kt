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

    // --- "Live Send" (Alternative UI Concept 2) fixed accents ---------------------------------
    // The Live Send design exploration (ui/livesend/) is a fixed-palette "Energetic Sport Style"
    // concept, not a theme-reactive one: its whole visual identity (including its base
    // background/text/accent colors, not just the CTA/achievement/info highlights below) is
    // pinned by the Figma spec and must NOT shift when the user's selected ClimbThemeOption
    // changes. So — like sent/fell/gold above — every Live Send color is fixed regardless of
    // theme. Sourced from the Live Send Figma token set: accent-1/accent-5/accent-6/accent-7 plus
    // the spec's base bg/text tokens.
    /** accent-1 #c6ff3d — signature neon-lime accent: highlighter marks, active tab underlines/labels, links, selection rings, progress fills, "Live"/badge emphasis. This is the concept's primary accent — use it wherever the spec calls for "the accent color", instead of the theme-reactive [chalk]. */
    val liveSendAccent = Color(0xFFC6FF3D)
    /** Live Send spec base background #0b0e10 — fixed screen/surface background, instead of the theme-reactive [bg]. */
    val liveSendBg = Color(0xFF0B0E10)
    /** Live Send spec base primary text #f5fafa — fixed primary text/icon color, instead of the theme-reactive [textPrimary]. */
    val liveSendTextPrimary = Color(0xFFF5FAFA)
    /** Live Send spec base muted text #7c8a8f — fixed secondary/muted text color, instead of the theme-reactive [textMuted]. */
    val liveSendTextMuted = Color(0xFF7C8A8F)
    /** Dark contrast color (accent-4 #0b0e10) for text/icons placed on top of [liveSendAccent], instead of the theme-reactive [chalkText]. */
    val liveSendAccentText = Color(0xFF0B0E10)
    /** accent #161b1f — fixed card/surface background, instead of the theme-reactive [surface]. Card surfaces must stay pinned along with the rest of this fixed-palette concept, or they'd visibly clash against [liveSendBg]/[liveSendAccent] whenever the user's selected theme differs from the spec's dark styling. */
    val liveSendSurface = Color(0xFF161B1F)
    /** accent-3 #1f262b — fixed raised-surface background (inputs, pills, OAuth buttons), instead of the theme-reactive [surfaceRaised]. */
    val liveSendSurfaceRaised = Color(0xFF1F262B)
    /** Fixed hairline border tone for card/tile outlines against [liveSendSurface], instead of the theme-reactive [border]. Not an explicit Figma token (the spec's flat, shadow-less depth relies on subtle surface/border contrast rather than a named stroke color) — derived one step lighter than [liveSendSurfaceRaised] to read as a hairline, not a new fill. */
    val liveSendBorder = Color(0xFF2A343B)
    /** accent-5 #ff3d5a — vivid CTA/urgency red: primary action buttons, FAB, hardest-grade badges, admin/privileged pills. */
    val liveSendCta = Color(0xFFFF3D5A)
    /** accent-6 #ffd23d — achievement gold: leaderboard medal, yellow-graded routes. */
    val liveSendGold = Color(0xFFFFD23D)
    /** accent-7 #3da9fc — info/live blue: "live now" indicator dots, informational badges. */
    val liveSendInfo = Color(0xFF3DA9FC)
    /** neutral-1 #000000 at partial alpha — scrim for legibility over video/image thumbnails (play-button circles, duration tags). Fixed and theme-independent, like [holdSheen]. */
    val mediaScrim = Color.Black.copy(alpha = 0.55f)

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
