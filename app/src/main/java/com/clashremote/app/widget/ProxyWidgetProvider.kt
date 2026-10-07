package com.clashremote.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.clashremote.core.WidgetCommand

open class ProxyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val runtime = WidgetRuntime.from(context)
        ids.forEach { id ->
            WidgetRenderer.render(context, id)
            runtime.ticket(id)?.let { WidgetCoordinator.enqueue(context, it, WidgetCommand.Refresh) }
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetCoordinator.RENDER -> WidgetRenderer.render(context,
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            WidgetCoordinator.CONTROL -> {
                val id = intent.getIntExtra("widgetId", -1)
                val runtime = WidgetRuntime.from(context)
                val ticket = runtime.ticket(id) ?: return
                if (ticket.bindingToken != intent.getStringExtra("bindingToken") ||
                    ticket.profileRevision != intent.getStringExtra("profileRevision")) {
                    WidgetRenderer.render(context, id); return
                }
                val cache = WidgetStore(context).cache(id)
                val command = when (intent.getStringExtra("action")) {
                    "refresh" -> WidgetCommand.Refresh
                    "mode" -> {
                        if (cache == null || cache.busyUntil > System.currentTimeMillis()) return
                        val mode = intent.getStringExtra("mode") ?: return
                        if (mode !in setOf("rule", "global", "direct")) return
                        WidgetCommand.Mode(mode)
                    }
                    else -> return
                }
                WidgetCoordinator.enqueue(context, ticket, command)
            }
            else -> super.onReceive(context, intent)
        }
    }
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetCoordinator.remove(context, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        WidgetRenderer.render(context, id)
    }
}

class SquareProxyWidgetProvider : ProxyWidgetProvider()
class SlimProxyWidgetProvider : ProxyWidgetProvider()
class LargeProxyWidgetProvider : ProxyWidgetProvider()
