package com.clashremote.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.clashremote.app.storage.ProfileStore
import com.clashremote.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class WidgetControls(val routerName: String, val profileRevision: String, val snapshot: ControlSnapshot)

class WidgetRuntime(
    private val store: WidgetStore,
    private val persistence: ProfilePersistence,
    private val revision: () -> String,
    private val client: WidgetControlClient = WidgetControlClient(),
    private val isInstalled: (Int) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onRender: (Int) -> Unit = {},
) {
    companion object {
        private val controlLock = Mutex()
        fun from(context: Context): WidgetRuntime {
            val app = context.applicationContext
            return WidgetRuntime(WidgetStore(app), ProfileStore(app), { WidgetCoordinator.profileRevision(app) },
                isInstalled = { id -> AppWidgetManager.getInstance(app).getAppWidgetInfo(id)?.provider?.className ==
                    WidgetCoordinator.PROVIDER }, onRender = { WidgetCoordinator.render(app, it) })
        }
    }
    fun ticket(id: Int): WidgetTicket? {
        if (!isInstalled(id)) return null
        val binding = store.binding(id) ?: return null
        return WidgetTicket(id, binding.token, revision(), UUID.randomUUID().toString())
    }
    fun isCurrent(ticket: WidgetTicket): Boolean = isInstalled(ticket.widgetId) &&
        revision() == ticket.profileRevision && store.binding(ticket.widgetId)?.token == ticket.bindingToken

    suspend fun loadControls(): WidgetControls = controlLock.withLock {
        val captured = revision()
        val profile = persistence.load() ?: throw ClashException("请先配置路由器")
        val snapshot = request(profile, WidgetCommand.Refresh) { revision() == captured }
        WidgetControls(profile.name, captured, snapshot)
    }

    suspend fun execute(ticket: WidgetTicket, command: WidgetCommand): ControlSnapshot? = controlLock.withLock {
        if (!isCurrent(ticket)) return@withLock null
        val binding = store.binding(ticket.widgetId) ?: return@withLock null
        val bindings = store.bindings().filter { isInstalled(it.widgetId) }
        val before = store.cache(ticket.widgetId)?.takeIf { it.profileRevision == ticket.profileRevision }
            ?: WidgetCache(profileRevision = ticket.profileRevision)
        store.saveIfCurrent(binding, before.copy(error = null, busyUntil = clock() + 90_000)) { isCurrent(ticket) }
        onRender(ticket.widgetId)
        try {
            val profile = persistence.load() ?: throw ClashException("请先配置路由器")
            val actual = if (command != WidgetCommand.Refresh && !store.claimWrite(ticket.widgetId, ticket.requestToken))
                WidgetCommand.Refresh else command
            val snapshot = request(profile, actual) { isCurrent(ticket) }
            if (!isCurrent(ticket)) return@withLock null
            bindings.forEach { bound ->
                val group = snapshot.proxies[bound.group]
                val valid = group?.type == "Selector"
                val cache = WidgetCache(snapshot.mode, if (valid) group.now else null, clock(),
                    if (valid) null else "策略组不可用，请重新选择", routerName = profile.name,
                    profileRevision = ticket.profileRevision)
                if (store.saveIfCurrent(bound, cache) { isCurrent(ticket) }) onRender(bound.widgetId)
            }
            snapshot
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrent(ticket)) return@withLock null
            val message = if (e is ClashException) e.message ?: "操作失败，请重试" else "操作失败，请刷新重试"
            store.saveIfCurrent(binding, before.copy(error = message)) { isCurrent(ticket) }
            throw e
        } finally {
            if (isCurrent(ticket)) {
                val cache = store.cache(ticket.widgetId) ?: before
                store.saveIfCurrent(binding, cache.copy(busyUntil = 0)) { isCurrent(ticket) }
                onRender(ticket.widgetId)
            }
        }
    }
    private suspend fun request(profile: RouterProfile, command: WidgetCommand, isCurrent: () -> Boolean): ControlSnapshot =
        try { withTimeout(45_000) { client.execute(profile, command, isCurrent) } }
        catch (e: TimeoutCancellationException) { throw ClashException("操作超时，请刷新确认", e) }
}
