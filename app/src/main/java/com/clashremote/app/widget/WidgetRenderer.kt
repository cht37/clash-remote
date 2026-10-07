package com.clashremote.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.SizeF
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
        .setComponent(WidgetCoordinator.provider(context, ticket.widgetId)
            ?: ComponentName(context.packageName, WidgetCoordinator.PROVIDER))
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
        if (Build.VERSION.SDK_INT >= 31) {
            // The launcher selects a fitting layout for each orientation and resize without a network request.
            val sizes = listOf(SizeF(220f, 110f), SizeF(110f, 110f), SizeF(250f, 40f), SizeF(220f, 180f))
            return RemoteViews(sizes.associateWith { views(context, id, it) })
        }
        val default = when (WidgetCoordinator.provider(context, id)?.className) {
            WidgetCoordinator.SQUARE_PROVIDER -> SizeF(110f, 110f)
            WidgetCoordinator.SLIM_PROVIDER -> SizeF(250f, 40f)
            WidgetCoordinator.LARGE_PROVIDER -> SizeF(250f, 180f)
            else -> SizeF(250f, 110f)
        }
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        fun dimension(key: String, fallback: Float) = options.getInt(key).takeIf { it > 0 }?.toFloat() ?: fallback
        val portrait = views(context, id, SizeF(dimension(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, default.width),
            dimension(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, default.height)))
        val landscape = views(context, id, SizeF(dimension(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, default.width),
            dimension(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, default.height)))
        return RemoteViews(landscape, portrait)
    }

    internal fun views(context: Context, id: Int, size: SizeF): RemoteViews {
        val layout = when {
            size.width >= 220f && size.height < 100f -> R.layout.widget_proxy_slim
            size.width < 220f -> R.layout.widget_proxy_square
            size.height >= 180f -> R.layout.widget_proxy_large
            else -> R.layout.widget_proxy
        }
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
        return RemoteViews(context.packageName, layout).apply {
            val status = when {
                !configured -> "未配置"
                binding == null -> "请选择策略组"
                busy -> "正在处理…"
                cache.error != null -> "同步失败"
                cache.updatedAt == 0L -> "待刷新"
                else -> "更新于 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cache.updatedAt))}"
            }
            val compactStatus = when {
                !configured -> "未配置"
                binding == null -> "未绑定"
                busy -> "处理中"
                cache.error != null -> "失败"
                cache.updatedAt == 0L -> "待刷新"
                else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cache.updatedAt))
            }
            setTextViewText(R.id.widget_time, compactStatus)
            setContentDescription(R.id.widget_refresh, "${context.getString(R.string.widget_refresh)}，$status${cache.error?.let { "，$it" }.orEmpty()}")
            setTextViewText(R.id.widget_router, cache.routerName ?: if (configured) "路由器" else "未配置路由器")
            if (layout == R.layout.widget_proxy_square)
                setViewVisibility(R.id.widget_router, View.GONE)
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
            setTextViewText(R.id.widget_label, if (!configured) "点击打开设置" else binding?.group ?: "选择策略组")
            setTextViewText(R.id.widget_detail, if (!configured) "点击打开设置" else cache.error ?: if (binding == null) "点击完成微件配置" else "")
            setTextColor(R.id.widget_detail, if (cache.error != null && configured) context.getColor(R.color.widget_error) else secondary)
            setViewVisibility(R.id.widget_label, View.VISIBLE)
            setViewVisibility(R.id.widget_detail, if (layout == R.layout.widget_proxy_large && cache.error != null && configured)
                View.VISIBLE else View.GONE)
            setContentDescription(R.id.widget_configure, "${context.getString(R.string.widget_select_group)}，${binding?.group ?: "未绑定"}")
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
            setContentDescription(R.id.widget_title_area, "${context.getString(R.string.app_name)}，${cache.routerName ?: "路由器"}")
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
