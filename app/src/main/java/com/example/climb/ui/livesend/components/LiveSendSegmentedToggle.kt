package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * A two-way pill toggle — Signup's "I'm joining as: Climber / Gym-Club" and Settings' "App Mode:
 * Personal / Club". Mirrors the shipped app's `SegmentedTabs` pattern in
 * [com.example.climb.ui.auth.AuthScreen] (private there since only that screen used it); promoted
 * to shared here because Live Send needs the same control twice. The selected segment fills with
 * [ClimbPalette.liveSendAccent] and flips its text to [ClimbPalette.liveSendAccentText] for contrast, matching the
 * spec's lime-fill-on-dark-text selected state.
 */
@Composable
fun LiveSendSegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (selected) ClimbPalette.liveSendAccent else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) ClimbPalette.liveSendAccentText else ClimbPalette.liveSendTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
