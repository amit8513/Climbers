package com.example.climb.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.leaderboard.data.LeaderboardRepository
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.leaderboard.model.LeaderboardEntry
import com.example.climb.leaderboard.model.LeaderboardResult
import com.example.climb.leaderboard.period.LeaderboardPeriodProvider
import com.example.climb.leaderboard.period.PeriodFilter
import com.example.climb.ui.components.SectionCard
import com.example.climb.ui.theme.ClimbPalette
import com.example.climb.ui.theme.wallTexture
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data class Loaded(val result: LeaderboardResult, val lastUpdatedAt: Long?) : LeaderboardUiState
    data class Error(val message: String) : LeaderboardUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentUid: String,
    leaderboardRepository: LeaderboardRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(LeaderboardCategory.OVERALL) }
    var periodFilter by remember { mutableStateOf(PeriodFilter.THIS_WEEK) }
    var uiState by remember { mutableStateOf<LeaderboardUiState>(LeaderboardUiState.Loading) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<LeaderboardEntry?>(null) }

    selectedEntry?.let { entry ->
        LeaderboardProfileScreen(entry = entry, category = category, onBack = { selectedEntry = null }, modifier = modifier)
        return
    }

    val period = remember(periodFilter) { LeaderboardPeriodProvider.periodFor(periodFilter) }

    suspend fun load(forceRefresh: Boolean) {
        runCatching {
            if (forceRefresh) leaderboardRepository.refreshLeaderboard(currentUid, category, period)
            else leaderboardRepository.getLeaderboard(currentUid, category, period)
        }.onSuccess { result ->
            uiState = LeaderboardUiState.Loaded(result, leaderboardRepository.lastUpdatedAt(category, period))
        }.onFailure { error ->
            uiState = LeaderboardUiState.Error(error.message ?: "Something went wrong")
        }
    }

    LaunchedEffect(category, period.id) {
        // Keep whatever's on screen visible while a category/period switch loads in the
        // background, rather than flashing back to a bare skeleton every time.
        if (uiState !is LeaderboardUiState.Loaded) uiState = LeaderboardUiState.Loading
        load(forceRefresh = false)
    }

    Box(modifier = modifier.fillMaxSize().wallTexture()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LeaderboardHeader(periodFilter = periodFilter, onPeriodSelect = { periodFilter = it })
            LeaderboardTabs(
                selectedCategory = category,
                onSelect = { category = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )

            when (val state = uiState) {
                is LeaderboardUiState.Loading -> LoadingSkeleton()
                is LeaderboardUiState.Error -> ErrorState(state.message, onRetry = { scope.launch { load(forceRefresh = false) } })
                is LeaderboardUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            load(forceRefresh = true)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LeaderboardContent(state.result, category, currentUid, state.lastUpdatedAt, onOpenEntry = { selectedEntry = it })
                }
            }
        }
    }
}

@Composable
private fun LeaderboardHeader(periodFilter: PeriodFilter, onPeriodSelect: (PeriodFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(text = "LEADERBOARD", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 0.5.sp)
            Text(text = "See how you stack up with your friends.", color = ClimbPalette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        PeriodSelector(selected = periodFilter, onSelect = onPeriodSelect)
    }
}

@Composable
private fun LeaderboardContent(
    result: LeaderboardResult,
    category: LeaderboardCategory,
    currentUid: String,
    lastUpdatedAt: Long?,
    onOpenEntry: (LeaderboardEntry) -> Unit,
) {
    if (result.entries.isEmpty()) {
        EmptyState()
        return
    }

    val podiumEntries = result.entries.take(3)
    val rowEntries = result.entries.drop(3)
    val showStickyRow = result.entries.none { it.userId == currentUid } && result.currentUserEntry != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(text = category.podiumTitle, color = ClimbPalette.textMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
            LeaderboardPodium(podiumEntries, category, onEntryClick = onOpenEntry)
            lastUpdatedAt?.let {
                Text(
                    text = "Updated ${SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(it))}",
                    color = ClimbPalette.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
        }

        items(rowEntries, key = { it.userId }) { entry ->
            LeaderboardRow(entry = entry, category = category, onClick = { onOpenEntry(entry) }, onOpenVideos = { onOpenEntry(entry) })
        }

        if (showStickyRow) {
            item(key = "sticky_${result.currentUserEntry?.userId}") {
                result.currentUserEntry?.let {
                    StickyCurrentUserRow(it, category, onClick = { onOpenEntry(it) })
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionCard(title = "How scoring works") {
                Text(text = category.scoringExplanation, color = ClimbPalette.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        item {
            Text(
                text = "Leaderboard updates every Monday at 12:00 AM.",
                color = ClimbPalette.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = ClimbPalette.chalk, strokeWidth = 2.dp, modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "No rankings yet", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            text = "Add friends and log some climbs to start this week's leaderboard.",
            color = ClimbPalette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Couldn't load the leaderboard", color = ClimbPalette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = message, color = ClimbPalette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        TextButton(onClick = onRetry) {
            Text("Try again", color = ClimbPalette.chalk, fontWeight = FontWeight.Bold)
        }
    }
}
