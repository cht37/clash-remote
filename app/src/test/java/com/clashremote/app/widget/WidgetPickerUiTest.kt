package com.clashremote.app.widget

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.clashremote.app.ui.ClashRemoteTheme
import com.clashremote.core.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val binding = WidgetBinding(7, "代理/🚀", "binding-7")
    private var ticket: WidgetTicket? = WidgetTicket(7, "binding-7", "revision-1", "request-1")
    private val snapshot = ControlSnapshot("rule", mapOf(
        "代理/🚀" to ProxyInfo("代理/🚀", "Selector", listOf("香港/🚀 01", "日本 01"), "香港/🚀 01"),
        "视频" to ProxyInfo("视频", "Selector", listOf("美国 01"), "美国 01"),
        "自动选择" to ProxyInfo("自动选择", "URLTest", listOf("香港/🚀 01"), "香港/🚀 01")))
    private val selected = mutableListOf<WidgetCommand.Select>()
    private val saved = mutableListOf<String>()
    private var failSelection = false
    private var gate: CompletableDeferred<ControlSnapshot?>? = null
    private fun launch(configuring: Boolean = false): WidgetPickerViewModel {
        val vm = WidgetPickerViewModel(configuring, binding,
            loadControls = { WidgetControls("家庭路由器", "revision-1", snapshot) },
            currentTicket = { ticket }, selectNode = { _, command ->
                selected += command
                if (failSelection) throw ClashException("切换失败，请重试")
                gate?.await() ?: snapshot
            }, bindGroup = { group, _ -> saved += group })
        compose.setContent { ClashRemoteTheme { WidgetPickerScreen(vm, onClose = {}) } }
        compose.waitForIdle()
        return vm
    }
    @Test fun searchAndClickSubmitExactNodeAndGroup() {
        val vm = launch()
        compose.onNodeWithTag("widget-search").performTextReplacement("日本")
        compose.onNodeWithText("日本 01").performClick()
        compose.waitForIdle()
        assertEquals(listOf(WidgetCommand.Select("代理/🚀", "日本 01")), selected)
        assertTrue(vm.state.value.completed)
    }
    @Test fun failedSelectionKeepsSearchAndCurrentNode() {
        failSelection = true
        val vm = launch()
        compose.onNodeWithTag("widget-search").performTextReplacement("日本")
        compose.onNodeWithText("日本 01").performClick()
        compose.onNodeWithText("切换失败，请重试").assertIsDisplayed()
        compose.onNodeWithTag("widget-search").assertTextContains("日本")
        assertEquals("香港/🚀 01", vm.state.value.current)
        assertFalse(vm.state.value.completed)
    }
    @Test fun configurationOnlyOffersSelectorsAndSavesSelectedGroup() {
        launch(configuring = true)
        compose.onNodeWithText("自动选择").assertDoesNotExist()
        compose.onNodeWithText("视频").performClick()
        compose.waitForIdle()
        assertEquals(listOf("视频"), saved)
        assertTrue(selected.isEmpty())
    }
    @Test fun pendingSelectionRejectsDuplicateClick() {
        gate = CompletableDeferred()
        val vm = launch()
        compose.onNodeWithText("日本 01").performClick()
        compose.waitForIdle()
        vm.choose("日本 01")
        assertEquals(1, selected.size)
        gate!!.complete(snapshot)
        compose.waitForIdle()
    }
    @Test fun removedBindingCannotSubmitNode() {
        launch()
        ticket = null
        compose.onNodeWithText("日本 01").performClick()
        compose.onNodeWithText("配置已变化，请刷新后重试").assertIsDisplayed()
        assertTrue(selected.isEmpty())
    }
    @Test fun emojiSearchPreservesExactMemberName() {
        launch()
        compose.onNodeWithTag("widget-search").performTextReplacement("🚀")
        compose.onNodeWithText("香港/🚀 01").assertIsDisplayed()
        compose.onNodeWithText("日本 01").assertDoesNotExist()
    }
}
