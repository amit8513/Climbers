package com.example.climb.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** Faint grid of dots evoking a bouldering wall's t-nut bolt holes. */
fun Modifier.wallTexture(): Modifier = this
    .background(ClimbPalette.bg)
    .drawBehind {
        val spacing = 27.dp.toPx()
        val radius = 1.4.dp.toPx()
        val offset = 9.dp.toPx()
        var y = offset
        while (y < size.height) {
            var x = offset
            while (x < size.width) {
                drawCircle(color = ClimbPalette.textureDot, radius = radius, center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
