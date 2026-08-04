package com.example.climb.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** A bar shape with a circular bite cut out of the top-center edge, for a docked FAB. */
class NotchedBarShape(
    private val notchRadius: Dp,
    private val cornerRadius: Dp = 20.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val notchRadiusPx = with(density) { notchRadius.toPx() }
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }
        val topCorner = CornerRadius(cornerRadiusPx)

        val barPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    topLeft = topCorner,
                    topRight = topCorner,
                    bottomRight = CornerRadius.Zero,
                    bottomLeft = CornerRadius.Zero,
                ),
            )
        }

        val notchPath = Path().apply {
            addOval(
                Rect(
                    left = size.width / 2f - notchRadiusPx,
                    top = -notchRadiusPx,
                    right = size.width / 2f + notchRadiusPx,
                    bottom = notchRadiusPx,
                ),
            )
        }

        val resultPath = Path()
        resultPath.op(barPath, notchPath, PathOperation.Difference)
        return Outline.Generic(resultPath)
    }
}
