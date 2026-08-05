package com.example.climb.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.leaderboard.model.LeaderboardCategory
import com.example.climb.ui.theme.ClimbPalette

@Composable
fun LeaderboardTabs(selectedCategory: LeaderboardCategory, onSelect: (LeaderboardCategory) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeaderboardCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) ClimbPalette.chalk else ClimbPalette.surface)
                    .border(1.dp, if (isSelected) ClimbPalette.chalk else ClimbPalette.border, RoundedCornerShape(50))
                    .clickable(onClick = { onSelect(category) })
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                        contentDescription = "${category.tabTitle} category"
                    },
            ) {
                Text(
                    text = category.tabTitle,
                    color = if (isSelected) ClimbPalette.chalkText else ClimbPalette.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
