package com.example.climb.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.clubs.ClubUpdateEntity
import com.example.climb.ui.theme.ClimbPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val updateDateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)

/**
 * A single club update — replaces the plain in-column `UpdateRow` text in [ClubUpdatesScreen] with
 * its own bordered card, so a scrolling list of updates has clear separation between entries.
 * [ClubUpdateEntity] has no "type" field yet (NEW SET / MAINTENANCE / EVENT), so unlike the visual
 * spec this doesn't show a type badge — that needs one new optional field on the entity plus a
 * staff-side picker, which is a small, separate follow-up rather than part of this pass.
 */
@Composable
fun GymUpdateCard(update: ClubUpdateEntity, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ClimbPalette.surface)
            .border(1.dp, ClimbPalette.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text = update.text, color = ClimbPalette.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(6.dp))
        Text(text = updateDateFormat.format(Date(update.createdAt)), color = ClimbPalette.textMuted, fontSize = 11.sp)
    }
}
