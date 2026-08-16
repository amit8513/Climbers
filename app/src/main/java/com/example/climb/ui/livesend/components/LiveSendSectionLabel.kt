package com.example.climb.ui.livesend.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * The small uppercase muted section header repeated throughout Live Send — "TODAY'S ACTIVITY",
 * "MANAGE", "POPULAR ROUTES", "VENUES", "Weekly Leaders", "Club Members · 2", "App Mode",
 * "Appearance". Mirrors the existing app's recurring-per-file `SectionLabel` pattern (see
 * [com.example.climb.ui.clubs.ClubsScreen]'s private one) but is promoted to a shared file here
 * since nearly every Live Send screen needs it, matching this project's rule to promote a pattern
 * once enough call sites want it. Pass [forceUppercase] = false for the handful of spec labels
 * that are Title Case rather than ALL CAPS (e.g. "Weekly Leaders").
 */
@Composable
fun LiveSendSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 12,
    forceUppercase: Boolean = true,
    color: Color = ClimbPalette.liveSendTextMuted,
) {
    Text(
        text = if (forceUppercase) text.uppercase() else text,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}
