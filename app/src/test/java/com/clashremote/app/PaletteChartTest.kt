package com.clashremote.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.clashremote.app.ui.ClashRemoteTheme
import com.clashremote.core.AppPalette
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaletteChartTest {
    @get:Rule val compose = createComposeRule()
    private fun verifyTraces() {
        var palette by mutableStateOf(AppPalette.GREEN)
        var colors: Pair<Color, Color>? = null
        compose.setContent {
            ClashRemoteTheme(palette) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { colors = scheme.primary to scheme.secondary }
                Box {}
            }
        }
        AppPalette.entries.forEach { selected ->
            compose.runOnIdle { palette = selected }
            compose.waitForIdle()
            val pair = requireNotNull(colors)
            assertNotEquals("${selected.label} must keep download and upload colors distinct", pair.first, pair.second)
        }
    }
    @Test fun lightPalettesDistinguishTrafficDirections() = verifyTraces()
    @Test @Config(qualifiers = "night") fun darkPalettesDistinguishTrafficDirections() = verifyTraces()
}
