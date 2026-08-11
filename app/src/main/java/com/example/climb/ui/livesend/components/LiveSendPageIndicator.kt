package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

/**
 * The onboarding dot-page-indicator (Dot1/Dot2/Dot3 in the Figma spec) — a wide lime pill for the
 * current page and small muted circles for the rest. Only one Onboarding frame was exported, but
 * this is built as a proper `pageCount`/`currentPage` component since a real onboarding flow is
 * necessarily multi-page.
 */
@Composable
fun LiveSendPageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            if (index > 0) Spacer(Modifier.width(6.dp))
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 20.dp else 6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) ClimbPalette.liveSendAccent else ClimbPalette.liveSendTextMuted),
            )
        }
    }
}
