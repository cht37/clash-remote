package com.clashremote.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: WidgetStore
    @Before fun setup() {
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().clear().commit()
        store = WidgetStore(context)
    }
    @Test fun widgetsKeepIndependentBindingsAcrossStoreRecreation() {
        store.bind(7, "代理")
        store.bind(8, "视频")
        assertEquals("代理", WidgetStore(context).binding(7)!!.group)
        assertEquals("视频", WidgetStore(context).binding(8)!!.group)
        assertNotEquals(store.binding(7)!!.token, store.binding(8)!!.token)
    }
    @Test fun rebindingInvalidatesOldTokenAndCache() {
        val old = store.bind(7, "代理")
        store.saveCache(7, WidgetCache("rule", "香港 01", 123))
        val current = store.bind(7, "视频")
        assertNotEquals(old.token, current.token)
        assertNull(store.cache(7))
    }
    @Test fun deletingWidgetAlsoDeletesSnapshotAndAttemptMarker() {
        store.bind(7, "代理")
        store.saveCache(7, WidgetCache("rule", "香港 01", 123))
        assertTrue(store.claimWrite(7, "request-1"))
        assertFalse(store.claimWrite(7, "request-1"))
        store.remove(7)
        assertNull(store.binding(7))
        assertNull(store.cache(7))
        assertTrue(store.claimWrite(7, "request-1"))
    }
    @Test fun invalidationKeepsGroupButDropsRemoteState() {
        store.bind(7, "代理")
        store.saveCache(7, WidgetCache("global", "香港 01", 123))
        store.invalidate()
        assertEquals("代理", store.binding(7)!!.group)
        assertNull(store.cache(7))
    }
}
