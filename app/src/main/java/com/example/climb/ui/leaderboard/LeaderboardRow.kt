package com.example.climb.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.model.RankMovementType
import com.example.climb.ui.theme.ClimbPalette

/** Minimum touch target everywhere in this feature, per the accessibility requirement. */
private val MinTouchTarget = 44.dp

/** Falls back to the initials whenever [photoUrl] is null, blank, or fails to load — the initials
 * are drawn first and the photo layered on top, so a failed/loading image never leaves a blank
 * circle. */
@Composable
fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp, photoUrl: String? = null, modifier: Modifier = Modifier) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ClimbPalette.liveSendSurfaceRaised)
            .border(1.dp, ClimbPalette.liveSendBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Black, fontSize = (size.value * 0.4f).sp)
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        }
    }
}

@Composable
fun RankMovementChip(entry: LeaderboardEntry, modifier: Modifier = Modifier) {
    val (glyph, color) = when (entry.rankMovementType) {
        RankMovementType.UP -> "▲" to ClimbPalette.positive
        RankMovementType.DOWN -> "▼" to ClimbPalette.negative
        RankMovementType.UNCHANGED -> "–" to ClimbPalette.liveSendTextMuted
        RankMovementType.NEW -> "★" to ClimbPalette.liveSendAccent
        RankMovementType.UNRANKED -> "–" to ClimbPalette.liveSendTextMuted
    }
    Row(
        modifier = modifier.semantics { contentDescription = rankMovementAccessibilityLabel(entry) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = glyph, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(text = rankMovementLabel(entry), color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Generic lock when the viewer can't see a video, a play glyph + count when they can — never a
 * thumbnail, and this composable never receives a URL to load one from in the first place. */
@Composable
fun VideoStatusIndicator(entry: LeaderboardEntry, onOpenVideos: () -> Unit, modifier: Modifier = Modifier) {
    when {
        entry.hasViewableVideo -> Row(
            modifier = modifier
                .size(width = 52.dp, height = MinTouchTarget)
                .clickable(onClick = onOpenVideos)
                .semantics { contentDescription = "${entry.sharedVideoCount} viewable shared video${if (entry.sharedVideoCount == 1) "" else "s"} from ${entry.displayName}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "▶", color = ClimbPalette.liveSendAccent, fontSize = 13.sp)
            Text(text = "${entry.sharedVideoCount}", color = ClimbPalette.liveSendTextMuted, fontSize = 12.sp)
        }
        entry.hasPrivateVideo -> Box(
            modifier = modifier
                .size(MinTouchTarget)
                .semantics { contentDescription = "This video is private" },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔒", color = ClimbPalette.liveSendTextMuted, fontSize = 14.sp)
        }
        else -> Spacer(modifier.size(MinTouchTarget))
    }
}

@Composable
fun LeaderboardRow(
    entry: LeaderboardEntry,
    category: LeaderboardCategory,
    onClick: () -> Unit,
    onOpenVideos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (entry.isCurrentUser) ClimbPalette.liveSendSurfaceRaised else ClimbPalette.liveSendSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("leaderboard_row_${entry.userId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${entry.rank}",
            color = ClimbPalette.liveSendTextMuted,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            modifier = Modifier.width(24.dp),
        )
        InitialsAvatar(entry.displayName, 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = entry.displayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (entry.isCurrentUser) {
                    Text(text = "YOU", color = ClimbPalette.liveSendAccent, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.5.sp)
                }
            }
            Text(
                text = rowSupportingLines(category, entry).joinToString(" · "),
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = primaryValue(category, entry), color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            RankMovementChip(entry)
        }
        VideoStatusIndicator(entry, onOpenVideos)
    }
}

@Composable
fun StickyCurrentUserRow(entry: LeaderboardEntry, category: LeaderboardCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ClimbPalette.liveSendBg)
            .padding(top = 8.dp),
    ) {
        Text(
            text = "YOUR RANK",
            color = ClimbPalette.liveSendTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        if (!entry.isEligible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClimbPalette.liveSendSurfaceRaised)
                    .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(
                    text = entry.eligibilityReason ?: "Not eligible this week",
                    color = ClimbPalette.liveSendTextMuted,
                    fontSize = 13.sp,
                )
            }
        } else {
            LeaderboardRow(entry = entry, category = category, onClick = onClick, onOpenVideos = {})
        }
    }
}
