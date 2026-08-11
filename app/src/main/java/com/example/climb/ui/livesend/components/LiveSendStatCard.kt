package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * A small square-ish "big number + uppercase label" block — the dashboard's Members/Live Now/
 * Sends Today cards and RouteDetail's Send Rate/Peak Grade/Sessions stats. Same shape both places
 * (16dp-rounded surface, centered value, small muted caption below); [valueColor] lets a
 * standout stat (e.g. "LIVE NOW") use [ClimbPalette.liveSendCta]-style emphasis instead of the
 * default text color.
 */
@Composable
fun LiveSendStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ClimbPalette.liveSendTextPrimary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ClimbPalette.liveSendSurface)
            .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(
            text = label.uppercase(),
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
