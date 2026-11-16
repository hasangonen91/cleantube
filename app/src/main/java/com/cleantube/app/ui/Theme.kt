package com.cleantube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C0017),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFFF8A9B),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF101013),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF242428),
    onSurfaceVariant = Color(0xFFAAAAB2),
    secondaryContainer = Color(0xFF29292E),
    onSecondaryContainer = Color.White,
)

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, content = content)
