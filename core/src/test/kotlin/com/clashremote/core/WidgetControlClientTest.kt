package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetControlClientTest {
    private val profile = RouterProfile("家", "http://192.168.1.1:9090", "test-secret")
    private class Api : ClashApi {
        var mode = "rule"
        var node = "香港 01"
        var type = "Selector"
        var writtenMode: String? = null
        var writtenNode: Pair<String, String>? = null
        var confirmedMode: String? = null
        var failWrite = false
        var failReadAfterWrite = false
        var closed = 0
        var proxyGate: CompletableDeferred<Unit>? = null
        override suspend fun config(): RuntimeConfig {
            if (failReadAfterWrite && writtenMode != null) throw ClashException("离线")
            return RuntimeConfig(confirmedMode ?: mode)
        }
        override suspend fun proxies(): ProxySnapshot {
            proxyGate?.await()
            return ProxySnapshot(mapOf("代理/🚀" to ProxyInfo("代理/🚀", type, listOf("香港 01", "日本 01"), node)))
        }
        override suspend fun setMode(mode: String) {
            if (failWrite) throw ClashException("拒绝写入")
            writtenMode = mode; this.mode = mode
        }
        override suspend fun selectProxy(group: String, name: String) {
            if (failWrite) throw ClashException("拒绝写入")
            writtenNode = group to name; node = name
        }
        override fun close() { closed++ }
        override suspend fun version(): String = error("微件不需要内核版本")
        override suspend fun connections(): ConnectionSnapshot = error("微件不需要连接列表")
        override fun traffic(): Flow<Traffic> = error("微件不建立流量连接")
        override suspend fun delay(name: String, url: String): Int = error("微件不测速")
        override suspend fun closeConnection(id: String?) = error("微件不关闭连接")
    }
    private suspend fun failure(block: suspend () -> Unit): Exception {
        try { block() } catch (e: Exception) { return e }
        throw AssertionError("Expected operation to fail")
    }
    @Test fun refreshOnlyLoadsControlsAndClosesClient() = runBlocking {
        val api = Api()
        val result = WidgetControlClient { api }.execute(profile, WidgetCommand.Refresh)
        assertEquals("rule", result.mode)
        assertEquals("香港 01", result.proxies.getValue("代理/🚀").now)
        assertEquals(1, api.closed)
    }
    @Test fun modeUsesConfirmedRemoteValue() = runBlocking {
        val api = Api().apply { confirmedMode = "direct" }
        val result = WidgetControlClient { api }.execute(profile, WidgetCommand.Mode("global"))
        assertEquals("global", api.writtenMode)
        assertEquals("direct", result.mode)
        assertEquals(1, api.closed)
    }
    @Test fun selectionSubmitsExactGroupAndMember() = runBlocking {
        val api = Api()
        val result = WidgetControlClient { api }.execute(profile, WidgetCommand.Select("代理/🚀", "日本 01"))
        assertEquals("代理/🚀" to "日本 01", api.writtenNode)
        assertEquals("日本 01", result.proxies.getValue("代理/🚀").now)
    }
    @Test fun selectionRejectsMissingMemberWithoutWriting() = runBlocking {
        val api = Api()
        assertTrue(failure { WidgetControlClient { api }.execute(profile,
            WidgetCommand.Select("代理/🚀", "日本")) } is ClashException)
        assertNull(api.writtenNode)
        assertEquals(1, api.closed)
    }
    @Test fun automaticGroupCannotBeSelected() = runBlocking {
        val api = Api().apply { type = "URLTest" }
        failure { WidgetControlClient { api }.execute(profile, WidgetCommand.Select("代理/🚀", "日本 01")) }
        assertNull(api.writtenNode)
    }
    @Test fun invalidModeNeverReachesRouter() = runBlocking {
        val api = Api()
        failure { WidgetControlClient { api }.execute(profile, WidgetCommand.Mode("invalid")) }
        assertNull(api.writtenMode)
    }
    @Test fun writeFailurePreservesConfirmedRemoteMode() = runBlocking {
        val api = Api().apply { failWrite = true }
        val error = failure { WidgetControlClient { api }.execute(profile, WidgetCommand.Mode("global")) }
        assertEquals("拒绝写入", error.message)
        assertEquals("rule", api.mode)
        assertEquals(1, api.closed)
    }
    @Test fun failedReadAfterWriteReportsUnconfirmedResult() = runBlocking {
        val api = Api().apply { failReadAfterWrite = true }
        val error = failure { WidgetControlClient { api }.execute(profile, WidgetCommand.Mode("global")) }
        assertTrue(error.message.orEmpty().contains("可能已生效"))
        assertEquals("global", api.writtenMode)
        assertEquals(1, api.closed)
    }
    @Test fun changedConfigurationPreventsWrite() = runBlocking {
        val api = Api()
        failure { WidgetControlClient { api }.execute(profile, WidgetCommand.Mode("global")) { false } }
        assertNull(api.writtenMode)
    }
    @Test fun cancelledRequestClosesClientAndPropagatesCancellation() = runTest {
        val api = Api().apply { proxyGate = CompletableDeferred() }
        val job = async { WidgetControlClient { api }.execute(profile, WidgetCommand.Refresh) }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, api.closed)
    }
}
