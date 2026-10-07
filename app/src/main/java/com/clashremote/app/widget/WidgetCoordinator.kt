package com.clashremote.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.clashremote.core.*
import kotlinx.coroutines.flow.first

object WidgetCoordinator {
    const val PROVIDER = "com.clashremote.app.widget.ProxyWidgetProvider"
    const val SQUARE_PROVIDER = "com.clashremote.app.widget.SquareProxyWidgetProvider"
    const val SLIM_PROVIDER = "com.clashremote.app.widget.SlimProxyWidgetProvider"
    const val LARGE_PROVIDER = "com.clashremote.app.widget.LargeProxyWidgetProvider"
    private val providers = listOf(PROVIDER, SQUARE_PROVIDER, SLIM_PROVIDER, LARGE_PROVIDER)
    const val RENDER = "com.clashremote.app.widget.RENDER"
    const val CONTROL = "com.clashremote.app.widget.CONTROL"
    private const val TAG = "proxy-widgets"
    private fun widgetTag(id: Int) = "$TAG-$id"
    fun profileRevision(context: Context): String = context.getSharedPreferences("router", Context.MODE_PRIVATE)
        .getString("widget_revision", "legacy") ?: "legacy"
    fun render(context: Context, id: Int) {
        val component = provider(context, id) ?: return
        context.sendBroadcast(Intent(RENDER).setComponent(component)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
    }
    fun provider(context: Context, id: Int): ComponentName? =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.takeIf {
            it.packageName == context.packageName && it.className in providers
        }
    fun owns(context: Context, id: Int): Boolean = provider(context, id) != null
    private fun work(ticket: WidgetTicket, command: WidgetCommand): OneTimeWorkRequest {
        val input = Data.Builder().putInt("widgetId", ticket.widgetId).putString("bindingToken", ticket.bindingToken)
            .putString("profileRevision", ticket.profileRevision).putString("requestToken", ticket.requestToken)
        when (command) {
            WidgetCommand.Refresh -> input.putString("action", "refresh")
            is WidgetCommand.Mode -> input.putString("action", "mode").putString("mode", command.mode)
            is WidgetCommand.Select -> input.putString("action", "select").putString("group", command.group).putString("node", command.node)
        }
        return OneTimeWorkRequestBuilder<WidgetWorker>().setInputData(input.build()).addTag(TAG)
            .addTag(widgetTag(ticket.widgetId)).build()
    }
    fun enqueue(context: Context, ticket: WidgetTicket, command: WidgetCommand) {
        // Refresh notifications must survive a running stale read. Replace reads independently of writes.
        val refresh = command == WidgetCommand.Refresh
        val name = if (refresh) "proxy-widget-refresh-${ticket.widgetId}" else "proxy-widget-${ticket.widgetId}"
        val policy = if (refresh) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, work(ticket, command))
    }
    suspend fun submitNode(context: Context, ticket: WidgetTicket, command: WidgetCommand.Select): ControlSnapshot? {
        val manager = WorkManager.getInstance(context.applicationContext)
        val request = work(ticket, command)
        manager.enqueue(request)
        // Cancelling the panel only stops observation; the persisted submission continues independently.
        val result = manager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }!!
        if (result.state != WorkInfo.State.SUCCEEDED)
            throw ClashException(result.outputData.getString("error") ?: "操作失败，请刷新确认")
        val mode = result.outputData.getString("mode") ?: return null
        val node = result.outputData.getString("node") ?: return null
        if (WidgetStore(context).binding(ticket.widgetId)?.token != ticket.bindingToken ||
            profileRevision(context) != ticket.profileRevision) return null
        return ControlSnapshot(mode, mapOf(command.group to ProxyInfo(command.group, "Selector", now = node)))
    }
    fun refreshAll(context: Context) {
        val runtime = WidgetRuntime.from(context)
        installedIds(context).forEach { id -> runtime.ticket(id)?.let { enqueue(context, it, WidgetCommand.Refresh) } }
    }
    fun profileChanged(context: Context) {
        if (installedIds(context).isEmpty()) return
        WidgetStore(context).invalidate()
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        installedIds(context).forEach { render(context, it) }
        refreshAll(context)
    }
    fun appearanceChanged(context: Context) { installedIds(context).forEach { render(context, it) } }
    fun installedIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context)
        return providers.flatMap { manager.getAppWidgetIds(ComponentName(context.packageName, it)).toList() }
            .distinct().toIntArray()
    }
    fun remove(context: Context, id: Int) {
        WidgetStore(context).remove(id)
        WorkManager.getInstance(context).cancelUniqueWork("proxy-widget-$id")
        WorkManager.getInstance(context).cancelAllWorkByTag(widgetTag(id))
    }
}
