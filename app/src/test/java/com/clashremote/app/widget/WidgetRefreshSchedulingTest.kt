package com.clashremote.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.clashremote.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetRefreshSchedulingTest {
    private class Api : ClashApi {
        @Volatile var mode = "rule"
        var proxyReads = 0
        val readingOldSnapshot = CompletableDeferred<Unit>()
        val releaseOldSnapshot = CompletableDeferred<Unit>()
        override suspend fun config() = RuntimeConfig(mode)
        override suspend fun proxies(): ProxySnapshot {
            if (++proxyReads == 1) {
                readingOldSnapshot.complete(Unit)
                releaseOldSnapshot.await()
            }
            return ProxySnapshot(mapOf("代理" to ProxyInfo("代理", "Selector", listOf("A"), "A")))
        }
        override suspend fun setMode(mode: String) { this.mode = mode }
        override suspend fun selectProxy(group: String, name: String) {}
        override fun close() {}
        override suspend fun version() = "unused"
        override suspend fun connections() = ConnectionSnapshot()
        override fun traffic(): Flow<Traffic> = emptyFlow()
        override suspend fun delay(name: String, url: String) = 1
        override suspend fun closeConnection(id: String?) {}
    }
    @Test fun mainAppChangeDuringOldRefreshEventuallyPublishesNewMode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        val store = WidgetStore(context)
        store.bind(7, "代理")
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
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        WidgetCoordinator.enqueue(context, runtime.ticket(7)!!, WidgetCommand.Refresh)
        withTimeout(5_000) { api.readingOldSnapshot.await() }
        // The main app confirms a new mode while the earlier widget readback is incomplete.
        api.mode = "global"
        WidgetCoordinator.enqueue(context, runtime.ticket(7)!!, WidgetCommand.Refresh)
        api.releaseOldSnapshot.complete(Unit)
        withTimeout(5_000) { while (store.cache(7)?.mode != "global") delay(10) }
        assertEquals("global", store.cache(7)!!.mode)
        assertTrue(api.proxyReads >= 2)
    }
}
