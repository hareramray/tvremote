package com.tvremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RemoteColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1D3A),
    primaryContainer = Color(0xFF2B4A80),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondaryContainer = Color(0xFF2A3140),
    onSecondaryContainer = Color(0xFFDDE3F0),
    tertiaryContainer = Color(0xFF4A3A1C),
    onTertiaryContainer = Color(0xFFFFE2B0),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1E232D),
    onSurfaceVariant = Color(0xFFC3C8D4),
    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = Color(0xFF13161C),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1B2029),
    surfaceContainerHighest = Color(0xFF232833),
    outlineVariant = Color(0xFF3A4150),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RemoteColors, content = content)
}
