package com.example.climb.ui.progress

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.ClimbRepository
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import java.util.Locale

/**
 * Same asymmetric silhouette as [com.example.climb.ui.components.HoldBadge], scaled up for the
 * hero. The corner radii are re-declared rather than shared because they only read as a hold
 * when they stay proportional to the badge they're drawn on.
 */
private val heroHoldShape = RoundedCornerShape(
    topStart = 22.dp,
    topEnd = 28.dp,
    bottomEnd = 25.dp,
    bottomStart = 19.dp,
)

@Composable
fun ProgressScreen(repository: ClimbRepository, currentUid: String, modifier: Modifier = Modifier) {
    val climbs by repository.observeAll(currentUid).collectAsStateWithLifecycle(initialValue = emptyList())
    val now = remember { System.currentTimeMillis() }

    val headline = remember(climbs) { headlineStats(climbs) }
    val averageGrade = remember(climbs) { averageSentGrade(climbs) }
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
            // Matches Home's header rhythm — wordmark left, monospace meta right — so the two
            // tabs read as the same app rather than two differently-titled screens.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "PROGRESS",
                    color = ClimbPalette.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "${headline.sessions} sessions",
                    color = ClimbPalette.textMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(16.dp))

            HeroCard(stats = headline, averageGrade = averageGrade)

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Hardest send by week") {
                if (progression.all { it == null }) {
                    EmptyHint("Your weekly high point will chart here.")
                } else {
                    ProgressionChart(progression)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Send rate by grade") {
                if (sendRates.isEmpty()) {
                    EmptyHint("Log a graded climb to see which grades you're consolidating.")
                } else {
                    sendRates.forEach { rate -> SendRateRow(rate) }
                    Caption("Sent attempts ÷ all attempts at that grade.")
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Grade pyramid · last 90 days") {
                if (pyramid.isEmpty()) {
                    EmptyHint("Send a graded climb to start your pyramid.")
                } else {
                    val maxCount = pyramid.maxOf { it.second }
                    val peakGrade = pyramid.maxOf { it.first }
                    pyramid.forEach { (grade, count) ->
                        PyramidRow(
                            grade = grade,
                            count = count,
                            maxCount = maxCount,
                            isPeak = grade == peakGrade,
                        )
                    }
                    Caption("A wide base under a narrow top is a healthy pyramid.")
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Consistency · last $WEEKS_IN_HEATMAP weeks") {
                ConsistencyHeatmap(heatmap)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Peak grade gets the full width and the hold silhouette; the supporting numbers sit under a
 * divider at a smaller weight. Three equal cells gave every stat the same importance, which
 * left the screen with nothing to look at first.
 */
@Composable
private fun HeroCard(stats: HeadlineStats, averageGrade: Double?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClimbPalette.surface)
            .border(1.dp, ClimbPalette.border, shape)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 60.dp)
                    .clip(heroHoldShape)
                    .background(ClimbPalette.chalk)
                    .background(Brush.radialGradient(colors = listOf(ClimbPalette.holdSheen, Color.Transparent)))
                    .border(1.dp, ClimbPalette.borderStrong, heroHoldShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stats.peakGrade?.let { "V$it" } ?: "—",
                    color = ClimbPalette.chalkText,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                )
            }
            Column {
                Text(
                    text = "PEAK GRADE",
                    color = ClimbPalette.textMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (stats.peakGrade == null) {
                        "No graded send logged yet."
                    } else {
                        "Hardest grade you've sent."
                    },
                    color = ClimbPalette.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = ClimbPalette.border)
        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            HeroStat(
                value = stats.sendRatePercent?.let { "$it%" } ?: "—",
                label = "Send rate",
                modifier = Modifier.weight(1f),
            )
            HeroStat(
                value = averageGrade?.let { String.format(Locale.US, "V%.1f", it) } ?: "—",
                label = "Avg send",
                modifier = Modifier.weight(1f),
            )
            HeroStat(
                value = stats.sessions.toString(),
                label = "Sessions",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            color = ClimbPalette.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label.uppercase(Locale.US),
            color = ClimbPalette.textSecondary,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, color = ClimbPalette.textSecondary, fontSize = 13.sp)
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        color = ClimbPalette.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SendRateRow(rate: GradeSendRate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradeLabel("V${rate.grade}")
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(50))
                .background(ClimbPalette.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = rate.percent / 100f)
                    .background(sendRateColor(rate.percent), RoundedCornerShape(50)),
            )
        }
        Text(
            text = "${rate.percent}%",
            color = ClimbPalette.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = "${rate.sends}/${rate.attempts}",
            color = ClimbPalette.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(34.dp),
        )
    }
}

// Green where you're consolidating, amber mid, rust where it's still a project — so the
// grade you're working on reads at a glance instead of needing the numbers.
@Composable
@ReadOnlyComposable
private fun sendRateColor(percent: Int) = when {
    percent >= 60 -> ClimbPalette.sent
    percent >= 30 -> ClimbPalette.project
    else -> ClimbPalette.fell
}

/**
 * A line through the weekly high points rather than eight separate bars. Bars encoded each week
 * in isolation; the shape of the trend is the thing this card is actually about, and blank weeks
 * break the line instead of drawing a misleading zero.
 */
@Composable
private fun ProgressionChart(progression: List<Int?>) {
    val sent = progression.filterNotNull()
    val topGrade = sent.maxOrNull() ?: 0
    val bottomGrade = sent.minOrNull() ?: 0
    val chalk = ClimbPalette.chalk
    val track = ClimbPalette.border
    val surface = ClimbPalette.surface

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.height(100.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            AxisLabel("V$topGrade")
            if (bottomGrade != topGrade) AxisLabel("V$bottomGrade")
        }
        Column(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                val padY = 10.dp.toPx()
                val usable = (size.height - padY * 2).coerceAtLeast(1f)
                val stepX = if (progression.size > 1) size.width / (progression.size - 1) else 0f
                val span = (topGrade - bottomGrade).coerceAtLeast(1)

                drawLine(
                    color = track,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )

                val points = progression.mapIndexedNotNull { index, grade ->
                    grade?.let {
                        val fraction = if (topGrade == bottomGrade) 0.5f else (it - bottomGrade).toFloat() / span
                        Offset(index * stepX, padY + usable * (1f - fraction))
                    }
                }
                if (points.isEmpty()) return@Canvas

                if (points.size > 1) {
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(
                            colors = listOf(chalk.copy(alpha = 0.16f), Color.Transparent),
                        ),
                    )
                    drawPath(
                        path = line,
                        color = chalk,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                points.forEach { drawCircle(color = chalk, radius = 3.dp.toPx(), center = it) }
                // Knock the newest week out of the line so "where am I now" is findable at a glance.
                points.last().let { latest ->
                    drawCircle(color = surface, radius = 5.5.dp.toPx(), center = latest)
                    drawCircle(color = chalk, radius = 4.dp.toPx(), center = latest)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                AxisLabel("-${WEEKS_IN_PROGRESSION - 1}w")
                Spacer(Modifier.weight(1f))
                AxisLabel("now")
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        color = ClimbPalette.textMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
    )
}

/**
 * Bars grow from the centre outward so the rows actually form the pyramid the card is named
 * after — left-aligned bars made the same numbers read as an ordinary bar chart.
 */
@Composable
private fun PyramidRow(grade: Int, count: Int, maxCount: Int, isPeak: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "V$grade",
            color = if (isPeak) ClimbPalette.textPrimary else ClimbPalette.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(26.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = count.toFloat() / maxCount)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPeak) ClimbPalette.chalk else ClimbPalette.chalk.copy(alpha = 0.62f)),
            )
        }
        Text(
            text = count.toString(),
            color = ClimbPalette.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
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
    // grid[week][day], day 0 = Sunday (see util.startOfWeek), so Mon/Wed/Fri land on rows 1/3/5.
    val dayLabels = listOf("", "M", "", "W", "", "F", "")
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 2.dp),
            ) {
                dayLabels.forEach { label ->
                    Box(modifier = Modifier.height(14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = label,
                            color = ClimbPalette.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            grid.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { count ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(heatColor(count)),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Less", color = ClimbPalette.textMuted, fontSize = 11.sp)
            for (level in 0..4) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatColor(level)),
                )
            }
            Text("More", color = ClimbPalette.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun heatColor(count: Int) = when {
    count <= 0 -> ClimbPalette.border
    count == 1 -> ClimbPalette.textPrimary.copy(alpha = 0.14f)
    count == 2 -> ClimbPalette.textPrimary.copy(alpha = 0.32f)
    count == 3 -> ClimbPalette.textPrimary.copy(alpha = 0.55f)
    else -> ClimbPalette.textPrimary.copy(alpha = 0.85f)
}
