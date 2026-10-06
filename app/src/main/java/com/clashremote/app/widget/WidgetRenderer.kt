package com.clashremote.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.clashremote.app.MainActivity
import com.clashremote.app.R
import com.clashremote.app.storage.AppearanceStore
import com.clashremote.core.AppPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetRenderer {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    fun modeIntent(context: Context, ticket: WidgetTicket, mode: String): PendingIntent =
        PendingIntent.getBroadcast(context, 0, controlIntent(context, ticket, "mode:$mode")
            .putExtra("action", "mode").putExtra("mode", mode), FLAGS)
    private fun controlIntent(context: Context, ticket: WidgetTicket, action: String) = Intent(WidgetCoordinator.CONTROL)
        .setComponent(ComponentName(context.packageName, WidgetCoordinator.PROVIDER))
        .setData(identity(ticket, action)).putExtra("widgetId", ticket.widgetId)
        .putExtra("bindingToken", ticket.bindingToken).putExtra("profileRevision", ticket.profileRevision)
    private fun identity(ticket: WidgetTicket, action: String): Uri = Uri.Builder().scheme("clashremote-widget")
        .authority(ticket.widgetId.toString()).appendPath(ticket.bindingToken).appendPath(ticket.profileRevision)
        .appendPath(action).build()
    private fun activityIntent(context: Context, ticket: WidgetTicket, activity: String, action: String): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent().setComponent(ComponentName(context.packageName, activity))
            .setData(identity(ticket, action)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ticket.widgetId)
            .putExtra("bindingToken", ticket.bindingToken).putExtra("profileRevision", ticket.profileRevision), FLAGS)

    fun views(context: Context, id: Int): RemoteViews {
        val store = WidgetStore(context)
        val binding = store.binding(id)
        val cache = store.cache(id)?.takeIf { it.profileRevision == WidgetCoordinator.profileRevision(context) }
            ?: WidgetCache()
        val configured = context.getSharedPreferences("router", Context.MODE_PRIVATE).contains("profile")
        val busy = configured && cache.busyUntil > System.currentTimeMillis()
        val mode = if (configured) cache.mode?.lowercase(Locale.ROOT) else null
        val ticket = WidgetTicket(id, binding?.token ?: "unbound", WidgetCoordinator.profileRevision(context), "render")
        val palette = AppPalette.fromId(AppearanceStore(context).loadPalette())
        val selectedBackground = when (palette) {
            AppPalette.GREEN -> R.drawable.widget_mode_green
            AppPalette.BLUE -> R.drawable.widget_mode_blue
            AppPalette.PURPLE -> R.drawable.widget_mode_purple
            AppPalette.ORANGE -> R.drawable.widget_mode_orange
        }
        val foreground = context.getColor(R.color.widget_foreground)
        val secondary = context.getColor(R.color.widget_secondary)
        return RemoteViews(context.packageName, R.layout.widget_proxy).apply {
            val status = when {
                !configured -> "未配置"
                binding == null -> "请选择策略组"
                busy -> "正在处理…"
                cache.error != null -> "同步失败"
                cache.updatedAt == 0L -> "待刷新"
                else -> "更新于 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cache.updatedAt))}"
            }
            setTextViewText(R.id.widget_time, status)
            setTextViewText(R.id.widget_router, cache.routerName ?: if (configured) "路由器" else "未配置路由器")
            setTextColor(R.id.widget_dot, context.getColor(when {
                !configured || cache.updatedAt == 0L -> R.color.widget_secondary
                cache.error != null -> R.color.widget_error
                else -> R.color.widget_success
            }))
            setTextViewText(R.id.widget_node, when {
                !configured -> "请先配置路由器"
                binding == null -> "选择策略组"
                else -> cache.node ?: "选择节点"
            })
            setTextViewText(R.id.widget_detail, if (!configured) "点击打开设置" else cache.error ?: binding?.let { "策略组：${it.group}" } ?: "点击完成微件配置")
            setTextColor(R.id.widget_detail, if (cache.error != null && configured) context.getColor(R.color.widget_error) else secondary)
            setViewVisibility(R.id.widget_label, if (cache.error != null) View.GONE else View.VISIBLE)
            listOf(Triple(R.id.widget_rule, "rule", "规则"), Triple(R.id.widget_global, "global", "全局"),
                Triple(R.id.widget_direct, "direct", "直连")).forEach { (view, value, label) ->
                val selected = mode == value
                setInt(view, "setBackgroundResource", if (selected) selectedBackground else R.drawable.widget_button)
                setTextColor(view, if (selected) android.graphics.Color.WHITE else foreground)
                setContentDescription(view, if (selected) "$label，当前模式" else label)
                setBoolean(view, "setEnabled", configured && binding != null && mode != null && !busy)
                setOnClickPendingIntent(view, modeIntent(context, ticket, value))
            }
            val openApp = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(Uri.parse("clashremote-widget://$id/app")).putExtra("open_settings", !configured), FLAGS)
            setOnClickPendingIntent(R.id.widget_title_area, openApp)
            val config = activityIntent(context, ticket, "com.clashremote.app.widget.WidgetConfigActivity", "config")
            val node = if (!configured) openApp else if (binding == null) config else
                activityIntent(context, ticket, "com.clashremote.app.widget.WidgetNodeActivity", "node")
            setOnClickPendingIntent(R.id.widget_node_area, node)
            setBoolean(R.id.widget_node_area, "setEnabled", !busy)
            setOnClickPendingIntent(R.id.widget_configure, if (configured) config else openApp)
            val refresh = PendingIntent.getBroadcast(context, 0,
                controlIntent(context, ticket, "refresh").putExtra("action", "refresh"), FLAGS)
            setOnClickPendingIntent(R.id.widget_refresh, if (!configured) openApp else if (binding == null) config else refresh)
        }
    }
    fun render(context: Context, id: Int) {
        if (id !in WidgetCoordinator.installedIds(context)) return
        AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, id))
    }
    fun renderAll(context: Context) { WidgetCoordinator.installedIds(context).forEach { render(context, it) } }
}
