package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.climb.ui.theme.ClimbPalette

/**
 * The circular avatar used for the home feed header, activity-feed rows, leaderboard/member
 * rows, and the (bigger, ring-bordered) settings profile photo — a monogram-on-[ClimbPalette.wall]
 * circle by default, or a real photo via [photoUrl] (mirrors the Coil usage already established
 * by [com.example.climb.ui.auth.ProfileSetupScreen]'s ProfilePhotoPicker). [ringed] draws the
 * lime/chalk ring the spec uses on the home-feed header avatar and the settings profile photo.
 */
@Composable
fun LiveSendAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    size: Int = 32,
    photoUrl: String? = null,
    ringed: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(ClimbPalette.wall)
            .then(if (ringed) Modifier.border(2.dp, ClimbPalette.liveSendAccent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initial.take(1).uppercase(),
                color = ClimbPalette.liveSendAccent,
                fontWeight = FontWeight.Black,
                fontSize = (size * 0.4f).sp,
            )
        }
    }
}
