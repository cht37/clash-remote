package com.clashremote.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.clashremote.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetSizeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private data class Preset(val provider: String, val metadata: Int, val size: SizeF, val columns: Int, val rows: Int)
    private val presets = listOf(
        Preset(WidgetCoordinator.PROVIDER, R.xml.proxy_widget_info, SizeF(250f, 110f), 4, 2),
        Preset(WidgetCoordinator.SQUARE_PROVIDER, R.xml.proxy_widget_square_info, SizeF(110f, 110f), 2, 2),
        Preset(WidgetCoordinator.SLIM_PROVIDER, R.xml.proxy_widget_slim_info, SizeF(250f, 40f), 4, 1),
        Preset(WidgetCoordinator.LARGE_PROVIDER, R.xml.proxy_widget_large_info, SizeF(250f, 180f), 4, 3),
    )
    private lateinit var store: WidgetStore

    @Before fun setup() {
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().clear()
            .putString("profile", "stored").commit()
        store = WidgetStore(context)
        presets.forEachIndexed { i, preset ->
            val id = i + 7
            val info = AppWidgetProviderInfo().apply {
                provider = ComponentName(context.packageName, preset.provider)
                initialLayout = R.layout.widget_proxy
            }
            shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id, info)
            store.bind(id, "很长的代理策略组")
            store.saveCache(id, WidgetCache("global", "香港 01 / 节点名称很长", 123, routerName = "家庭路由器"))
        }
    }

    @Test fun advertisedGridSizesDoNotRequireMoreThanTheirDefaultCellBounds() {
        val namespace = "http://schemas.android.com/apk/res/android"
        for (preset in presets) {
            context.resources.getXml(preset.metadata).use { parser ->
                while (parser.eventType != XmlPullParser.START_TAG) parser.next()
                assertEquals(preset.columns, parser.getAttributeIntValue(namespace, "targetCellWidth", 0))
                assertEquals(preset.rows, parser.getAttributeIntValue(namespace, "targetCellHeight", 0))
                val attributes = context.resources.obtainAttributes(parser,
                    intArrayOf(android.R.attr.minWidth, android.R.attr.minHeight))
                try {
                    val density = context.resources.displayMetrics.density
                    assertTrue(attributes.getDimension(0, 0f) / density <= preset.size.width)
                    assertTrue(attributes.getDimension(1, 0f) / density <= preset.size.height)
                } finally { attributes.recycle() }
            }
        }
    }

    @Test fun allPresetLayoutsFitTheirMinimumSizeWithNormalAndLargeFonts() {
        for (scale in listOf(1f, 1.3f)) {
            val sizedContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                fontScale = scale
            })
            presets.forEachIndexed { i, preset ->
                val view = measured(sizedContext, i + 7, preset.size)
                if (scale == 1f) {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file = File("build/reports/widget-previews/${preset.columns}x${preset.rows}.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                assertInsideParents(view, "${preset.columns}x${preset.rows}, font=$scale")
                for (id in listOf(R.id.widget_rule, R.id.widget_global, R.id.widget_direct,
                    R.id.widget_node_area, R.id.widget_configure, R.id.widget_refresh, R.id.widget_title_area)) {
                    val control = view.findViewById<View>(id)
                    assertEquals(View.VISIBLE, control.visibility)
                    assertTrue(control.width > 0 && control.height > 0)
                    assertTrue(control.isClickable)
                }
            }
        }
    }

    @Test fun resizingKeepsConfirmedModeNodeAndErrorAcrossEveryLayout() {
        store.saveCache(7, WidgetCache("global", "香港 01", 123, "操作可能已生效，请刷新确认", routerName = "家庭路由器"))
        for (preset in presets) {
            val view = measured(context, 7, preset.size)
            assertEquals("香港 01", view.findViewById<TextView>(R.id.widget_node).text.toString())
            assertEquals("操作可能已生效，请刷新确认", view.findViewById<TextView>(R.id.widget_detail).text.toString())
            assertTrue(view.findViewById<TextView>(R.id.widget_global).contentDescription.toString().contains("当前模式"))
            assertInsideParents(view, "error at ${preset.size}")
        }
    }

    @Test fun nodeAndGroupNamesAreFullyVisibleAtEveryDefaultSize() {
        presets.forEachIndexed { i, preset ->
            val view = measured(context, i + 7, preset.size)
            for ((id, text) in listOf(R.id.widget_node to "香港 01 / 节点名称很长", R.id.widget_label to "很长的代理策略组")) {
                val field = view.findViewById<TextView>(id)
                assertEquals(text, field.text.toString())
                assertEquals(View.VISIBLE, field.visibility)
                assertTrue("${preset.size}: $text exceeds visible lines", field.layout.lineCount <= field.maxLines)
                for (line in 0 until field.layout.lineCount)
                    assertEquals("${preset.size}: $text truncated", 0, field.layout.getEllipsisCount(line))
            }
        }
    }

    @Test fun everySizeIsIncludedInSynchronizationAndRoutesToItsOwnProvider() {
        val declared = context.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName), 0)
        assertEquals(presets.map { it.provider }.toSet(), declared.map { it.activityInfo.name }.toSet())
        val runtime = WidgetRuntime.from(context)
        assertEquals(setOf(7, 8, 9, 10), WidgetCoordinator.installedIds(context).toSet())
        presets.forEachIndexed { i, preset ->
            val id = i + 7
            val ticket = runtime.ticket(id)!!
            val intent = shadowOf(WidgetRenderer.modeIntent(context, ticket, "rule")).savedIntent
            assertEquals(preset.provider, intent.component!!.className)
            WidgetCoordinator.render(context, id)
            assertEquals(preset.provider, shadowOf(context).broadcastIntents.last().component!!.className)
        }
        val foreign = AppWidgetProviderInfo().apply {
            provider = ComponentName("another.application", WidgetCoordinator.SQUARE_PROVIDER)
        }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(99, foreign)
        store.bind(99, "代理")
        assertNull(runtime.ticket(99))
    }

    private fun measured(context: Context, id: Int, size: SizeF): ViewGroup {
        val view = WidgetRenderer.views(context, id, size).apply(context, FrameLayout(context)) as ViewGroup
        val density = context.resources.displayMetrics.density
        val width = (size.width * density).toInt()
        val height = (size.height * density).toInt()
        // Auto-sizing text may request another layout after the first measurement.
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
        return view
    }

    private fun assertInsideParents(parent: ViewGroup, scenario: String) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val label = if (child.id == View.NO_ID) child.javaClass.simpleName else context.resources.getResourceEntryName(child.id)
            val bounds = Rect(child.left, child.top, child.right, child.bottom)
            assertTrue("$scenario: $label clipped: $bounds in ${parent.width}x${parent.height}",
                bounds.left >= 0 && bounds.top >= 0 && bounds.right <= parent.width && bounds.bottom <= parent.height)
            if (child is TextView && child.layout != null) {
                val available = child.height - child.compoundPaddingTop - child.compoundPaddingBottom
                // Fallback-font line boxes can round differently from TextView's measured height.
                // Check the visible glyphs at their actual baselines rather than unused line-box leading.
                for (line in 0 until minOf(child.layout.lineCount, child.maxLines)) {
                    val text = child.layout.text.subSequence(child.layout.getLineStart(line), child.layout.getLineEnd(line)).toString()
                    val glyphs = Rect()
                    child.paint.getTextBounds(text, 0, text.length, glyphs)
                    val baseline = child.layout.getLineBaseline(line)
                    assertTrue("$scenario: $label glyphs clipped: $glyphs at $baseline in $available",
                        baseline + glyphs.top >= 0 && baseline + glyphs.bottom <= available)
                }
            }
            if (child is ViewGroup) assertInsideParents(child, scenario)
        }
    }
}
