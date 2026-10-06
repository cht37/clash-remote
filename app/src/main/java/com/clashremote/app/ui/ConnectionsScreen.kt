package com.clashremote.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clashremote.core.*

@Composable fun ConnectionsScreen(state: RemoteState, controller: RemoteController, settings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val online = state.status == ConnectionStatus.ONLINE && !state.busy
    val connections = state.snapshot.connections.filter {
        query.isBlank() || listOf(it.destination, it.metadata.sourceIP, it.metadata.destinationIP, it.chains.joinToString(" "))
            .any { value -> value.contains(query, true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("活跃连接", "查看路由器流量的去向和代理链路") }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索域名、IP 或代理") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${connections.size} 个连接", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { confirmCloseAll = true }, enabled = online && state.snapshot.connections.isNotEmpty()) { Text("关闭全部") }
            }
            if (state.profile != null && state.status != ConnectionStatus.ONLINE) {
                Text("${statusLabel(state.status)}，以下为上次获取的连接。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.profile == null) {
            item { EmptyPanel("先连接路由器", "连接后可以查看代理流量和活跃连接。", "前往设置", settings) }
        } else if (connections.isEmpty()) {
            item { EmptyPanel(if (query.isBlank()) "当前没有活跃连接" else "没有匹配的连接",
                if (query.isBlank()) "路由器产生新的代理流量后，连接会显示在这里。" else "尝试其他域名、IP 或代理名称。") }
        }
        items(connections, key = { it.id }) { connection ->
            Card {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 16.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(connection.destination, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { controller.closeConnection(connection.id) }, enabled = online) { Icon(Icons.Outlined.Close, "关闭 ${connection.destination}") }
                    }
                    Text("${connection.metadata.network.uppercase()}  ${connection.metadata.sourceIP}:${connection.metadata.sourcePort}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(connection.chains.asReversed().joinToString(" → ").ifEmpty { "未提供代理链路" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text("规则：${connection.rule} ${connection.rulePayload}", style = MaterialTheme.typography.bodySmall)
                    Text("↓ ${bytes(connection.download)}     ↑ ${bytes(connection.upload)}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (confirmCloseAll) AlertDialog(onDismissRequest = { confirmCloseAll = false },
        title = { Text("关闭所有代理连接？") }, text = { Text("路由器上的现有连接会中断，正在使用的应用可能重新建立连接。") },
        confirmButton = { TextButton(onClick = { confirmCloseAll = false; controller.closeAllConnections() }, enabled = online) { Text("关闭全部") } },
        dismissButton = { TextButton(onClick = { confirmCloseAll = false }) { Text("取消") } })
}
