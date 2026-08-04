package com.example.climb.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import com.example.climb.util.startOfDay
import com.example.climb.util.startOfWeek

private const val WEEKS_IN_BAR_CHART = 8
private const val WEEKS_IN_HEATMAP = 10
private const val DAY_MILLIS = 86_400_000L
private const val WEEK_MILLIS = 7 * DAY_MILLIS

private fun gradePyramid(climbs: List<ClimbEntity>): List<Pair<Int, Int>> =
    climbs.filter { it.outcome == ClimbOutcome.SENT && it.vGrade != null }
        .groupBy { it.vGrade!! }
        .map { (grade, list) -> grade to list.size }
        .sortedByDescending { it.first }

private fun sendsByWeek(climbs: List<ClimbEntity>, now: Long): List<Int> {
    val currentWeekStart = startOfWeek(now)
    val counts = IntArray(WEEKS_IN_BAR_CHART)
    for (climb in climbs) {
        if (climb.outcome != ClimbOutcome.SENT) continue
        val weeksAgo = ((currentWeekStart - startOfWeek(climb.createdAt)) / WEEK_MILLIS).toInt()
        if (weeksAgo in 0 until WEEKS_IN_BAR_CHART) {
            counts[WEEKS_IN_BAR_CHART - 1 - weeksAgo]++
        }
    }
    return counts.toList()
}

private fun consistencyGrid(climbs: List<ClimbEntity>, now: Long): List<List<Int>> {
    val currentWeekStart = startOfWeek(now)
    val grid = Array(WEEKS_IN_HEATMAP) { IntArray(7) }
    for (climb in climbs) {
        val weeksAgo = ((currentWeekStart - startOfWeek(climb.createdAt)) / WEEK_MILLIS).toInt()
        if (weeksAgo !in 0 until WEEKS_IN_HEATMAP) continue
        val dayIndex = ((startOfDay(climb.createdAt) - startOfWeek(climb.createdAt)) / DAY_MILLIS).toInt()
        if (dayIndex in 0..6) {
            grid[WEEKS_IN_HEATMAP - 1 - weeksAgo][dayIndex]++
        }
    }
    return grid.map { it.toList() }
}

@Composable
fun ProgressScreen(repository: ClimbRepository, modifier: Modifier = Modifier) {
    val climbs by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val now = remember { System.currentTimeMillis() }

    val pyramid = remember(climbs) { gradePyramid(climbs) }
    val weeklySends = remember(climbs) { sendsByWeek(climbs, now) }
    val heatmap = remember(climbs) { consistencyGrid(climbs, now) }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "CLIMB",
                color = ClimbPalette.textMuted,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Progress",
                color = ClimbPalette.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )

            Spacer(Modifier.height(18.dp))

            SectionCard(title ="Grade pyramid") {
                if (pyramid.isEmpty()) {
                    EmptyCardHint("Send a graded climb to start your pyramid.")
                } else {
                    val maxCount = pyramid.maxOf { it.second }
                    pyramid.forEach { (grade, count) ->
                        PyramidRow(grade = grade, count = count, maxCount = maxCount)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title ="Sends by week") {
                if (weeklySends.sum() == 0) {
                    EmptyCardHint("Your weekly send count will show up here.")
                } else {
                    WeeklyBars(weeklySends)
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title ="Consistency") {
                ConsistencyHeatmap(heatmap)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyCardHint(text: String) {
    Text(text = text, color = ClimbPalette.textSecondary, fontSize = 13.sp)
}

@Composable
private fun PyramidRow(grade: Int, count: Int, maxCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "V$grade",
            color = ClimbPalette.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(24.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ClimbPalette.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = count.toFloat() / maxCount)
                    .background(ClimbPalette.chalk, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = count.toString(),
            color = ClimbPalette.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(18.dp),
        )
    }
}

@Composable
private fun WeeklyBars(weeklySends: List<Int>) {
    val maxCount = (weeklySends.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(90.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeklySends.forEachIndexed { index, count ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val barHeight = maxOf(2.dp, (70 * (count.toFloat() / maxCount)).dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .background(ClimbPalette.chalk, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "W${index + 1}",
                    color = ClimbPalette.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun ConsistencyHeatmap(grid: List<List<Int>>) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            grid.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { count ->
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(heatColor(count)),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Less", color = ClimbPalette.textMuted, fontSize = 10.sp)
            for (level in 0..4) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatColor(level)),
                )
            }
            Text("More", color = ClimbPalette.textMuted, fontSize = 10.sp)
        }
    }
}

private fun heatColor(count: Int) = when {
    count <= 0 -> ClimbPalette.border
    count == 1 -> ClimbPalette.textPrimary.copy(alpha = 0.14f)
    count == 2 -> ClimbPalette.textPrimary.copy(alpha = 0.32f)
    count == 3 -> ClimbPalette.textPrimary.copy(alpha = 0.55f)
    else -> ClimbPalette.textPrimary.copy(alpha = 0.85f)
}
