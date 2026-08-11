package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

/**
 * The generic big rounded content card behind nearly every Live Send surface — dashboard stat
 * cards, the video card, the chart/send-rate cards on Progress, the leaderboard/members cards on
 * Community, and the profile/theme/mode cards on Settings. The Figma spec fills these with flat
 * color and no stroke, but per this project's existing convention (every bordered surface —
 * [com.example.climb.ui.components.SectionCard], [com.example.climb.ui.clubs.ClubCard] — pairs a
 * surface fill with a hairline border for contrast against [com.example.climb.ui.theme.wallTexture])
 * this keeps that border. Corner radius varies a lot across the spec (16/20/28dp) so it's a
 * parameter rather than fixed, unlike [com.example.climb.ui.components.SectionCard]'s constant 12dp.
 */
@Composable
fun LiveSendCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    padding: Int = 16,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.liveSendSurface)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding.dp),
        content = content,
    )
}
