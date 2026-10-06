package com.clashremote.app.widget

import android.content.Context
import com.clashremote.core.ClashException
import org.json.JSONObject
import java.util.UUID

data class WidgetBinding(val widgetId: Int, val group: String, val token: String)
data class WidgetCache(
    val mode: String? = null, val node: String? = null, val updatedAt: Long = 0,
    val error: String? = null, val busyUntil: Long = 0, val routerName: String? = null,
    val profileRevision: String = "legacy",
)
data class WidgetTicket(
    val widgetId: Int, val bindingToken: String, val profileRevision: String, val requestToken: String,
)

class WidgetStore(context: Context) {
    private val preferences = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    fun bind(id: Int, group: String): WidgetBinding = synchronized(lock) {
        require(id > 0 && group.isNotBlank())
        val binding = WidgetBinding(id, group, UUID.randomUUID().toString())
        commit(preferences.edit().putString("binding:$id", JSONObject().put("group", group)
            .put("token", binding.token).toString()).remove("cache:$id").remove("attempts:$id"))
        binding
    }
    fun binding(id: Int): WidgetBinding? = synchronized(lock) {
        read("binding:$id")?.let {
            val group = it.optString("group")
            val token = it.optString("token")
            if (group.isBlank() || token.isBlank()) null else WidgetBinding(id, group, token)
        }
    }
    fun bindings(): List<WidgetBinding> = synchronized(lock) {
        preferences.all.keys.filter { it.startsWith("binding:") }
            .mapNotNull { it.substringAfter(':').toIntOrNull()?.let(::binding) }
    }
    fun cache(id: Int): WidgetCache? = synchronized(lock) {
        read("cache:$id")?.let {
            WidgetCache(it.stringOrNull("mode"), it.stringOrNull("node"), it.optLong("updatedAt"),
                it.stringOrNull("error"), it.optLong("busyUntil"), it.stringOrNull("routerName"),
                it.optString("profileRevision", "legacy"))
        }
    }
    fun saveCache(id: Int, cache: WidgetCache) = synchronized(lock) {
        val json = JSONObject().put("mode", cache.mode).put("node", cache.node)
            .put("updatedAt", cache.updatedAt).put("error", cache.error).put("busyUntil", cache.busyUntil)
            .put("routerName", cache.routerName)
            .put("profileRevision", cache.profileRevision)
        commit(preferences.edit().putString("cache:$id", json.toString()))
    }
    fun saveIfCurrent(binding: WidgetBinding, cache: WidgetCache, isCurrent: () -> Boolean): Boolean =
        synchronized(lock) {
            if (!isCurrent() || binding(binding.widgetId)?.token != binding.token) return@synchronized false
            saveCache(binding.widgetId, cache)
            true
        }
    fun claimWrite(id: Int, request: String): Boolean = synchronized(lock) {
        val attempts = preferences.getStringSet("attempts:$id", emptySet()).orEmpty()
        if (request in attempts) return@synchronized false
        commit(preferences.edit().putStringSet("attempts:$id", attempts + request))
        true
    }
    fun remove(id: Int) = synchronized(lock) {
        commit(preferences.edit().remove("binding:$id").remove("cache:$id").remove("attempts:$id"))
    }
    fun invalidate() = synchronized(lock) {
        val edit = preferences.edit()
        preferences.all.keys.filter { !it.startsWith("binding:") }.forEach(edit::remove)
        commit(edit)
    }
    private fun read(key: String): JSONObject? = try {
        preferences.getString(key, null)?.let(::JSONObject)
    } catch (_: Exception) { null }
    private fun JSONObject.stringOrNull(key: String) = if (has(key) && !isNull(key)) getString(key) else null
    private fun commit(edit: android.content.SharedPreferences.Editor) {
        if (!edit.commit()) throw ClashException("微件设置保存失败，请重试")
    }
}
