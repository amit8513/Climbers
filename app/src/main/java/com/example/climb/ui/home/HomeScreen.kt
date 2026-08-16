package com.example.climb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.data.settings.SettingsStore
import com.example.climb.data.social.UserProfile
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.leaderboard.InitialsAvatar
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.livesend.components.LiveSendStatCard
import com.example.climb.ui.progress.averageSentGrade
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import com.example.climb.util.daysBetween
import com.example.climb.util.startOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)
private val dateFormatter = SimpleDateFormat("MMM d", Locale.US)

private fun sendsThisWeek(climbs: List<ClimbEntity>, now: Long): Int =
    climbs.count { it.outcome == ClimbOutcome.SENT && daysBetween(now, it.createdAt) in 0..6 }

private fun dayStreak(climbs: List<ClimbEntity>, now: Long): Int {
    val climbDays = climbs.map { startOfDay(it.createdAt) }.toSet()
    var streak = 0
    var day = startOfDay(now)
    while (climbDays.contains(day)) {
        streak++
        day -= 86_400_000L
    }
    return streak
}

private fun formatClimbDate(createdAt: Long, now: Long): String {
    val diff = daysBetween(now, createdAt)
    return when {
        diff == 0 -> "Today, ${timeFormatter.format(Date(createdAt))}"
        diff == 1 -> "Yesterday"
        diff in 2..6 -> "$diff days ago"
        else -> dateFormatter.format(Date(createdAt))
    }
}

@Composable
fun HomeScreen(
    repository: ClimbRepository,
    currentUid: String,
    profile: UserProfile,
    settingsStore: SettingsStore,
    onClimbClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val climbs by repository.observeAll(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val now = remember { System.currentTimeMillis() }
    val averageGrade = remember(climbs) { averageSentGrade(climbs) }

    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        if (settingsStore.homeVideoBackgroundEnabled) {
            HomeVideoBackground(
                climbs = climbs,
                opacity = settingsStore.homeVideoOpacity,
                style = settingsStore.homeVideoMontageStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatarWithGrade(profile = profile, averageGrade = averageGrade, size = 52.dp)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "CLIMBERS",
                        color = ClimbPalette.liveSendTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp,
                    )
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = ClimbPalette.liveSendTextMuted,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveSendStatCard(
                    value = remember(climbs) { sendsThisWeek(climbs, now) }.toString(),
                    label = "Sends this week",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                LiveSendStatCard(
                    value = remember(climbs) { dayStreak(climbs, now) }.toString(),
                    label = "Day streak",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Spacer(Modifier.height(22.dp))

            // Matches Sends This Week's own card background — per user request that this label,
            // the row date text, and the Sent badge all read as one consistent tone with that card.
            LiveSendSectionLabel(text = "Recent Climbs", modifier = Modifier.padding(horizontal = 20.dp), color = ClimbPalette.liveSendSurface)

            Spacer(Modifier.height(10.dp))

            if (climbs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No climbs yet — tap + to record one.", color = ClimbPalette.liveSendTextMuted)
                }
            } else {
                HorizontalDivider(color = ClimbPalette.liveSendBorder, modifier = Modifier.padding(horizontal = 20.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(climbs, key = { it.id }) { climb ->
                        ClimbRow(climb = climb, now = now, onClick = { onClimbClick(climb.id) })
                        HorizontalDivider(color = ClimbPalette.liveSendBorder)
                    }
                    item {
                        Text(
                            text = "Tap a climb to watch it back. Tap + to record one.",
                            color = ClimbPalette.liveSendTextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The profile picture with a small "V{n}" badge for the average grade sent, overlapping its
 * bottom-right corner — badge is omitted entirely (not shown as "V0" or "—") when there aren't
 * any sends to average yet, since a fabricated grade would be worse than no badge. */
@Composable
private fun ProfileAvatarWithGrade(profile: UserProfile, averageGrade: Double?, size: Dp, modifier: Modifier = Modifier) {
    val accent = ClimbPalette.liveSendAccent
    val ringWidth = 1.5.dp
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .border(ringWidth, accent, CircleShape)
                .padding(ringWidth),
        ) {
            InitialsAvatar(name = profile.username, size = size - ringWidth * 2, photoUrl = profile.photoUrl)
        }
        if (averageGrade != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = size * 0.11f, y = size * 0.11f)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
                    .semantics { contentDescription = "Average grade sent: V${averageGrade.roundToNearestInt()}" }
                    .padding(horizontal = size * 0.11f, vertical = size * 0.03f),
            ) {
                Text(
                    text = "V${averageGrade.roundToNearestInt()}",
                    color = ClimbPalette.liveSendAccentText,
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.24f).sp,
                )
            }
        }
    }
}

private fun Double.roundToNearestInt(): Int = Math.round(this).toInt()

@Composable
private fun ClimbRow(climb: ClimbEntity, now: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldBadge(grade = climb.vGrade, routeColor = climb.routeColor)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = climb.routeColor.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() },
                    color = ClimbPalette.liveSendTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatClimbDate(climb.createdAt, now),
                    color = ClimbPalette.liveSendSurface,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutcomePill(
                    outcome = climb.outcome,
                    backgroundColor = if (climb.outcome == ClimbOutcome.SENT) ClimbPalette.liveSendSurface else null,
                )
                if (climb.notes.isNotBlank()) {
                    Text(
                        text = climb.notes,
                        color = ClimbPalette.liveSendTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

