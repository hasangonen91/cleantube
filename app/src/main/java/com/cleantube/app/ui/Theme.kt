package com.cleantube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF4E45), onPrimary = Color.White,
    background = Color(0xFF0F0F0F), onBackground = Color.White,
    surface = Color(0xFF0F0F0F), onSurface = Color.White,
    surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFAAAAAA),
    secondaryContainer = Color(0xFF272727), onSecondaryContainer = Color.White,
)

@Composable
fun CleanTubeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, content = content)
