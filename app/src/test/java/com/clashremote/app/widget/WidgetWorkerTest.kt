package com.clashremote.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.clashremote.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetWorkerTest {
    private class Api : ClashApi {
        var writes = 0
        override suspend fun config() = RuntimeConfig("rule")
        override suspend fun proxies() = ProxySnapshot(mapOf("代理" to ProxyInfo("代理", "Selector", listOf("A"), "A")))
        override suspend fun setMode(mode: String) { writes++ }
        override suspend fun selectProxy(group: String, name: String) { writes++ }
        override fun close() {}
        override suspend fun version() = "unused"
        override suspend fun connections() = ConnectionSnapshot()
        override fun traffic(): Flow<Traffic> = emptyFlow()
        override suspend fun delay(name: String, url: String) = 1
        override suspend fun closeConnection(id: String?) {}
    }
    @Test fun systemRescheduledModeWorkOnlyReadsRemoteState() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        val store = WidgetStore(context)
        val binding = store.bind(7, "代理")
        val api = Api()
        val profile = RouterProfile("家", "http://192.168.1.1:9090")
        val persistence = object : ProfilePersistence {
            override fun load() = profile
            override fun save(profile: RouterProfile) {}
            override fun clear() {}
        }
        val runtime = WidgetRuntime(store, persistence, { "revision-1" }, WidgetControlClient { api }, isInstalled = { it == 7 })
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                WidgetWorker(appContext, workerParameters, runtime)
        }
        val worker = TestListenableWorkerBuilder<WidgetWorker>(context).setWorkerFactory(factory).setRunAttemptCount(1)
            .setInputData(workDataOf("widgetId" to 7, "bindingToken" to binding.token, "profileRevision" to "revision-1",
                "requestToken" to "restored-request", "action" to "mode", "mode" to "global")).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, api.writes)
        assertEquals("rule", store.cache(7)!!.mode)
    }
    @Test fun systemRescheduledNodeWorkReturnsConfirmationWithoutWriting() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        val store = WidgetStore(context)
        val binding = store.bind(7, "代理")
        val api = Api()
        val persistence = object : ProfilePersistence {
            override fun load() = RouterProfile("家", "http://192.168.1.1:9090")
            override fun save(profile: RouterProfile) {}
            override fun clear() {}
        }
        val runtime = WidgetRuntime(store, persistence, { "revision-1" }, WidgetControlClient { api }, isInstalled = { it == 7 })
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                WidgetWorker(appContext, workerParameters, runtime)
        }
        val worker = TestListenableWorkerBuilder<WidgetWorker>(context).setWorkerFactory(factory).setRunAttemptCount(1)
            .setInputData(workDataOf("widgetId" to 7, "bindingToken" to binding.token, "profileRevision" to "revision-1",
                "requestToken" to "restored-node", "action" to "select", "group" to "代理", "node" to "A")).build()
        val result = worker.doWork() as ListenableWorker.Result.Success
        assertEquals("A", result.outputData.getString("node"))
        assertEquals(0, api.writes)
        assertEquals("A", store.cache(7)!!.node)
    }
}
