package com.example.climb.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.climb.ui.theme.ClimbPalette

private val BAR_HEIGHT = 56.dp

data class ClubBarTab(
    val icon: ImageVector,
    val contentDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** Club Mode's own floating pill, mirroring [ClimbBottomBar]'s look — a visibly different lower
 * bar so it's unmistakable you're inside a club rather than using the app as a plain climber.
 * Shared by both the staff Club Mode shell (Manage/Updates/Members/Exit) and the member club
 * shell (Routes/Updates/My club videos/Club leaderboard) — same look, different tabs. */
@Composable
fun ClubBottomBar(tabs: List<ClubBarTab>) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .height(BAR_HEIGHT)
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
            .background(ClimbPalette.surfaceRaised)
            .border(1.dp, ClimbPalette.borderStrong, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            ClubNavTab(
                icon = tab.icon,
                contentDescription = tab.contentDescription,
                selected = tab.selected,
                onClick = tab.onClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ClubNavTab(icon: ImageVector, contentDescription: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(BAR_HEIGHT)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) ClimbPalette.chalk else ClimbPalette.textMuted,
            modifier = Modifier.size(if (selected) 26.dp else 22.dp),
        )
    }
}
