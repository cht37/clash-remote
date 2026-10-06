package com.clashremote.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.clashremote.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun forgedConfigurationIdIsCancelledWithoutCreatingBinding() {
        val intent = Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 99)
        val controller = Robolectric.buildActivity(WidgetConfigActivity::class.java, intent).create()
        assertTrue(controller.get().isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
        assertNull(WidgetStore(context).binding(99))
        controller.destroy()
    }
    @Test fun cancellingReconfigurationKeepsPreviousGroupAndToken() {
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().clear().commit()
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, ProxyWidgetProvider::class.java)
            initialLayout = R.layout.widget_proxy
        }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(7, info)
        val old = WidgetStore(context).bind(7, "代理")
        val intent = Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7)
        val controller = Robolectric.buildActivity(WidgetConfigActivity::class.java, intent).create()
        controller.get().finish()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
        assertEquals(old, WidgetStore(context).binding(7))
        controller.destroy()
    }
}
