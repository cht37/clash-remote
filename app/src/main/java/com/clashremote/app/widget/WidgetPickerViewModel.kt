package com.clashremote.app.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clashremote.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WidgetPickerState(
    val loading: Boolean = true, val loaded: Boolean = false, val busy: Boolean = false,
    val error: String? = null, val query: String = "", val routerName: String = "",
    val groupName: String = "", val choices: List<String> = emptyList(), val current: String = "",
    val completed: Boolean = false, val needsSetup: Boolean = false,
)

class WidgetPickerViewModel(
    val configuring: Boolean,
    private val binding: WidgetBinding?,
    private val loadControls: suspend () -> WidgetControls,
    private val currentTicket: () -> WidgetTicket?,
    private val selectNode: suspend (WidgetTicket, WidgetCommand.Select) -> ControlSnapshot?,
    private val bindGroup: suspend (String, WidgetControls) -> Unit,
) : ViewModel() {
    private val mutable = MutableStateFlow(WidgetPickerState())
    val state = mutable.asStateFlow()
    private var controls: WidgetControls? = null
    private var job: Job? = null
    init { reload() }
    fun updateQuery(value: String) { mutable.update { it.copy(query = value) } }
    fun reload() {
        if (state.value.busy) return
        job?.cancel()
        mutable.update { it.copy(loading = true, loaded = false, error = null, needsSetup = false) }
        job = viewModelScope.launch {
            try {
                val data = loadControls()
                controls = data
                if (configuring) {
                    mutable.update { it.copy(loading = false, loaded = true, routerName = data.routerName,
                        choices = data.snapshot.proxies.filterValues { group -> group.type == "Selector" }.keys.toList(),
                        current = binding?.group.orEmpty()) }
                } else {
                    val ticket = currentTicket()
                    if (ticket == null || ticket.bindingToken != binding?.token || ticket.profileRevision != data.profileRevision)
                        throw ClashException("配置已变化，请刷新后重试")
                    val group = data.snapshot.proxies[binding.group]
                    if (group?.type != "Selector") throw ClashException("策略组不可用，请重新选择")
                    mutable.update { it.copy(loading = false, loaded = true, routerName = data.routerName,
                        groupName = binding.group, choices = group.all.orEmpty(), current = group.now) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = readable(e)
                mutable.update { it.copy(loading = false, loaded = false, error = message,
                    needsSetup = message.contains("配置路由器") || message.contains("重新填写")) }
            }
        }
    }
    fun choose(name: String) {
        val before = state.value
        val data = controls ?: return
        if (!before.loaded || before.loading || before.busy || before.completed || name !in before.choices) return
        mutable.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            try {
                if (configuring) {
                    bindGroup(name, data)
                } else {
                    val ticket = currentTicket()
                    if (ticket == null || ticket.bindingToken != binding?.token || ticket.profileRevision != data.profileRevision)
                        throw ClashException("配置已变化，请刷新后重试")
                    val confirmed = selectNode(ticket, WidgetCommand.Select(before.groupName, name))
                        ?: throw ClashException("配置已变化，请刷新后重试")
                    mutable.update { it.copy(current = confirmed.proxies[before.groupName]?.now.orEmpty()) }
                }
                mutable.update { it.copy(completed = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = readable(e)) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun readable(e: Exception) = if (e is ClashException) e.message ?: "操作失败，请重试" else "操作失败，请重试"
}
