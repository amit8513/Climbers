package com.example.climb.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.leaderboard.period.PeriodFilter
import com.example.climb.ui.theme.ClimbPalette

@Composable
fun PeriodSelector(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ClimbPalette.liveSendSurface)
                .border(1.dp, ClimbPalette.liveSendBorder, RoundedCornerShape(50))
                .clickable(onClick = { expanded = true })
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "Period: ${selected.label}. Tap to change."
                },
        ) {
            Text(text = "${selected.label} ▾", color = ClimbPalette.liveSendTextPrimary, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PeriodFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label) },
                    onClick = {
                        onSelect(filter)
                        expanded = false
                    },
                )
            }
        }
    }
}
