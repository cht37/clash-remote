package com.clashremote.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.clashremote.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRuntimeTest {
    private lateinit var store: WidgetStore
    private var revision = "revision-1"
    private val profile = RouterProfile("家", "http://192.168.1.1:9090", "private-secret")
    private val persistence = object : ProfilePersistence {
        override fun load() = profile
        override fun save(profile: RouterProfile) {}
        override fun clear() {}
    }
    private class Api : ClashApi {
        var mode = "rule"
        var writes = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        override suspend fun config(): RuntimeConfig {
            started.complete(Unit); gate?.await()
            if (fail) throw ClashException("离线")
            return RuntimeConfig(mode)
        }
        override suspend fun proxies() = ProxySnapshot(mapOf(
            "代理" to ProxyInfo("代理", "Selector", listOf("香港 01", "日本 01"), "香港 01"),
            "视频" to ProxyInfo("视频", "Selector", listOf("美国 01"), "美国 01")))
        override suspend fun setMode(mode: String) { writes++; this.mode = mode }
        override suspend fun selectProxy(group: String, name: String) { writes++ }
        override fun close() {}
        override suspend fun version() = "unused"
        override suspend fun connections() = ConnectionSnapshot()
        override fun traffic(): Flow<Traffic> = emptyFlow()
        override suspend fun delay(name: String, url: String) = 1
        override suspend fun closeConnection(id: String?) {}
    }
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        store = WidgetStore(context); revision = "revision-1"
    }
    private fun runtime(api: Api) = WidgetRuntime(store, persistence, { revision },
        WidgetControlClient { api }, isInstalled = { it == 7 || it == 8 }, clock = { 500L })
    @Test fun modeChangeUpdatesAllWidgetGroupsFromConfirmedSnapshot() = runTest {
        store.bind(7, "代理"); store.bind(8, "视频")
        val runtime = runtime(Api())
        runtime.execute(runtime.ticket(7)!!, WidgetCommand.Mode("global"))
        assertEquals("global", store.cache(7)!!.mode)
        assertEquals("global", store.cache(8)!!.mode)
        assertEquals("香港 01", store.cache(7)!!.node)
        assertEquals("美国 01", store.cache(8)!!.node)
    }
    @Test fun deletedWidgetCannotReceiveLateResult() = runTest {
        store.bind(7, "代理")
        val api = Api().apply { gate = CompletableDeferred() }
        val runtime = runtime(api)
        val ticket = runtime.ticket(7)!!
        val task = async { runtime.execute(ticket, WidgetCommand.Refresh) }
        api.started.await(); store.remove(7); api.gate!!.complete(Unit)
        task.await()
        assertNull(store.binding(7)); assertNull(store.cache(7))
    }
    @Test fun routerRevisionChangeDiscardsLateSnapshot() = runTest {
        store.bind(7, "代理")
        val api = Api().apply { gate = CompletableDeferred() }
        val runtime = runtime(api)
        val ticket = runtime.ticket(7)!!
        val task = async { runtime.execute(ticket, WidgetCommand.Refresh) }
        api.started.await(); revision = "revision-2"; store.invalidate(); api.gate!!.complete(Unit)
        task.await()
        assertNull(store.cache(7))
    }
    @Test fun attemptedWriteIsNotReplayedByRepeatedWork() = runTest {
        store.bind(7, "代理")
        val api = Api()
        val runtime = runtime(api)
        val ticket = runtime.ticket(7)!!
        runtime.execute(ticket, WidgetCommand.Mode("global"))
        runtime.execute(ticket, WidgetCommand.Mode("global"))
        assertEquals(1, api.writes)
    }
    @Test fun failedRefreshKeepsLastConfirmedNodeAndEndsBusyState() = runTest {
        store.bind(7, "代理")
        store.saveCache(7, WidgetCache("rule", "香港 01", 123, profileRevision = "revision-1"))
        val runtime = runtime(Api().apply { fail = true })
        try { runtime.execute(runtime.ticket(7)!!, WidgetCommand.Refresh) } catch (_: ClashException) {}
        assertEquals("香港 01", store.cache(7)!!.node)
        assertEquals(123L, store.cache(7)!!.updatedAt)
        assertNotNull(store.cache(7)!!.error)
        assertEquals(0L, store.cache(7)!!.busyUntil)
    }
    @Test fun forgedIdCannotWriteToRouter() = runTest {
        store.bind(99, "代理")
        val api = Api()
        val runtime = runtime(api)
        assertNull(runtime.ticket(99))
        assertNull(runtime.execute(WidgetTicket(99, store.binding(99)!!.token, revision, "forged"),
            WidgetCommand.Mode("global")))
        assertEquals(0, api.writes)
    }
    @Test fun requestTimeoutLeavesVisibleErrorAndAllowsRetry() = runTest {
        store.bind(7, "代理")
        val api = Api().apply { gate = CompletableDeferred() }
        val runtime = runtime(api)
        val task = async { runCatching { runtime.execute(runtime.ticket(7)!!, WidgetCommand.Refresh) } }
        api.started.await()
        advanceTimeBy(45_001); runCurrent(); task.await()
        assertNotNull(store.cache(7)!!.error)
        assertEquals(0L, store.cache(7)!!.busyUntil)
    }
    @Test fun concurrentWidgetWritesAreSerializedAndFinishWithSameGlobalMode() = runTest {
        store.bind(7, "代理"); store.bind(8, "视频")
        val api = Api().apply { gate = CompletableDeferred() }
        val runtime = runtime(api)
        val first = async { runtime.execute(runtime.ticket(7)!!, WidgetCommand.Mode("global")) }
        api.started.await()
        val second = async { runtime.execute(runtime.ticket(8)!!, WidgetCommand.Mode("direct")) }
        runCurrent(); assertEquals(1, api.writes)
        api.gate!!.complete(Unit); first.await(); second.await()
        assertEquals("direct", store.cache(7)!!.mode)
        assertEquals("direct", store.cache(8)!!.mode)
    }
}
