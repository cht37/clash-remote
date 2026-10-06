package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdatesTest {
    @Test fun versionsCompareNumericallyAndIgnoreLeadingV() {
        assertTrue(isNewerVersion("v0.10.0", "0.2.0"))
        assertTrue(isNewerVersion("1.0.0", "0.99.99"))
        assertFalse(isNewerVersion("v0.2.0", "0.2.0"))
        assertFalse(isNewerVersion("v0.1.9", "0.2.0"))
        assertTrue(runCatching { isNewerVersion("nightly", "0.2.0") }.isFailure)
    }
    @Test fun latestReleaseUsesPublicApiAndPrefersUniversalApk() = runBlocking {
        val server = MockWebServer(); server.start()
        val client = GitHubReleaseClient("0.2.0", server.url("/repos/cht37/clash-remote/releases/latest"))
        try {
            server.enqueue(MockResponse().setBody("""{"tag_name":"v0.3.0","html_url":"https://github.com/cht37/clash-remote/releases/tag/v0.3.0","body":"Changes","assets":[{"name":"arm64.apk","browser_download_url":"https://github.com/cht37/clash-remote/releases/download/v0.3.0/arm64.apk","size":10},{"name":"universal.apk","browser_download_url":"https://github.com/cht37/clash-remote/releases/download/v0.3.0/universal.apk","size":20}]}"""))
            val release = client.latest()!!
            assertEquals("v0.3.0", release.tag)
            assertTrue(release.apkUrl!!.endsWith("universal.apk"))
            assertEquals(20L, release.apkSize)
            val request = server.takeRequest()
            assertEquals("/repos/cht37/clash-remote/releases/latest", request.path)
            assertNull(request.getHeader("Authorization"))
        } finally { client.close(); server.shutdown() }
    }
    @Test fun missingReleaseIsNormalAndUntrustedAssetsAreNotOffered() = runBlocking {
        val server = MockWebServer(); server.start()
        val client = GitHubReleaseClient("0.2.0", server.url("/latest"))
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(client.latest())
            server.enqueue(MockResponse().setBody("""{"tag_name":"v0.3.0","assets":[{"name":"fake.apk","browser_download_url":"https://evil.example/app.apk"}]}"""))
            assertNull(client.latest()!!.apkUrl)
            server.enqueue(MockResponse().setResponseCode(403))
            assertTrue(runCatching { client.latest() }.exceptionOrNull() is ClashException)
            server.enqueue(MockResponse().setBody("<html>bad</html>"))
            assertTrue(runCatching { client.latest() }.exceptionOrNull() is ClashException)
        } finally { client.close(); server.shutdown() }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun updateStateDistinguishesAvailableAndNoRelease() = runTest {
        var response: ReleaseInfo? = ReleaseInfo("v0.3.0", "Update", "Changes", RELEASES_PAGE, null, 0)
        val controller = UpdateController(backgroundScope, "0.2.0", object : ReleaseSource {
            override suspend fun latest() = response
            override fun close() {}
        })
        controller.check(); runCurrent()
        assertEquals(UpdateStatus.AVAILABLE, controller.state.value.status)
        response = null; controller.check(); runCurrent()
        assertEquals(UpdateStatus.NO_RELEASE, controller.state.value.status)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancellationCannotPublishOutdatedUpdateResult() = runTest {
        val result = CompletableDeferred<ReleaseInfo?>()
        val controller = UpdateController(backgroundScope, "0.2.0", object : ReleaseSource {
            override suspend fun latest() = withContext(NonCancellable) { result.await() }
            override fun close() {}
        })
        controller.check(); runCurrent(); controller.cancel()
        result.complete(ReleaseInfo("v0.3.0", "Update", "", RELEASES_PAGE, null, 0)); runCurrent()
        assertEquals(UpdateStatus.NOT_CHECKED, controller.state.value.status)
        assertFalse(controller.state.value.checking)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun failedFreshCheckDoesNotStillClaimUpToDate() = runTest {
        var fail = false
        val controller = UpdateController(backgroundScope, "0.2.0", object : ReleaseSource {
            override suspend fun latest(): ReleaseInfo {
                if (fail) throw ClashException("GitHub 请求受限")
                return ReleaseInfo("v0.2.0", "Current", "", RELEASES_PAGE, null, 0)
            }
            override fun close() {}
        })
        controller.check(); runCurrent()
        assertEquals(UpdateStatus.UP_TO_DATE, controller.state.value.status)
        fail = true; controller.check(); runCurrent()
        assertEquals(UpdateStatus.NOT_CHECKED, controller.state.value.status)
        assertNotNull(controller.state.value.error)
    }
}
