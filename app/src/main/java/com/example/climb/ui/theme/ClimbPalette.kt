package com.example.climb.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.climb.data.RouteColor

/**
 * Fixed dark "gym energy" palette. The app deliberately stays single-theme (no light mode) —
 * the whole point is a near-black shell so a climb's tagged route color is the only saturated
 * thing on screen.
 */
object ClimbPalette {
    val bg = Color(0xFF16130F)
    val surface = Color(0xFF1E1A14)
    val surfaceRaised = Color(0xFF241F18)
    val border = Color(0xFF2E2A21)
    val borderStrong = Color(0xFF443D2E)
    val textPrimary = Color(0xFFF1EBDC)
    val textSecondary = Color(0xFF9C9280)
    val textMuted = Color(0xFF6B6252)
    val chalk = Color(0xFFF1EBDC)
    val chalkText = Color(0xFF16130F)
    val sent = Color(0xFF7EA86B)
    val fell = Color(0xFFB5654F)
    val project = Color(0xFF8F8560)
    val textureDot = textPrimary.copy(alpha = 0.05f)
    val chalkDust = textPrimary.copy(alpha = 0.4f)
    val holdSheen = Color.White.copy(alpha = 0.32f)
    val wall = Color(0xFF2B2823)
    val wall2 = Color(0xFF35312A)
}

/** A darkened, desaturation-safe tone of a route color, for text/borders drawn on top of it. */
fun RouteColor.darkAccent(): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(hex.toInt(), hsv)
    hsv[2] = (hsv[2] * 0.28f).coerceAtLeast(0.1f)
    return Color(AndroidColor.HSVToColor(hsv))
}
