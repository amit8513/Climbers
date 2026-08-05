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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.ui.components.HoldBadge
import com.example.climb.ui.components.OutcomePill
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import com.example.climb.util.daysBetween
import com.example.climb.util.startOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.US)
private val dateFormatter = SimpleDateFormat("MMM d", Locale.US)
private val headerDateFormatter = SimpleDateFormat("MMM d", Locale.US)

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
    onClimbClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val climbs by repository.observeAll(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val now = remember { System.currentTimeMillis() }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "CLIMB",
                    color = ClimbPalette.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = headerDateFormatter.format(Date(now)),
                    color = ClimbPalette.textMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(18.dp))

            StatsStrip(
                sends = remember(climbs) { sendsThisWeek(climbs, now) },
                streak = remember(climbs) { dayStreak(climbs, now) },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .height(IntrinsicSize.Min),
            )

            Spacer(Modifier.height(22.dp))

            Text(
                text = "RECENT CLIMBS",
                color = ClimbPalette.textMuted,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(10.dp))

            if (climbs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No climbs yet — tap + to record one.", color = ClimbPalette.textSecondary)
                }
            } else {
                HorizontalDivider(color = ClimbPalette.border, modifier = Modifier.padding(horizontal = 20.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(climbs, key = { it.id }) { climb ->
                        ClimbRow(climb = climb, now = now, onClick = { onClimbClick(climb.id) })
                        HorizontalDivider(color = ClimbPalette.border)
                    }
                    item {
                        Text(
                            text = "Tap a climb to watch it back. Tap + to record one.",
                            color = ClimbPalette.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsStrip(sends: Int, streak: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, ClimbPalette.border, shape),
    ) {
        StatCell(value = sends, label = "Sends this week", modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ClimbPalette.border),
        )
        StatCell(value = streak, label = "Day streak", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ClimbPalette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = value.toString(), color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = label.uppercase(Locale.US), color = ClimbPalette.textSecondary, fontSize = 11.sp, letterSpacing = 0.6.sp)
    }
}

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
                    color = ClimbPalette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatClimbDate(climb.createdAt, now),
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutcomePill(outcome = climb.outcome)
                if (climb.notes.isNotBlank()) {
                    Text(
                        text = climb.notes,
                        color = ClimbPalette.textSecondary,
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

