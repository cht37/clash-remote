package com.clashremote.core

import kotlinx.coroutines.CancellationException

sealed interface WidgetCommand {
    data object Refresh : WidgetCommand
    data class Mode(val mode: String) : WidgetCommand
    data class Select(val group: String, val node: String) : WidgetCommand
}

data class ControlSnapshot(val mode: String, val proxies: Map<String, ProxyInfo>)

/** A short-lived control request, independent of the application's foreground session. */
class WidgetControlClient(
    private val factory: (RouterProfile) -> ClashApi = { OkHttpClashApi(it) },
) {
    suspend fun execute(
        profile: RouterProfile,
        command: WidgetCommand,
        isCurrent: () -> Boolean = { true },
    ): ControlSnapshot {
        val client = factory(profile.validated())
        var wrote = false
        fun checkCurrent() {
            if (!isCurrent()) throw ClashException("配置已变化，请重新操作")
        }
        try {
            checkCurrent()
            when (command) {
                WidgetCommand.Refresh -> Unit
                is WidgetCommand.Mode -> {
                    if (command.mode !in setOf("rule", "global", "direct"))
                        throw ClashException("不支持此代理模式")
                    checkCurrent()
                    client.setMode(command.mode)
                    wrote = true
                }
                is WidgetCommand.Select -> {
                    val group = client.proxies().proxies[command.group]
                    if (group?.type != "Selector" || command.node !in group.all.orEmpty())
                        throw ClashException("策略组或节点不可用，请刷新后重试")
                    checkCurrent()
                    client.selectProxy(command.group, command.node)
                    wrote = true
                }
            }
            val snapshot = ControlSnapshot(client.config().mode, client.proxies().proxies)
            checkCurrent()
            return snapshot
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (wrote) throw ClashException("操作可能已生效，请刷新确认", e)
            throw e
        } finally {
            client.close()
        }
    }
}
