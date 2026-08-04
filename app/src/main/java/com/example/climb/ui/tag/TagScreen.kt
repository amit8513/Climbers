package com.example.climb.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.climb.data.ClimbEntity
import com.example.climb.data.ClimbOutcome
import com.example.climb.data.ClimbRepository
import com.example.climb.data.RouteColor
import kotlinx.coroutines.launch

@Composable
fun TagScreen(
    videoPath: String,
    durationMs: Long,
    repository: ClimbRepository,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var vGrade by remember { mutableStateOf<Int?>(null) }
    var routeColor by remember { mutableStateOf<RouteColor?>(null) }
    var outcome by remember { mutableStateOf(ClimbOutcome.SENT) }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tag this climb", style = MaterialTheme.typography.headlineSmall)

        Text("Route color")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(RouteColor.entries) { color ->
                ColorSwatch(
                    color = color,
                    selected = routeColor == color,
                    onClick = { routeColor = color },
                )
            }
        }

        Text("Grade")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..17).toList()) { grade ->
                FilterChip(
                    selected = vGrade == grade,
                    onClick = { vGrade = if (vGrade == grade) null else grade },
                    label = { Text("V$grade") },
                )
            }
        }

        Text("Outcome")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClimbOutcome.entries.forEach { option ->
                FilterChip(
                    selected = outcome == option,
                    onClick = { outcome = option },
                    label = { Text(option.name) },
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Button(
            enabled = routeColor != null && !saving,
            onClick = {
                val color = routeColor ?: return@Button
                saving = true
                scope.launch {
                    repository.save(
                        ClimbEntity(
                            videoPath = videoPath,
                            createdAt = System.currentTimeMillis(),
                            durationMs = durationMs,
                            vGrade = vGrade,
                            routeColor = color,
                            outcome = outcome,
                            notes = notes,
                        ),
                    )
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving..." else "Save climb")
        }
    }
}

@Composable
private fun ColorSwatch(color: RouteColor, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color.hex))
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = if (color == RouteColor.WHITE || color == RouteColor.YELLOW) Color.Black else Color.White,
                )
            }
        }
    }
}
