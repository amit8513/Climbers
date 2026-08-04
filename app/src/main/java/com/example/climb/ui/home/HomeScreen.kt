package com.example.climb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbRepository
import com.example.climb.data.RouteColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
fun HomeScreen(
    repository: ClimbRepository,
    onRecordClick: () -> Unit,
    onClimbClick: (Long) -> Unit,
) {
    val climbs by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onRecordClick) { Text("+") }
        },
    ) { padding ->
        if (climbs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No climbs yet — tap + to record one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(climbs, key = { it.id }) { climb ->
                    ClimbRow(climb = climb, onClick = { onClimbClick(climb.id) })
                }
            }
        }
    }
}

@Composable
private fun ClimbRow(climb: ClimbEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(climb.routeColor.hex)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = climb.vGrade?.let { "V$it" } ?: "?",
                color = contrastColorFor(climb.routeColor),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(dateFormatter.format(Date(climb.createdAt)), style = MaterialTheme.typography.bodyMedium)
            Text(climb.outcome.name, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun contrastColorFor(color: RouteColor): Color =
    if (color == RouteColor.WHITE || color == RouteColor.YELLOW) Color.Black else Color.White
