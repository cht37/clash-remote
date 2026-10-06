package com.clashremote.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClashApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OkHttpClashApi
    @Before fun setup() {
        server = MockWebServer(); server.start()
        api = OkHttpClashApi(RouterProfile("路由器", server.url("/api/").toString(), "test-secret"))
    }
    @After fun cleanup() { api.close(); server.shutdown() }

    @Test fun selectionEncodesNameAndKeepsPrefix() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.selectProxy("香港/自动 🚀", "节点 A")
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("Bearer test-secret", request.getHeader("Authorization"))
        assertEquals("/api/proxies/%E9%A6%99%E6%B8%AF%2F%E8%87%AA%E5%8A%A8%20%F0%9F%9A%80", request.path)
        assertEquals("节点 A", Json.parseToJsonElement(request.body.readUtf8()).jsonObject["name"]!!.jsonPrimitive.content)
    }
    @Test fun modeAndCloseAcceptEmpty204() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }
        api.setMode("global"); api.closeConnection("abc/123"); api.closeConnection(null)
        assertEquals("PATCH", server.takeRequest().method)
        assertEquals("/api/connections/abc%2F123", server.takeRequest().path)
        assertEquals("DELETE", server.takeRequest().method)
    }
    @Test fun authenticationErrorIsReadable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val failure = runCatching { api.version() }.exceptionOrNull()
        assertTrue(failure is ClashException)
        assertTrue(failure!!.message!!.contains("密钥"))
        assertFalse(failure.message!!.contains("test-secret"))
    }
    @Test fun unknownFieldsAndMissingOptionalFieldsAreAccepted() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"proxies":{"自动":{"type":"Selector","all":["A"],"now":"A","new-field":true},"A":{"type":"Shadowsocks"}},"extra":true}"""))
        val result = api.proxies()
        assertEquals(listOf("A"), result.proxies.getValue("自动").all)
        assertEquals("A", result.proxies.getValue("A").name)
        server.enqueue(MockResponse().setBody("""{"uploadTotal":24,"downloadTotal":42,"connections":null}"""))
        assertTrue(api.connections().connections.isEmpty())
    }
    @Test fun incompatibleResponseIsRejected() = runBlocking {
        for (body in listOf("<html>dashboard</html>", "{}")) {
            server.enqueue(MockResponse().setBody(body))
            assertTrue(runCatching { api.proxies() }.exceptionOrNull() is ClashException)
        }
    }
    @Test fun redirectsAreNotFollowed() = runBlocking {
        val destination = MockWebServer(); destination.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/version")))
            assertTrue(runCatching { api.version() }.isFailure)
            assertEquals(0, destination.requestCount)
        } finally { destination.shutdown() }
    }
    @Test fun delayQueryIsEncoded() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"delay":128}"""))
        assertEquals(128, api.delay("节点/A", "https://example.com/a?b=c&d=e"))
        val request = server.takeRequest().requestUrl!!
        assertEquals("https://example.com/a?b=c&d=e", request.queryParameter("url"))
        assertEquals("5000", request.queryParameter("timeout"))
    }
    @Test fun websocketUsesHeaderAndParsesSpeed() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("""{"up":12,"down":34,"upTotal":1000}""")
            }
        }))
        val traffic = withTimeout(5000) { api.traffic().first() }
        assertEquals(12L, traffic.up); assertEquals(34L, traffic.down)
        assertEquals("Bearer test-secret", server.takeRequest().getHeader("Authorization"))
    }
    @Test fun serverCloseTerminatesTrafficInsteadOfFreezingIt() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                webSocket.send("""{"up":12,"down":34}""")
                webSocket.close(1000, "finished")
            }
        }))
        val failure = runCatching { withTimeout(2000) { api.traffic().collect() } }.exceptionOrNull()
        assertTrue("A controller Close frame must terminate the stream with an actionable error", failure is ClashException)
    }
    @Test fun addressesRejectCredentialsQueriesAndFragments() {
        for (endpoint in listOf("ftp://router", "http://u:p@router", "http://router?q=x", "http://router/#x", "", "http://router:0")) {
            assertTrue(endpoint, runCatching { RouterProfile(endpoint = endpoint).validated() }.isFailure)
        }
        assertEquals("http://[fd00::1]:9090/", RouterProfile(endpoint = "http://[fd00::1]:9090").validated().endpoint)
        assertEquals("http://router.lan:9090/prefix/", RouterProfile(endpoint = " http://router.lan:9090/prefix ").validated().endpoint)
    }
}
