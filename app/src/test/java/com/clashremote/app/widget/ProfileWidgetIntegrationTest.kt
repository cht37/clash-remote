package com.clashremote.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.clashremote.app.storage.ProfileStore
import com.clashremote.core.ProfileCodec
import com.clashremote.core.RouterProfile
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileWidgetIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profile = RouterProfile("家庭路由器", "http://192.168.1.1:9090", "test-secret")
    private fun store() = ProfileStore(context, ProfileCodec { SecretKeySpec(ByteArray(32) { 3 }, "AES") })
    @Before fun setup() {
        context.getSharedPreferences("router", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun savingSameProfileRotatesRevisionAndPreservesEncryptedConfiguration() {
        val store = store()
        store.save(profile)
        val first = WidgetCoordinator.profileRevision(context)
        store.save(profile)
        assertNotEquals("legacy", first)
        assertNotEquals(first, WidgetCoordinator.profileRevision(context))
        assertEquals(profile.validated(), store.load())
        val encoded = context.getSharedPreferences("router", Context.MODE_PRIVATE).getString("profile", "")!!
        assertFalse(encoded.contains("test-secret"))
    }
    @Test fun clearDropsCredentialsAndInvalidatesInFlightRevision() {
        val store = store()
        store.save(profile)
        val before = WidgetCoordinator.profileRevision(context)
        store.clear()
        assertNull(store.load())
        assertNotEquals(before, WidgetCoordinator.profileRevision(context))
    }
}
