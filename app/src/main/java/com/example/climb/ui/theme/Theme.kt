package com.example.climb.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ClimbDark = darkColorScheme(
    primary = Color(0xFFE53935),
    secondary = Color(0xFF43A047),
)

private val ClimbLight = lightColorScheme(
    primary = Color(0xFFE53935),
    secondary = Color(0xFF43A047),
)

@Composable
fun ClimbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> ClimbDark
        else -> ClimbLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
