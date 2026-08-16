package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

/**
 * Live-Send-styled equivalent of [com.example.climb.ui.components.SectionCard] — same
 * title-plus-content card shape, but built on the fixed liveSend surface/border palette instead
 * of the theme-reactive one, so screens using it read consistently with Broadcast/Routes/the
 * staff Club Mode shell rather than clashing against their neon-lime-on-near-black look.
 */
@Composable
fun LiveSendSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        LiveSendSectionLabel(text = title, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}
