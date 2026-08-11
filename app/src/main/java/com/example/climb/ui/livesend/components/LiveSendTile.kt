package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * A 160x90 grid tile — ClubDashboard's "Manage" grid (Routes/Members/Venues/Broadcast, each
 * "🧗 Routes"-style emoji + label) and ExploreScreen's venue tiles (name + route count). Same
 * shape either way; [sublabel] is null for the Manage tiles and set for the venue tiles.
 */
@Composable
fun LiveSendTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    sublabel: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .width(160.dp)
            .height(90.dp)
            .clip(shape)
            .background(ClimbPalette.liveSendSurface)
            .border(1.dp, ClimbPalette.liveSendBorder, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (emoji != null) "$emoji $label" else label,
            color = ClimbPalette.liveSendTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        if (sublabel != null) {
            Text(text = sublabel, color = ClimbPalette.liveSendTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
