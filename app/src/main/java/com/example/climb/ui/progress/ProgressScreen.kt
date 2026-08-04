package com.example.climb.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import com.example.climb.data.ClimbRepository
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

@Composable
fun ProgressScreen(repository: ClimbRepository, modifier: Modifier = Modifier) {
    val climbs by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val now = remember { System.currentTimeMillis() }

    val headline = remember(climbs) { headlineStats(climbs) }
    val pyramid = remember(climbs) { gradePyramid(climbs, now) }
    val sendRates = remember(climbs) { sendRateByGrade(climbs) }
    val progression = remember(climbs) { gradeProgression(climbs, now) }
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

            HeadlineRow(headline, modifier = Modifier.height(IntrinsicSize.Min))

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Send rate by grade") {
                if (sendRates.isEmpty()) {
                    EmptyHint("Log a graded climb to see which grades you're consolidating.")
                } else {
                    sendRates.forEach { rate ->
                        SendRateRow(rate)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sent attempts ÷ all attempts at that grade.",
                        color = ClimbPalette.textMuted,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Hardest send by week") {
                if (progression.all { it == null }) {
                    EmptyHint("Your weekly high point will chart here.")
                } else {
                    ProgressionChart(progression)
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Grade pyramid · last 90 days") {
                if (pyramid.isEmpty()) {
                    EmptyHint("Send a graded climb to start your pyramid.")
                } else {
                    val maxCount = pyramid.maxOf { it.second }
                    pyramid.forEach { (grade, count) ->
                        PyramidRow(grade = grade, count = count, maxCount = maxCount)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "A wide base under a narrow top is a healthy pyramid.",
                        color = ClimbPalette.textMuted,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionCard(title = "Consistency") {
                ConsistencyHeatmap(heatmap)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeadlineRow(stats: HeadlineStats, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, ClimbPalette.border, shape),
    ) {
        HeadlineCell(
            value = stats.peakGrade?.let { "V$it" } ?: "—",
            label = "Peak grade",
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        HeadlineCell(
            value = stats.sendRatePercent?.let { "$it%" } ?: "—",
            label = "Send rate",
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        HeadlineCell(
            value = stats.sessions.toString(),
            label = "Sessions",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CellDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(ClimbPalette.border),
    )
}

@Composable
private fun HeadlineCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ClimbPalette.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = value,
            color = ClimbPalette.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            color = ClimbPalette.textSecondary,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, color = ClimbPalette.textSecondary, fontSize = 13.sp)
}

@Composable
private fun SendRateRow(rate: GradeSendRate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GradeLabel("V${rate.grade}")
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
                    .fillMaxWidth(fraction = rate.percent / 100f)
                    .background(sendRateColor(rate.percent), RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = "${rate.percent}%",
            color = ClimbPalette.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = "${rate.sends}/${rate.attempts}",
            color = ClimbPalette.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(34.dp),
        )
    }
}

// Green where you're consolidating, amber mid, rust where it's still a project — so the
// grade you're working on reads at a glance instead of needing the numbers.
private fun sendRateColor(percent: Int) = when {
    percent >= 60 -> ClimbPalette.sent
    percent >= 30 -> ClimbPalette.project
    else -> ClimbPalette.fell
}

@Composable
private fun ProgressionChart(progression: List<Int?>) {
    val maxGrade = progression.filterNotNull().maxOrNull() ?: 0
    val scaleTop = (maxGrade + 1).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        progression.forEachIndexed { index, grade ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = grade?.let { "V$it" } ?: "",
                    color = ClimbPalette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
                Spacer(Modifier.height(3.dp))
                val fraction = if (grade == null) 0f else (grade + 1).toFloat() / scaleTop
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((70 * fraction).dp.coerceAtLeast(2.dp))
                        .background(
                            if (grade == null) ClimbPalette.border else ClimbPalette.chalk,
                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = weekLabel(progression.size - 1 - index),
                    color = ClimbPalette.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

private fun weekLabel(weeksAgo: Int) = if (weeksAgo == 0) "now" else "-$weeksAgo"

@Composable
private fun PyramidRow(grade: Int, count: Int, maxCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GradeLabel("V$grade")
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
private fun GradeLabel(text: String) {
    Text(
        text = text,
        color = ClimbPalette.textSecondary,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.width(26.dp),
    )
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
