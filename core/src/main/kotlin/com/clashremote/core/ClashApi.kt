package com.clashremote.core

import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ClashApi {
    suspend fun version(): String
    suspend fun config(): RuntimeConfig
    suspend fun proxies(): ProxySnapshot
    suspend fun connections(): ConnectionSnapshot
    suspend fun setMode(mode: String)
    suspend fun selectProxy(group: String, name: String)
    suspend fun delay(name: String, url: String): Int
    suspend fun closeConnection(id: String?)
    fun traffic(): Flow<Traffic>
    fun close()
}

class OkHttpClashApi(profile: RouterProfile) : ClashApi {
    private val profile = profile.validated()
    private val base = this.profile.endpoint.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build()

    private fun url(vararg parts: String): HttpUrl = base.newBuilder().apply {
        parts.forEach { addPathSegment(it) }
    }.build()
    private fun request(url: HttpUrl) = Request.Builder().url(url).apply {
        if (profile.secret.isNotEmpty()) header("Authorization", "Bearer ${profile.secret}")
    }
    private fun body(key: String, value: String) = buildJsonObject { put(key, value) }
        .toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(networkError(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            if (!it.isSuccessful) throw httpError(it.code)
            try { if (it.code == 204) "" else it.body?.string().orEmpty() }
            catch (e: IOException) { throw networkError(e) }
        }
    }
    private suspend inline fun <reified T> read(url: HttpUrl): T = withContext(Dispatchers.IO) {
        decode(send(request(url).get().build()))
    }
    private inline fun <reified T> decode(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: Exception) { throw ClashException("返回数据与 Clash 控制 API 不兼容，请检查控制地址", e) }

    override suspend fun version(): String {
        val result: JsonObject = read(url("version"))
        return result["version"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw ClashException("未收到内核版本，请检查控制 API 地址")
    }
    override suspend fun config(): RuntimeConfig = read(url("configs"))
    override suspend fun proxies(): ProxySnapshot {
        val data: ProxySnapshot = read(url("proxies"))
        return data.copy(proxies = data.proxies.mapValues { (name, proxy) -> proxy.copy(name = name) })
    }
    override suspend fun connections(): ConnectionSnapshot {
        val root: JsonObject = read(url("connections"))
        if (!root.containsKey("connections")) throw ClashException("连接接口返回格式不兼容")
        return decode(root.toString())
    }
    override suspend fun setMode(mode: String) {
        require(mode in listOf("rule", "global", "direct"))
        send(request(url("configs")).patch(body("mode", mode)).build())
    }
    override suspend fun selectProxy(group: String, name: String) {
        send(request(url("proxies", group)).put(body("name", name)).build())
    }
    override suspend fun delay(name: String, url: String): Int {
        val target = url("proxies", name, "delay").newBuilder()
            .addQueryParameter("url", url).addQueryParameter("timeout", "5000").build()
        val data: JsonObject = read(target)
        return data["delay"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
            ?: throw ClashException("节点测速未成功")
    }
    override suspend fun closeConnection(id: String?) {
        val target = if (id == null) url("connections") else url("connections", id)
        send(request(target).delete().build())
    }
    override fun traffic(): Flow<Traffic> = callbackFlow {
        val socket = client.newWebSocket(request(url("traffic")).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try { trySend(decode<Traffic>(text)) } catch (e: Exception) { close(e) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(if (response != null) httpError(response.code) else networkError(t))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close(ClashException("实时流量连接已关闭，请重试"))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                close(ClashException("实时流量连接已关闭，请重试"))
            }
        })
        awaitClose { socket.cancel() }
    }
    override fun close() {
        client.dispatcher.cancelAll(); client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
    private fun httpError(code: Int) = ClashException(when (code) {
        401, 403 -> "控制密钥不正确或访问被拒绝，请检查 secret"
        404 -> "未找到控制 API，请检查地址和端口"
        in 300..399 -> "控制地址发生重定向，请直接填写最终 API 地址"
        else -> "路由器返回错误（HTTP $code），请稍后重试"
    })
    private fun networkError(t: Throwable) = ClashException(
        if (t is SSLException) "证书验证失败，请使用受信任的 HTTPS 证书"
        else "无法连接路由器，请检查 Wi-Fi、控制地址、监听端口和防火墙", t,
    )
}
