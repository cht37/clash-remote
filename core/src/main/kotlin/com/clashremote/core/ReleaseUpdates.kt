package com.clashremote.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val RELEASES_PAGE = "https://github.com/cht37/clash-remote/releases/latest"
private const val LATEST_API = "https://api.github.com/repos/cht37/clash-remote/releases/latest"
data class ReleaseInfo(val tag: String, val name: String, val notes: String, val pageUrl: String, val apkUrl: String?, val apkSize: Long)
interface ReleaseSource { suspend fun latest(): ReleaseInfo?; fun close() }

fun isNewerVersion(latest: String, current: String): Boolean {
    fun parse(value: String): List<Long> {
        val match = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+[A-Za-z0-9.-]+)?$").matchEntire(value.trim())
            ?: throw ClashException("发行版版本号无法识别，请查看 GitHub 发行页面")
        return match.groupValues.drop(1).map { it.toLongOrNull() ?: throw ClashException("发行版版本号无效") }
    }
    val a = parse(latest); val b = parse(current)
    return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
}

class GitHubReleaseClient(currentVersion: String, private val endpoint: HttpUrl = LATEST_API.toHttpUrl()) : ReleaseSource {
    @Serializable private data class Asset(val name: String = "", val browser_download_url: String = "", val size: Long = 0)
    @Serializable private data class Release(val tag_name: String, val name: String = "", val body: String = "", val html_url: String = "", val draft: Boolean = false, val prerelease: Boolean = false, val assets: List<Asset> = emptyList())
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val userAgent = "ClashRemoteAndroid/$currentVersion"
    private fun trustedUrl(value: String, prefix: String): String? {
        val url = value.toHttpUrlOrNull() ?: return null
        return url.takeIf { it.scheme == "https" && it.host == "github.com" && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() && it.encodedPath.startsWith(prefix) }?.toString()
    }
    override suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint).header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent).header("X-GitHub-Api-Version", "2022-11-28").build()
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ClashException("无法访问 GitHub，请检查网络后重试", e))
                }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
            })
        }
        response.use {
            if (it.code == 404) return@withContext null
            if (it.code == 403 || it.code == 429) throw ClashException("GitHub 请求受限，请稍后重试")
            if (!it.isSuccessful) throw ClashException("GitHub 返回错误（HTTP ${it.code}），请稍后重试")
            val release = try { json.decodeFromString<Release>(it.body?.string().orEmpty()) }
            catch (e: Exception) { throw ClashException("无法读取 GitHub 发行版信息，请稍后重试", e) }
            if (release.draft || release.prerelease) return@withContext null
            val apk = release.assets.filter { asset -> asset.name.endsWith(".apk", true) && trustedUrl(asset.browser_download_url, "/cht37/clash-remote/releases/download/") != null }
                .sortedBy { asset -> if (asset.name.contains("universal", true)) 0 else 1 }.firstOrNull()
            ReleaseInfo(release.tag_name, release.name.ifBlank { release.tag_name }, release.body,
                trustedUrl(release.html_url, "/cht37/clash-remote/releases/tag/") ?: RELEASES_PAGE,
                apk?.let { asset -> trustedUrl(asset.browser_download_url, "/cht37/clash-remote/releases/download/") }, apk?.size ?: 0)
        }
    }
    override fun close() { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
}
enum class UpdateStatus { NOT_CHECKED, UP_TO_DATE, AVAILABLE, NO_RELEASE }
data class UpdateState(val status: UpdateStatus = UpdateStatus.NOT_CHECKED, val checking: Boolean = false, val release: ReleaseInfo? = null, val error: String? = null)
class UpdateController(private val scope: CoroutineScope, private val currentVersion: String, private val source: ReleaseSource) {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    fun check() {
        if (state.value.checking) return
        val id = ++generation
        mutable.value = UpdateState(checking = true)
        job = scope.launch {
            try {
                val release = source.latest()
                val status = when { release == null -> UpdateStatus.NO_RELEASE; isNewerVersion(release.tag, currentVersion) -> UpdateStatus.AVAILABLE; else -> UpdateStatus.UP_TO_DATE }
                if (id == generation) mutable.value = UpdateState(status = status, release = release)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (id == generation) mutable.update { it.copy(error = if (e is ClashException) e.message else "更新检查失败，请重试") }
            } finally { if (id == generation) mutable.update { it.copy(checking = false) } }
        }
    }
    fun cancel() { generation++; job?.cancel(); job = null; mutable.update { it.copy(checking = false) } }
    fun close() { cancel(); source.close() }
}
