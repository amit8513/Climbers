package com.example.climb.ui.livesend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.livesend.components.GradeBadge
import com.example.climb.ui.livesend.components.LiveSendBottomBar
import com.example.climb.ui.livesend.components.LiveSendCard
import com.example.climb.ui.livesend.components.LiveSendChartBar
import com.example.climb.ui.livesend.components.LiveSendFab
import com.example.climb.ui.livesend.components.LiveSendNavTab
import com.example.climb.ui.livesend.components.LiveSendProgressBar
import com.example.climb.ui.livesend.components.LiveSendSectionLabel
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture

/**
 * Live Send (Alternative UI Concept 2) — Progress tab (Figma node 5:308).
 *
 * A read-only stats dashboard: this month's peak-grade summary card, a 5-week "hardest send"
 * bar chart, and a send-rate-by-grade breakdown, floating over the concept's own bottom nav bar
 * with its central record FAB. Pure presentation — [ProgressScreen] takes no data params because
 * the spec's numbers (V7 peak, 100% send rate, weekly bar heights, per-grade rates) are static
 * content for this design exploration, matching how the rest of Live Send's screens were scoped.
 *
 * All five interactive elements are the shared bottom bar's 4 tabs plus the floating record FAB;
 * there is no back affordance on this screen (it's a root tab destination, mirroring how the
 * shipped [com.example.climb.navigation.MemberClubNavHost] tabs have none either).
 */
@Composable
fun ProgressScreen(
    onFeedClick: () -> Unit,
    onProgressClick: () -> Unit,
    onRanksClick: () -> Unit,
    onClubClick: () -> Unit,
    onLogAttempt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().wallTexture(bg = ClimbPalette.liveSendBg, dot = ClimbPalette.liveSendTextPrimary.copy(alpha = 0.05f))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Progress",
                color = ClimbPalette.liveSendTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                modifier = Modifier.semantics { heading() },
            )

            // PeakCard (5:478) — this month's hardest send plus the three headline stats.
            LiveSendCard(cornerRadius = 20) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradeBadge(grade = "V7", cornerRadius = 16.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Peak Grade — hardest send this month",
                        color = ClimbPalette.liveSendTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("100% Send Rate", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("V5.0 Avg Send", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("2 Sessions", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // ChartCard (5:485) — "Hardest Send by Week" bar chart, 5 bars (5:487..5:491).
            LiveSendCard(cornerRadius = 20) {
                LiveSendSectionLabel(text = "Hardest Send by Week", fontSize = 13, forceUppercase = false)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(40, 55, 70, 50, 90).forEach { barHeight ->
                        LiveSendChartBar(heightFraction = barHeight / 90f, width = 30, maxHeight = 90)
                    }
                }
            }

            // SendRateCard (5:492) — per-grade send rate rows (5:494/495/496, 5:497/498/499).
            LiveSendCard(cornerRadius = 20) {
                LiveSendSectionLabel(text = "Send Rate by Grade", fontSize = 13, forceUppercase = false)
                Spacer(modifier = Modifier.height(14.dp))
                Text("V7 · 100%", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Normal, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                LiveSendProgressBar(progress = 1f)
                Spacer(modifier = Modifier.height(14.dp))
                Text("V4 · 100%", color = ClimbPalette.liveSendTextPrimary, fontWeight = FontWeight.Normal, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                LiveSendProgressBar(progress = 1f)
            }
        }

        // NavBar (5:500) + Fab (5:501/38:642) — floating pill nav with the record FAB centered
        // above it, per LiveSendFab's own doc comment describing this exact overlap.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            LiveSendBottomBar(
                tabs = listOf(
                    LiveSendNavTab(Icons.Filled.Home, "Feed", selected = false, onClick = onFeedClick),
                    LiveSendNavTab(Icons.Filled.QueryStats, "Progress", selected = true, onClick = onProgressClick),
                    LiveSendNavTab(Icons.Filled.EmojiEvents, "Ranks", selected = false, onClick = onRanksClick),
                    LiveSendNavTab(Icons.Filled.Group, "Club", selected = false, onClick = onClubClick),
                ),
            )
            LiveSendFab(
                onClick = onLogAttempt,
                icon = Icons.Filled.Add,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-40).dp)
                    .semantics { contentDescription = "Log a new attempt" },
            )
        }
    }
}
