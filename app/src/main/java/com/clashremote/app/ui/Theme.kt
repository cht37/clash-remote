package com.clashremote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.clashremote.core.AppPalette

fun paletteAccent(palette: AppPalette) = Color(when (palette) {
    AppPalette.GREEN -> 0xFF186B52
    AppPalette.BLUE -> 0xFF275EB8
    AppPalette.PURPLE -> 0xFF6F42A5
    AppPalette.ORANGE -> 0xFF975113
})
private fun scheme(palette: AppPalette, dark: Boolean): ColorScheme {
    val colors = when (palette) {
        AppPalette.GREEN -> if (dark) listOf(0xFF8CCFB1, 0xFF11382A, 0xFF254E3D, 0xFFC7EDDC) else listOf(0xFF186B52, 0xFFFFFFFF, 0xFFD3EDDF, 0xFF103D2F)
        AppPalette.BLUE -> if (dark) listOf(0xFFACC7FF, 0xFF142C54, 0xFF21496F, 0xFFDAEAFF) else listOf(0xFF275EB8, 0xFFFFFFFF, 0xFFCFE1FF, 0xFF132E65)
        AppPalette.PURPLE -> if (dark) listOf(0xFFD7B6FF, 0xFF3D195B, 0xFF56306F, 0xFFF0DBFF) else listOf(0xFF6F42A5, 0xFFFFFFFF, 0xFFEEDCFF, 0xFF391763)
        AppPalette.ORANGE -> if (dark) listOf(0xFFF8B987, 0xFF512B0B, 0xFF6E411A, 0xFFFFE0C4) else listOf(0xFF975113, 0xFFFFFFFF, 0xFFFFDDBB, 0xFF472300)
    }.map { Color(it) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors[0], onPrimary = colors[1], primaryContainer = colors[2], onPrimaryContainer = colors[3],
        secondary = colors[0], onSecondary = colors[1], secondaryContainer = colors[2], onSecondaryContainer = colors[3],
        tertiary = colors[0], onTertiary = colors[1], tertiaryContainer = colors[2], onTertiaryContainer = colors[3],
        surfaceTint = colors[0],
        background = Color(if (dark) 0xFF111720 else 0xFFF5F7F9), onBackground = Color(if (dark) 0xFFE3EAF2 else 0xFF18252A),
        surface = Color(if (dark) 0xFF151C25 else 0xFFF9FCFD), onSurface = Color(if (dark) 0xFFE3EAF2 else 0xFF18252A),
        surfaceVariant = Color(if (dark) 0xFF2D3641 else 0xFFE3EAF0), onSurfaceVariant = Color(if (dark) 0xFFBEC9D5 else 0xFF4A5966),
        surfaceContainerLowest = Color(if (dark) 0xFF0C121A else 0xFFFFFFFF),
        surfaceContainerLow = Color(if (dark) 0xFF19212C else 0xFFF1F4F7),
        surfaceContainer = Color(if (dark) 0xFF1D2632 else 0xFFEDF0F5),
        surfaceContainerHigh = Color(if (dark) 0xFF26303D else 0xFFE6EBF1),
        surfaceContainerHighest = Color(if (dark) 0xFF2D3847 else 0xFFDFE5ED),
    )
}
@Composable fun ClashRemoteTheme(palette: AppPalette = AppPalette.GREEN, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(palette, isSystemInDarkTheme()), content = content)
}
