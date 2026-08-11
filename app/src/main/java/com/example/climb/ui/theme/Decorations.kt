package com.example.climb.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Faint grid of dots evoking a bouldering wall's t-nut bolt holes.
 *
 * Uses [composed] so the modifier can read the active theme: the draw lambda itself isn't
 * composable, so the colours are captured here and closed over. Defaults follow the live
 * theme; pass fixed colors (e.g. `ClimbPalette.liveSendBg`) for a screen whose palette must
 * not shift with the theme, like the "Live Send" concept under ui/livesend/.
 */
fun Modifier.wallTexture(bg: Color = ClimbPalette.bg, dot: Color = ClimbPalette.textureDot): Modifier = composed {
    this
        .background(bg)
        .drawBehind {
            val spacing = 27.dp.toPx()
            val radius = 1.4.dp.toPx()
            val offset = 9.dp.toPx()
            var y = offset
            while (y < size.height) {
                var x = offset
                while (x < size.width) {
                    drawCircle(color = dot, radius = radius, center = Offset(x, y))
                    x += spacing
                }
                y += spacing
            }
        }
}
