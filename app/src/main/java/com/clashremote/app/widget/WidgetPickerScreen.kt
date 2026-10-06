package com.clashremote.app.widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WidgetPickerScreen(
    viewModel: WidgetPickerViewModel,
    onClose: () -> Unit,
    onReconfigure: () -> Unit = {},
    onSetup: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onClose() }
    val choices = state.choices.filter { it.contains(state.query, ignoreCase = true) }
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).imePadding().navigationBarsPadding()
        .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (viewModel.configuring) "选择策略组" else "选择节点", Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭") }
        }
        Text(if (viewModel.configuring) state.routerName.ifBlank { "为此微件选择策略组" }
            else listOf(state.groupName, state.routerName).filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        state.error?.let { error ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (!state.loaded) TextButton(onClick = if (state.needsSetup) onSetup else viewModel::reload) {
                        Text(if (state.needsSetup) "打开设置" else "重新加载")
                    }
                }
            }
        }
        if (state.loading) {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("正在读取路由器…", Modifier.padding(start = 12.dp))
            }
        } else if (state.loaded) {
            OutlinedTextField(state.query, viewModel::updateQuery, Modifier.fillMaxWidth().testTag("widget-search"),
                placeholder = { Text(if (viewModel.configuring) "搜索策略组" else "搜索节点") }, singleLine = true,
                enabled = !state.busy, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = MaterialTheme.shapes.large)
            if (choices.isEmpty()) {
                Text(if (state.choices.isEmpty()) {
                    if (viewModel.configuring) "未找到可手选的策略组" else "此策略组暂无节点"
                } else "没有匹配的结果", Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(choices, key = { index, name -> "$index:$name" }) { _, name ->
                        val selected = state.current == name
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                            Row(Modifier.fillMaxWidth().selectable(selected, enabled = !state.busy,
                                role = Role.RadioButton, onClick = { viewModel.choose(name) })
                                .semantics(mergeDescendants = true) {}.heightIn(min = 56.dp).padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected, onClick = null, enabled = !state.busy)
                                Text(name, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (selected) Text("当前", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (viewModel.configuring) "每个微件可绑定不同策略组" else "点击节点即可切换", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!viewModel.configuring) TextButton(onClick = onReconfigure, enabled = !state.busy) { Text("更换策略组") }
        }
    }
}
