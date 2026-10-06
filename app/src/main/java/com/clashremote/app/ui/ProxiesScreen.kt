package com.clashremote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clashremote.core.*

@Composable fun ProxiesScreen(state: RemoteState, controller: RemoteController, settings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember(state.profile?.endpoint) { mutableStateOf(emptySet<String>()) }
    val groups = state.proxies.values.filter { it.all != null && !it.hidden }
    val online = state.status == ConnectionStatus.ONLINE
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("代理策略", "选择出口节点，或检查节点延迟") }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索策略组或节点") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
        }
        if (state.profile == null) {
            item { EmptyPanel("先连接路由器", "节点列表来自路由器的 Clash 配置。", "前往设置", settings) }
        } else if (groups.isEmpty()) {
            item { EmptyPanel("暂无策略组", if (online) "当前 Clash 配置没有代理策略组。" else "连接路由器后查看策略组。", "刷新或重连", controller::refresh) }
        } else {
            if (!online) item { Text("${statusLabel(state.status)}，以下为上次获取的节点状态。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val visibleGroups = groups.filter { group ->
                query.isBlank() || group.name.contains(query, true) || group.all.orEmpty().any { it.contains(query, true) }
            }
            if (visibleGroups.isEmpty()) item { Text("没有匹配的节点") }
            visibleGroups.forEach { group ->
                val open = query.isNotBlank() || group.name in expanded || groups.size == 1
                item(key = "group:${group.name}") {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { expanded = if (group.name in expanded) expanded - group.name else expanded + group.name }
                                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(group.now.ifEmpty { group.type }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${group.type} · ${group.all.orEmpty().size} 个节点", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "收起策略组" else "展开策略组")
                            }
                            if (open) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { controller.testGroup(group.all.orEmpty()) },
                                        enabled = online && group.all.orEmpty().none { it in state.testing }) { Text("测整组延迟") }
                                }
                            }
                        }
                    }
                }
                if (open) {
                    group.all.orEmpty().filter { query.isBlank() || group.name.contains(query, true) || it.contains(query, true) }
                        .forEachIndexed { index, name ->
                            item(key = "node:${group.name}:$index:$name") {
                                val selected = group.now == name
                                val recorded = state.delays[name] ?: state.proxies[name]?.history?.lastOrNull()?.delay
                                Surface(shape = MaterialTheme.shapes.medium,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                                    val selection = if (group.type == "Selector") Modifier.selectable(
                                        selected = selected, enabled = online && !state.busy, role = Role.RadioButton,
                                        onClick = { controller.select(group.name, name) },
                                    ) else Modifier
                                    Row(Modifier.fillMaxWidth().then(selection).semantics(mergeDescendants = true) {}
                                        .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (group.type == "Selector") {
                                            RadioButton(selected, onClick = null, enabled = online && !state.busy)
                                        } else Icon(Icons.Outlined.Route, null, Modifier.padding(end = 12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                                            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            Text(state.proxies[name]?.type.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (name in state.testing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        else Text(when { recorded == null -> "未测试"; recorded <= 0 -> "不可达"; else -> "$recorded ms" },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (recorded != null && recorded <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                        IconButton(onClick = { controller.testNode(name) }, enabled = online && name !in state.testing) {
                                            Icon(Icons.Outlined.Speed, "测试 $name 延迟")
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}
