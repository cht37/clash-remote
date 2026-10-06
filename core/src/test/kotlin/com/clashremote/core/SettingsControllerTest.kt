package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsControllerTest {
    private class MemoryStore : ProfilePersistence {
        var stored: RouterProfile? = RouterProfile("A", "http://192.168.1.1:9090", "old-secret")
        var failSave = false
        override fun load() = stored
        override fun save(profile: RouterProfile) { if (failSave) throw ClashException("保存失败"); stored = profile }
        override fun clear() { stored = null }
    }
    private class TestApi(private val version: suspend () -> String) : ClashApi {
        override suspend fun version() = version.invoke()
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
    @Test fun draftTestDoesNotOverwriteSavedProfileAndEditsInvalidateIt() = runTest {
        val store = MemoryStore()
        val controller = SettingsController(backgroundScope, store, {}, {}, StandardTestDispatcher(testScheduler)) { TestApi { "B-version" } }
        runCurrent()
        val b = RouterProfile("B", "http://192.168.1.2:9090", "new-secret")
        controller.updateDraft(b); controller.test(); runCurrent()
        assertEquals(b, controller.state.value.draft)
        assertEquals("A", store.stored!!.name)
        assertTrue(controller.state.value.testMessage!!.contains("B-version"))
        controller.updateDraft(b.copy(endpoint = "http://192.168.1.3:9090"))
        assertNull(controller.state.value.testMessage)
    }
    @Test fun failedSavePreservesEditableDraftAndPreviousSession() = runTest {
        val store = MemoryStore().apply { failSave = true }
        val connected = mutableListOf<RouterProfile>()
        val controller = SettingsController(backgroundScope, store, connected::add, {}, StandardTestDispatcher(testScheduler)) { TestApi { "ok" } }
        runCurrent()
        val b = RouterProfile("B", "http://192.168.1.2:9090", "new-secret")
        controller.updateDraft(b); controller.save(); runCurrent()
        assertEquals(b, controller.state.value.draft)
        assertEquals("A", controller.state.value.profile!!.name)
        assertEquals("A", store.stored!!.name)
        assertTrue(connected.none { it.name == "B" })
        assertNotNull(controller.state.value.error)
        assertFalse(controller.state.value.saving)
    }
    @Test fun cancelledOldTestCannotChangeCurrentDraftTestState() = runTest {
        val oldResult = CompletableDeferred<String>()
        val newResult = CompletableDeferred<String>()
        val controller = SettingsController(backgroundScope, MemoryStore(), {}, {}, StandardTestDispatcher(testScheduler)) { profile ->
            TestApi { withContext(NonCancellable) { if (profile.name == "B") oldResult.await() else newResult.await() } }
        }
        runCurrent()
        controller.updateDraft(RouterProfile("B", "http://192.168.1.2:9090")); controller.test(); runCurrent()
        controller.updateDraft(RouterProfile("C", "http://192.168.1.3:9090")); controller.test(); runCurrent()
        oldResult.complete("B-version"); runCurrent()
        assertTrue(controller.state.value.testBusy)
        assertNull(controller.state.value.testMessage)
        newResult.complete("C-version"); runCurrent()
        assertFalse(controller.state.value.testBusy)
        assertTrue(controller.state.value.testMessage!!.contains("C-version"))
    }
}
