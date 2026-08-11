package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * A tappable theme preview swatch — Settings' "Appearance" row (Live Send / Chalk Stone /
 * Volcanic). [previewColor] is the swatch's own fill (typically that theme's `bg`), and [selected]
 * draws the [ClimbPalette.liveSendAccent] ring the spec shows on the active swatch (SwatchLiveSend's
 * stroke). This is generic over any [com.example.climb.data.settings.ClimbThemeOption] the caller
 * wants to preview, not hardcoded to the 3 existing options.
 */
@Composable
fun LiveSendThemeSwatch(
    name: String,
    previewColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(60.dp)
                .clip(shape)
                .background(previewColor)
                .border(if (selected) 2.dp else 1.dp, if (selected) ClimbPalette.liveSendAccent else ClimbPalette.liveSendBorder, shape)
                .clickable(onClick = onClick),
        )
        Text(
            text = name,
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
