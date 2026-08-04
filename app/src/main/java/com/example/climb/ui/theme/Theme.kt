package com.example.climb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Deliberately single-theme (no light mode, no dynamic color) — see ClimbPalette.
private val ClimbColorScheme = darkColorScheme(
    primary = ClimbPalette.chalk,
    onPrimary = ClimbPalette.chalkText,
    secondary = ClimbPalette.textSecondary,
    background = ClimbPalette.bg,
    onBackground = ClimbPalette.textPrimary,
    surface = ClimbPalette.surface,
    onSurface = ClimbPalette.textPrimary,
    surfaceVariant = ClimbPalette.surfaceRaised,
    onSurfaceVariant = ClimbPalette.textSecondary,
    outline = ClimbPalette.border,
    error = ClimbPalette.fell,
)

@Composable
fun ClimbTheme(content: @Composable () -> Unit) {
    // The app has no localized strings yet, so force LTR rather than letting Compose
    // auto-mirror the layout for RTL system locales — otherwise English-only screens render
    // reversed (badge/date swap sides) with no actual RTL support behind it.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = ClimbColorScheme,
            content = content,
        )
    }
}
