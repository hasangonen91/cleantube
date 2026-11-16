package com.cleantube.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Dark = darkColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0A14),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFF1F1F1),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    secondaryContainer = Color(0xFF272727),
    onSecondaryContainer = Color.White,
    outline = Color(0xFF303030),
)

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF0F0F0F).toArgb()
            window.navigationBarColor = Color(0xFF0F0F0F).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = Dark, content = content)
}
