package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControllerTest {
    private fun profile(name: String) = RouterProfile(name, "http://192.168.1.1:9090")
    private class FakeApi(val label: String) : ClashApi {
        var waitForVersion: CompletableDeferred<Unit>? = null
        var failWrites = false
        var mode = "rule"
        var chosen = "A"
        var activeTests = 0
        var maxTests = 0
        var connectionsCalls = 0
        var streamStarted = false
        var streamCancelled = false
        var closed = false
        var connectionSnapshot = ConnectionSnapshot()
        var nextSnapshotGate: CompletableDeferred<Unit>? = null
        override suspend fun version(): String { waitForVersion?.await(); return "$label-version" }
        override suspend fun config() = RuntimeConfig(mode)
        override suspend fun proxies() = ProxySnapshot(mapOf("代理" to ProxyInfo("代理", "Selector", listOf("A", "B"), chosen)))
        override suspend fun connections(): ConnectionSnapshot {
            connectionsCalls++
            val snapshot = connectionSnapshot
            val gate = nextSnapshotGate
            nextSnapshotGate = null
            gate?.await()
            return snapshot
        }
        override suspend fun setMode(mode: String) { if (failWrites) throw ClashException("失败"); this.mode = mode }
        override suspend fun selectProxy(group: String, name: String) { if (failWrites) throw ClashException("失败"); chosen = name }
        override suspend fun delay(name: String, url: String): Int {
            activeTests++; maxTests = maxOf(maxTests, activeTests)
            try { delay(100); return 123 } finally { activeTests-- }
        }
        override suspend fun closeConnection(id: String?) {
            connectionSnapshot = connectionSnapshot.copy(connections = connectionSnapshot.connections.filter { id != null && it.id != id })
        }
        override fun traffic(): Flow<Traffic> = flow {
            streamStarted = true
            try { emit(Traffic(12, 34)); awaitCancellation() } finally { streamCancelled = true }
        }
        override fun close() { closed = true }
    }
    @Test fun switchingProfilesRejectsPreviousSessionResults() = runTest {
        val a = FakeApi("A").apply { waitForVersion = CompletableDeferred() }
        val b = FakeApi("B")
        val controller = RemoteController(backgroundScope) { if (it.name == "A") a else b }
        controller.connect(profile("A")); runCurrent()
        controller.connect(profile("B")); runCurrent()
        a.waitForVersion!!.complete(Unit); runCurrent()
        assertEquals("B", controller.state.value.profile!!.name)
        assertEquals("B-version", controller.state.value.version)
        assertTrue(a.closed)
        controller.disconnect()
    }
    @Test fun failedWritesKeepConfirmedState() = runTest {
        val api = FakeApi("A")
        val controller = RemoteController(backgroundScope) { api }
        controller.connect(profile("A")); runCurrent(); api.failWrites = true
        controller.changeMode("global"); runCurrent()
        assertEquals("rule", controller.state.value.mode)
        assertNotNull(controller.state.value.error)
        controller.select("代理", "B"); runCurrent()
        assertEquals("A", controller.state.value.proxies.getValue("代理").now)
        assertFalse(controller.state.value.busy)
        controller.disconnect()
    }
    @Test fun backgroundCancelsStreamAndPolling() = runTest {
        val api = FakeApi("A")
        val controller = RemoteController(backgroundScope) { api }
        controller.connect(profile("A")); runCurrent()
        assertTrue(api.streamStarted)
        controller.setForeground(false); runCurrent()
        val calls = api.connectionsCalls
        advanceTimeBy(20_000); runCurrent()
        assertEquals(calls, api.connectionsCalls)
        assertTrue(api.streamCancelled)
        assertEquals(ConnectionStatus.PAUSED, controller.state.value.status)
        assertEquals(Traffic(), controller.state.value.traffic)
    }
    @Test fun groupTestsLimitConcurrencyAndPublishResults() = runTest {
        val api = FakeApi("A")
        val controller = RemoteController(backgroundScope) { api }
        controller.connect(profile("A")); runCurrent()
        controller.testGroup((1..10).map { "node$it" }); runCurrent()
        advanceTimeBy(301); runCurrent()
        assertEquals(4, api.maxTests)
        assertEquals(10, controller.state.value.delays.size)
        assertTrue(controller.state.value.delays.values.all { it == 123 })
        assertTrue(controller.state.value.testing.isEmpty())
        controller.disconnect()
    }
    @Test fun disconnectClearsSecretsAndLiveData() = runTest {
        val api = FakeApi("A")
        val controller = RemoteController(backgroundScope) { api }
        controller.connect(profile("A")); runCurrent(); controller.disconnect(); runCurrent()
        assertNull(controller.state.value.profile)
        assertEquals(ConnectionStatus.DISCONNECTED, controller.state.value.status)
        assertTrue(controller.state.value.proxies.isEmpty())
    }
    @Test fun olderConnectionPollCannotReintroduceClosedConnection() = runTest {
        val api = FakeApi("A").apply { connectionSnapshot = ConnectionSnapshot(connections = listOf(ConnectionInfo("one"))) }
        val controller = RemoteController(backgroundScope) { api }
        controller.connect(profile("A")); runCurrent()
        val gate = CompletableDeferred<Unit>()
        api.nextSnapshotGate = gate
        advanceTimeBy(2000); runCurrent()
        controller.closeConnection("one"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertTrue("Closed connections must not return from an older poll", controller.state.value.snapshot.connections.isEmpty())
        controller.disconnect()
    }
}
