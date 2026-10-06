package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface ProfilePersistence {
    fun load(): RouterProfile?
    fun save(profile: RouterProfile)
    fun clear()
}

data class SettingsState(
    val profile: RouterProfile? = null, val draft: RouterProfile = RouterProfile(),
    val error: String? = null, val loaded: Boolean = false,
    val testBusy: Boolean = false, val testMessage: String? = null, val saving: Boolean = false,
)

class SettingsController(
    private val scope: CoroutineScope,
    private val persistence: ProfilePersistence,
    private val onSaved: (RouterProfile) -> Unit,
    private val onCleared: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val factory: (RouterProfile) -> ClashApi = { OkHttpClashApi(it) },
) {
    private val mutable = MutableStateFlow(SettingsState())
    val state = mutable.asStateFlow()
    private var testJob: Job? = null
    private var testGeneration = 0L
    init {
        scope.launch {
            try {
                val profile = withContext(io) { persistence.load() }
                mutable.update { it.copy(profile = profile, draft = profile ?: RouterProfile(), loaded = true) }
                profile?.let(onSaved)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loaded = true, error = readable(e)) } }
        }
    }
    fun dismissError() { mutable.update { it.copy(error = null) } }
    fun clearTest() {
        testGeneration++
        testJob?.cancel(); testJob = null
        mutable.update { it.copy(testBusy = false, testMessage = null) }
    }
    fun updateDraft(draft: RouterProfile) {
        if (!state.value.loaded || state.value.saving) return
        clearTest()
        mutable.update { it.copy(draft = draft) }
    }
    fun test() {
        if (!state.value.loaded || state.value.saving) return
        clearTest()
        val draft = state.value.draft
        val generation = testGeneration
        mutable.update { it.copy(testBusy = true, error = null) }
        testJob = scope.launch {
            var client: ClashApi? = null
            try {
                client = factory(draft.validated())
                val version = client.version()
                client.config()
                if (generation == testGeneration) mutable.update { it.copy(testMessage = "连接成功，内核版本 $version") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == testGeneration) mutable.update { it.copy(testMessage = readable(e)) }
            } finally {
                client?.close()
                if (generation == testGeneration) mutable.update { it.copy(testBusy = false) }
            }
        }
    }
    fun save() {
        if (!state.value.loaded || state.value.saving) return
        clearTest()
        val draft = state.value.draft
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                val profile = draft.validated()
                withContext(io) { persistence.save(profile) }
                mutable.update { it.copy(profile = profile, draft = profile) }
                onSaved(profile)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = readable(e)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
    fun clear() {
        if (!state.value.loaded || state.value.saving) return
        clearTest()
        mutable.update { it.copy(saving = true) }
        scope.launch {
            try {
                withContext(io) { persistence.clear() }
                onCleared()
                mutable.value = SettingsState(loaded = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = readable(e)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
    private fun readable(e: Exception) = if (e is ClashException) e.message ?: "操作失败，请重试" else "无法读取或保存配置，请重新填写后重试"
}
