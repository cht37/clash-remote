package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class RemoteController(
    private val scope: CoroutineScope,
    private val factory: (RouterProfile) -> ClashApi = { OkHttpClashApi(it) },
) {
    private val mutable = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = mutable.asStateFlow()
    private var generation = 0L
    private var foreground = true
    private var sessionJob: Job? = null
    private var sessionScope: CoroutineScope? = null
    private var api: ClashApi? = null
    private val controlLock = Mutex()

    fun connect(profile: RouterProfile) {
        val normalized = profile.validated()
        stopSession()
        mutable.value = RemoteState(profile = normalized, status = if (foreground) ConnectionStatus.CONNECTING else ConnectionStatus.PAUSED)
        if (foreground) startSession(normalized)
    }
    fun disconnect() { stopSession(); mutable.value = RemoteState() }
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        val profile = state.value.profile ?: return
        if (!value) {
            stopSession()
            mutable.update { it.copy(status = ConnectionStatus.PAUSED, traffic = Traffic(), busy = false, testing = emptySet()) }
        } else {
            mutable.update { it.copy(status = ConnectionStatus.CONNECTING, error = null) }
            startSession(profile)
        }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private fun stopSession() {
        generation++
        sessionJob?.cancel(); sessionJob = null; sessionScope = null
        api?.close(); api = null
    }
    private fun publish(id: Long, transform: (RemoteState) -> RemoteState) {
        if (generation == id) mutable.update(transform)
    }
    private fun startSession(profile: RouterProfile) {
        val id = generation
        val client = factory(profile)
        api = client
        val job = SupervisorJob(scope.coroutineContext[Job])
        sessionJob = job
        val childScope = CoroutineScope(scope.coroutineContext + job)
        sessionScope = childScope
        childScope.launch {
            try {
                val version = client.version()
                controlLock.withLock { readControls(id, client) }
                val connections = client.connections()
                publish(id) { it.copy(version = version, snapshot = connections, status = ConnectionStatus.ONLINE, error = null) }
                launch {
                    guardSession(id) {
                        client.traffic().collect { traffic ->
                            publish(id) { it.copy(traffic = traffic, trafficHistory = (it.trafficHistory + traffic).takeLast(30)) }
                        }
                    }
                }
                launch {
                    guardSession(id) {
                        while (isActive) {
                            delay(2000)
                            controlLock.withLock {
                                val snapshot = client.connections()
                                publish(id) { it.copy(snapshot = snapshot) }
                            }
                        }
                    }
                }
                launch {
                    guardSession(id) {
                        while (isActive) { delay(10_000); controlLock.withLock { readControls(id, client) } }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failSession(id, e) }
        }
    }
    private suspend fun guardSession(id: Long, block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failSession(id, e) }
    }
    private fun failSession(id: Long, error: Exception) {
        if (id != generation) return
        publish(id) { it.copy(status = ConnectionStatus.OFFLINE, traffic = Traffic(), busy = false, testing = emptySet(), error = message(error)) }
        stopSession()
    }
    private suspend fun readControls(id: Long, client: ClashApi) {
        val config = client.config()
        val proxies = client.proxies()
        publish(id) { it.copy(mode = config.mode, proxies = proxies.proxies) }
    }
    private fun action(block: suspend (Long, ClashApi) -> Unit) {
        val client = api ?: return
        val childScope = sessionScope ?: return
        if (state.value.status != ConnectionStatus.ONLINE || state.value.busy) return
        val id = generation
        publish(id) { it.copy(busy = true, error = null) }
        childScope.launch {
            try { controlLock.withLock { block(id, client) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { publish(id) { it.copy(error = message(e)) } }
            finally { publish(id) { it.copy(busy = false) } }
        }
    }
    fun refresh() {
        val current = state.value
        if (current.status != ConnectionStatus.ONLINE) {
            current.profile?.let { connect(it) }; return
        }
        action { id, client ->
            readControls(id, client)
            val snapshot = client.connections()
            publish(id) { it.copy(snapshot = snapshot) }
        }
    }
    fun changeMode(mode: String) = action { id, client ->
        client.setMode(mode)
        val config = client.config()
        publish(id) { it.copy(mode = config.mode) }
    }
    fun select(group: String, name: String) = action { id, client ->
        if (state.value.proxies[group]?.type != "Selector") throw ClashException("此策略组不支持手动选择")
        client.selectProxy(group, name)
        val snapshot = client.proxies()
        publish(id) { it.copy(proxies = snapshot.proxies) }
    }
    fun closeConnection(id: String) = action { session, client ->
        client.closeConnection(id)
        val snapshot = client.connections()
        publish(session) { it.copy(snapshot = snapshot) }
    }
    fun closeAllConnections() = action { session, client ->
        client.closeConnection(null)
        val snapshot = client.connections()
        publish(session) { it.copy(snapshot = snapshot) }
    }
    fun testNode(name: String) = testGroup(listOf(name))
    fun testGroup(names: List<String>) {
        val client = api ?: return
        val childScope = sessionScope ?: return
        val profile = state.value.profile ?: return
        if (state.value.status != ConnectionStatus.ONLINE) return
        val pending = names.distinct().filter { it !in state.value.testing }
        val id = generation
        publish(id) { it.copy(testing = it.testing + pending) }
        childScope.launch {
            val semaphore = testSemaphore
            pending.forEach { name ->
                launch {
                    try {
                        val result = semaphore.withPermit { client.delay(name, profile.testUrl) }
                        publish(id) { it.copy(delays = it.delays + (name to result)) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { publish(id) { it.copy(delays = it.delays + (name to -1)) } }
                    finally { publish(id) { it.copy(testing = it.testing - name) } }
                }
            }
        }
    }
    private val testSemaphore = Semaphore(4)
    private fun message(e: Exception) = if (e is ClashException) e.message ?: "操作失败，请重试" else "操作失败，请重试或重新连接路由器"
}
