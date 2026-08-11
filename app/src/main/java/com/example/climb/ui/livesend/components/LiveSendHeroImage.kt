package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

/**
 * The full-width hero photo + bottom scrim gradient at the top of Onboarding, Login, and Signup —
 * same construction each time (a tall image bleeding into [ClimbPalette.liveSendBg] so the headline text
 * below reads clearly), just a different height (550/280/220dp) and headline content per screen.
 * Takes a `content` slot for the actual `Image`/`AsyncImage` painter plus any overlaid controls
 * (e.g. Login/Signup's "← Back") so this stays free of any asset-loading dependency.
 */
@Composable
fun LiveSendHeroImage(
    modifier: Modifier = Modifier,
    height: Int = 320,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp),
    ) {
        content()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((height / 2).dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, ClimbPalette.liveSendBg))),
        )
    }
}
