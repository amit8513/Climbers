package com.example.climb.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Only shows what [entry] has already been authorized to expose — by the time it reaches this
 * screen, [com.example.climb.leaderboard.privacy.LeaderboardPrivacyFilter] has already stripped
 * any video field the viewer isn't allowed to see (never a URL/thumbnail/title, only a lock state
 * or a count), so there's nothing further to hide here in the UI.
 */
@Composable
fun LeaderboardProfileScreen(
    entry: LeaderboardEntry,
    category: LeaderboardCategory,
    onBack: () -> Unit,
    onOpenFriendClimbs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                text = "← Back",
                color = ClimbPalette.liveSendTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp).clickable(onClick = onBack),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InitialsAvatar(entry.displayName, 64.dp)
                Column {
                    Text(text = entry.displayName, color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(text = "Rank #${entry.rank} · ${category.tabTitle}", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(18.dp))

            Column {
                LiveSendSectionLabel(text = "This week")
                Spacer(Modifier.height(10.dp))
                LiveSendCard {
                    Text(text = primaryValue(category, entry), color = ClimbPalette.liveSendAccent, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    rowSupportingLines(category, entry).forEach { line ->
                        Text(text = line, color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    RankMovementChip(entry)
                }
            }

            Spacer(Modifier.height(14.dp))

            Column {
                LiveSendSectionLabel(text = "Shared videos")
                Spacer(Modifier.height(10.dp))
                LiveSendCard {
                    when {
                        entry.hasViewableVideo -> Text(
                            text = "${entry.sharedVideoCount} viewable shared video${if (entry.sharedVideoCount == 1) "" else "s"} this week — tap to view →",
                            color = ClimbPalette.liveSendAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onOpenFriendClimbs),
                        )
                        entry.hasPrivateVideo -> Text(text = "🔒 This climber's videos are private.", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
                        else -> Text(text = "No shared videos this week.", color = ClimbPalette.liveSendTextMuted, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
