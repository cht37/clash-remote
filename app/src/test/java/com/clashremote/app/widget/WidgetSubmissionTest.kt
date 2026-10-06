package com.clashremote.app.widget

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.clashremote.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSubmissionTest {
    private class Api : ClashApi {
        var node = "A"
        var writes = 0
        val written = CompletableDeferred<Unit>()
        val confirm = CompletableDeferred<Unit>()
        override suspend fun selectProxy(group: String, name: String) {
            writes++; node = name; written.complete(Unit)
        }
        override suspend fun config(): RuntimeConfig {
            if (writes > 0) confirm.await()
            return RuntimeConfig("rule")
        }
        override suspend fun proxies() = ProxySnapshot(mapOf("代理" to ProxyInfo("代理", "Selector", listOf("A", "B"), node)))
        override suspend fun setMode(mode: String) {}
        override fun close() {}
        override suspend fun version() = "unused"
        override suspend fun connections() = ConnectionSnapshot()
        override fun traffic(): Flow<Traffic> = emptyFlow()
        override suspend fun delay(name: String, url: String) = 1
        override suspend fun closeConnection(id: String?) {}
    }
    @Test fun closingPickerAfterPutStillConfirmsWithoutReplayingWrite() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().clear()
                .putString("widget_revision", "revision-1").commit()
            val store = WidgetStore(context)
            val binding = store.bind(7, "代理")
            store.saveCache(7, WidgetCache("rule", "A", 1, profileRevision = "revision-1"))
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
            WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
                .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
            val initial = ControlSnapshot("rule", mapOf("代理" to ProxyInfo("代理", "Selector", listOf("A", "B"), "A")))
            val vm = WidgetPickerViewModel(false, binding,
                loadControls = { WidgetControls("家", "revision-1", initial) }, currentTicket = { runtime.ticket(7) },
                selectNode = { ticket, command -> WidgetCoordinator.submitNode(context, ticket, command) }, bindGroup = { _, _ -> })
            val viewModels = ViewModelStore().apply { put("picker", vm) }
            vm.choose("B")
            withTimeout(5_000) { api.written.await() }
            // Closing the Activity clears its ViewModel, cancelling observation but not the durable worker.
            viewModels.clear()
            api.confirm.complete(Unit)
            withTimeout(5_000) { while (store.cache(7)?.node != "B") delay(10) }
            assertEquals(1, api.writes)
            assertEquals("rule", store.cache(7)!!.mode)
            assertNull(store.cache(7)!!.error)
        } finally { Dispatchers.resetMain() }
    }
}
