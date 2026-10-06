package com.clashremote.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AppPalette(val id: String, val label: String) {
    GREEN("green", "松绿"), BLUE("blue", "海蓝"), PURPLE("purple", "鸢紫"), ORANGE("orange", "琥珀");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: GREEN }
}
interface AppearancePersistence {
    fun loadPalette(): String?
    fun savePalette(id: String)
}
data class AppearanceState(val palette: AppPalette = AppPalette.GREEN, val loaded: Boolean = false, val saving: Boolean = false, val error: String? = null)
class AppearanceController(private val scope: CoroutineScope, private val persistence: AppearancePersistence, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutable = MutableStateFlow(AppearanceState())
    val state = mutable.asStateFlow()
    init {
        scope.launch {
            try {
                val palette = withContext(io) { AppPalette.fromId(persistence.loadPalette()) }
                mutable.value = AppearanceState(palette = palette, loaded = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = AppearanceState(loaded = true, error = "无法读取配色，已使用默认配色") }
        }
    }
    fun select(palette: AppPalette) {
        if (!state.value.loaded || state.value.saving) return
        val previous = state.value.palette
        mutable.update { it.copy(palette = palette, saving = true, error = null) }
        scope.launch {
            try { withContext(io) { persistence.savePalette(palette.id) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(palette = previous, error = "配色保存失败，请重试") } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
