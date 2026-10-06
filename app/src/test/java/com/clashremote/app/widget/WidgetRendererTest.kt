package com.clashremote.app.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.clashremote.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: WidgetStore
    @Before fun setup() {
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().clear().commit()
        store = WidgetStore(context)
    }
    @Test fun modePendingIntentsHaveDistinctIdentityAndContainNoCredential() {
        val ticket = WidgetTicket(7, "binding-7", "revision-1", "request-1")
        val rule = WidgetRenderer.modeIntent(context, ticket, "rule")
        val global = WidgetRenderer.modeIntent(context, ticket, "global")
        val other = WidgetRenderer.modeIntent(context, ticket.copy(widgetId = 8), "rule")
        assertNotEquals(rule, global); assertNotEquals(rule, other)
        val intent = shadowOf(rule).savedIntent
        assertEquals("rule", intent.getStringExtra("mode"))
        assertEquals(7, intent.getIntExtra("widgetId", -1))
        assertFalse(intent.hasExtra("secret"))
        assertEquals("com.clashremote.app.widget.ProxyWidgetProvider", intent.component!!.className)
        assertTrue(rule.isImmutable)
    }
    @Test fun unconfiguredWidgetShowsSetupAndDoesNotInventRuleMode() {
        val view = WidgetRenderer.views(context, 7).apply(context, FrameLayout(context))
        assertEquals("请先配置路由器", view.findViewById<TextView>(R.id.widget_node).text.toString())
        assertFalse(view.findViewById<TextView>(R.id.widget_rule).isEnabled)
        assertFalse(view.findViewById<TextView>(R.id.widget_rule).contentDescription.toString().contains("当前"))
    }
    @Test fun failedRefreshRetainsConfirmedNodeAndShowsError() {
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().putString("profile", "stored").commit()
        store.bind(7, "代理")
        store.saveCache(7, WidgetCache("global", "香港 01", 123, "连接失败", routerName = "家庭路由器"))
        val view = WidgetRenderer.views(context, 7).apply(context, FrameLayout(context))
        assertEquals("香港 01", view.findViewById<TextView>(R.id.widget_node).text.toString())
        assertEquals("连接失败", view.findViewById<TextView>(R.id.widget_detail).text.toString())
        assertTrue(view.findViewById<TextView>(R.id.widget_global).contentDescription.toString().contains("当前"))
        assertTrue(view.findViewById<TextView>(R.id.widget_router).text.toString().contains("家庭路由器"))
    }
    @Test fun expiredBusyLeaseDoesNotPermanentlyDisableModeButtons() {
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().putString("profile", "stored").commit()
        store.bind(7, "代理")
        store.saveCache(7, WidgetCache("rule", "香港 01", 123, busyUntil = 1))
        val view = WidgetRenderer.views(context, 7).apply(context, FrameLayout(context))
        assertTrue(view.findViewById<TextView>(R.id.widget_rule).isEnabled)
    }
    @Test fun snapshotFromPreviousRouterIsIgnoredEvenIfInvalidationWasInterrupted() {
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().putString("profile", "stored")
            .putString("widget_revision", "revision-2").commit()
        store.bind(7, "代理")
        store.saveCache(7, WidgetCache("global", "旧路由器节点", 123, profileRevision = "revision-1"))
        val view = WidgetRenderer.views(context, 7).apply(context, FrameLayout(context))
        assertEquals("选择节点", view.findViewById<TextView>(R.id.widget_node).text.toString())
        assertFalse(view.findViewById<TextView>(R.id.widget_global).contentDescription.toString().contains("当前"))
    }
}
