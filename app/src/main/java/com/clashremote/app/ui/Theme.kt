package com.clashremote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF186B52), onPrimary = Color.White, primaryContainer = Color(0xFFD3EDDF),
    onPrimaryContainer = Color(0xFF103D2F), secondary = Color(0xFF4A6660),
    background = Color(0xFFF3F6F4), surface = Color(0xFFF9FCFA),
    surfaceVariant = Color(0xFFE4ECE7), onSurface = Color(0xFF182823),
    onSurfaceVariant = Color(0xFF53655C), outline = Color(0xFF788A80),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8CCFB1), onPrimary = Color(0xFF11382A), primaryContainer = Color(0xFF254E3D),
    onPrimaryContainer = Color(0xFFC7EDDC), secondary = Color(0xFFAFCBC0),
    background = Color(0xFF15221D), surface = Color(0xFF1A2A22), surfaceVariant = Color(0xFF2D4035),
    onSurface = Color(0xFFE2EEE6), onSurfaceVariant = Color(0xFFB1C3B8),
)
@Composable fun ClashRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
