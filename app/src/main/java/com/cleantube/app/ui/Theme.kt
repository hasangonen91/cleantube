package com.cleantube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF315B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF64152B),
    onPrimaryContainer = Color(0xFFFFDCE4),
    secondary = Color(0xFFFF8BA6),
    background = Color(0xFF08080B),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF111016),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF24202A),
    onSurfaceVariant = Color(0xFFAAAAB2),
    secondaryContainer = Color(0xFF29292E),
    onSecondaryContainer = Color.White,
)

@Composable
fun VyroTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, content = content)
