package com.clashremote.core

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"

@Serializable
data class RouterProfile(
    val name: String = "我的路由器",
    val endpoint: String = "",
    val secret: String = "",
    val testUrl: String = DEFAULT_TEST_URL,
) {
    fun validated(): RouterProfile {
        val base = endpoint.trim().toHttpUrlOrNull() ?: throw ClashException("控制地址需为有效的 HTTP 或 HTTPS 地址")
        if (base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null) {
            throw ClashException("控制地址不能包含用户名、密码、查询参数或 # 片段")
        }
        if (secret.any { it == '\r' || it == '\n' || it.code < 32 || it.code > 126 }) {
            throw ClashException("控制密钥需使用不含换行的可打印 ASCII 字符")
        }
        val test = testUrl.trim().toHttpUrlOrNull() ?: throw ClashException("测速地址需为有效的 HTTP 或 HTTPS 地址")
        if (test.username.isNotEmpty() || test.password.isNotEmpty() || test.fragment != null) {
            throw ClashException("测速地址不能包含用户名、密码或 # 片段")
        }
        val normalized = base.newBuilder().apply {
            if (!base.encodedPath.endsWith('/')) addPathSegment("")
        }.build().toString()
        return copy(name = name.trim().ifEmpty { "我的路由器" }, endpoint = normalized, testUrl = test.toString())
    }
}

class ClashException(message: String, cause: Throwable? = null) : Exception(message, cause)
