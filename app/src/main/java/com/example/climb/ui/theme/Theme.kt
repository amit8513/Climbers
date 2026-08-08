package com.example.climb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun ClimbTheme(content: @Composable () -> Unit) {
    // The app has no localized strings yet, so force LTR rather than letting Compose
    // auto-mirror the layout for RTL system locales — otherwise English-only screens render
    // reversed (badge/date swap sides) with no actual RTL support behind it.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Read directly inside this @Composable (not hoisted to a top-level val) so switching
        // the active theme via ClimbPalette.applyPalette() — from anywhere, e.g. the Settings
        // screen — recomposes this MaterialTheme with the new colors immediately.
        MaterialTheme(
            colorScheme = darkColorScheme(
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
            ),
            content = content,
        )
    }
}
