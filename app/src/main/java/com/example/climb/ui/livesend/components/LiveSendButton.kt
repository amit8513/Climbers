package com.example.climb.ui.livesend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.climb.ui.theme.ClimbPalette

/**
 * The full-width pill CTA button that recurs across every Live Send flow screen — Onboarding's
 * "Get Started", Login's "Log In", Signup's "Create Account", RouteDetail's "Log Attempt", and
 * Profile's "Enter Club Mode". Figma always pairs it with the same fixed vivid red
 * ([ClimbPalette.liveSendCta]) rather than the theme's [ClimbPalette.chalk] accent, so that stays
 * the default; pass [containerColor] to reuse the shape for a differently-colored pill action.
 */
@Composable
fun LiveSendPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Int = 56,
    containerColor: Color = ClimbPalette.liveSendCta,
    contentColor: Color = ClimbPalette.liveSendTextPrimary,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = contentColor)
        } else {
            Text(text = text, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
