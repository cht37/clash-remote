package com.clashremote.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.clashremote.app.ui.ClashRemoteApp
import com.clashremote.app.ui.ClashRemoteTheme
import com.clashremote.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class SaveRouterUiTest {
    @get:Rule val compose = createComposeRule()
    private class Store : ProfilePersistence {
        var profile: RouterProfile? = null
        override fun load() = profile
        override fun save(profile: RouterProfile) { this.profile = profile }
        override fun clear() { profile = null }
    }
    private class Api : ClashApi {
        override suspend fun version() = "test-router"
        override suspend fun config() = RuntimeConfig("rule")
        override suspend fun proxies() = ProxySnapshot(emptyMap())
        override suspend fun connections() = ConnectionSnapshot()
        override suspend fun setMode(mode: String) {}
        override suspend fun selectProxy(group: String, name: String) {}
        override suspend fun delay(name: String, url: String) = 1
        override suspend fun closeConnection(id: String?) {}
        override fun traffic(): Flow<Traffic> = emptyFlow()
        override fun close() {}
    }
    private fun launch(saved: RouterProfile? = null, initialPage: Int = 0): RemoteViewModel {
        val vm = RemoteViewModel(ApplicationProvider.getApplicationContext<Application>(), Store().apply { profile = saved }, { Api() },
            object : AppearancePersistence {
                override fun loadPalette(): String? = null
                override fun savePalette(id: String) {}
            }, object : ReleaseSource {
                override suspend fun latest(): ReleaseInfo? = null
                override fun close() {}
            }, Dispatchers.Unconfined)
        compose.setContent { ClashRemoteTheme { ClashRemoteApp(vm, initialPage = initialPage) } }
        compose.waitForIdle()
        return vm
    }
    @Test fun savingRouterDisplaysOverviewAndNewRouterWithoutManualNavigation() {
        launch()
        compose.onNodeWithText("路由器名称").performTextReplacement("新路由器")
        compose.onNodeWithText("Clash 控制地址").performTextReplacement("http://192.168.1.2:9090")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("保存并连接"))
        compose.onNodeWithText("保存并连接").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("路由器代理").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("新路由器").assertIsDisplayed()
        compose.onNodeWithText("http://192.168.1.2:9090/").assertIsDisplayed()
    }
    @Test fun invalidAddressKeepsSettingsAndShowsError() {
        launch()
        compose.onNodeWithText("Clash 控制地址").performTextReplacement("invalid-address")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("保存并连接"))
        compose.onNodeWithText("保存并连接").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("控制地址需为有效的 HTTP 或 HTTPS 地址").assertIsDisplayed()
        compose.onNodeWithText("保存并连接").assertExists()
    }
    @Test fun widgetSetupEntryOpensSettingsEvenWithSavedRouter() {
        launch(RouterProfile("已保存", "http://192.168.1.1:9090"), initialPage = 3)
        compose.onNodeWithText("Clash 控制地址").assertIsDisplayed()
        compose.onNodeWithText("http://192.168.1.1:9090").assertExists()
    }
}
