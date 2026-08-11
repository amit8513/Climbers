package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * A weekly-leaderboard row — Community screen's 3 medal rows (gold/silver/bronze). [medalColor]
 * is fixed per rank by convention ([ClimbPalette.gold]/[ClimbPalette.silver]/[ClimbPalette.bronze]
 * already exist for exactly this and are reused rather than the spec's literal yellow/gray/red
 * medal hexes) — the caller picks the medal color, this just lays out rank number + name + grade.
 */
@Composable
fun LiveSendLeaderRow(
    rank: Int,
    name: String,
    grade: String,
    medalColor: Color,
    modifier: Modifier = Modifier,
    medalTextColor: Color = ClimbPalette.liveSendBg,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(medalColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = rank.toString(), color = medalTextColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(text = name, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(text = grade, color = ClimbPalette.liveSendAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * A club-member row — Community screen's Members tab (avatar + name + Admin/Member pill). Built
 * from [LiveSendAvatar] + [LiveSendBadge] rather than reinventing either.
 */
@Composable
fun LiveSendMemberRow(
    initial: String,
    name: String,
    roleLabel: String,
    roleColor: Color,
    roleTextColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveSendAvatar(initial = initial, size = 32)
        Spacer(Modifier.width(12.dp))
        Text(text = name, color = ClimbPalette.liveSendTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        LiveSendBadge(text = roleLabel, containerColor = roleColor, contentColor = roleTextColor, fontSize = 10)
    }
}
