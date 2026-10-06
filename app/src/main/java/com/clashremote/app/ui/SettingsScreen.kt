package com.clashremote.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clashremote.core.*

@Composable fun SettingsScreen(
    state: SettingsState, test: () -> Unit, save: () -> Unit,
    clear: () -> Unit, draftChanged: (RouterProfile) -> Unit,
) {
    val draft = state.draft
    var showSecret by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val enabled = state.loaded && !state.saving
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("连接路由器", "手机与路由器连接到同一个局域网") }
        item {
            OutlinedTextField(draft.name, { draftChanged(draft.copy(name = it)) }, Modifier.fillMaxWidth(), enabled = enabled,
                label = { Text("路由器名称") }, singleLine = true)
        }
        item {
            OutlinedTextField(draft.endpoint, { draftChanged(draft.copy(endpoint = it)) }, Modifier.fillMaxWidth(), enabled = enabled,
                label = { Text("Clash 控制地址") }, placeholder = { Text("http://192.168.1.1:9090") },
                supportingText = { Text("填写控制 API 地址，包含 http:// 或 https://") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
        }
        item {
            OutlinedTextField(draft.secret, { draftChanged(draft.copy(secret = it)) }, Modifier.fillMaxWidth(), enabled = enabled,
                label = { Text("控制密钥（secret）") }, singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showSecret) "隐藏密钥" else "显示密钥")
                } }, supportingText = { Text("与路由器配置的 secret 一致，在手机上加密保存") })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = test, enabled = enabled && !state.testBusy && draft.endpoint.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text(if (state.testBusy) "测试中…" else "测试连接")
                }
                Button(onClick = save, enabled = enabled && draft.endpoint.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text(if (state.saving) "保存中…" else "保存并连接")
                }
            }
            if (state.testBusy || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            state.testMessage?.let { Text(it, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        item { HorizontalDivider() }
        item {
            OutlinedTextField(draft.testUrl, { draftChanged(draft.copy(testUrl = it)) }, Modifier.fillMaxWidth(), enabled = enabled,
                label = { Text("节点测速地址") }, supportingText = { Text("由路由器通过节点访问，用于测量延迟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("路由器接入说明", style = MaterialTheme.typography.titleMedium)
                    Text("在路由器的 Clash 设置中启用外部控制接口，使其监听局域网地址，并设置 secret。常用控制端口为 9090。", style = MaterialTheme.typography.bodyMedium)
                    Text("手机需要能够访问这个端口。代理端口（如 7890）与控制端口用途不同。", style = MaterialTheme.typography.bodyMedium)
                    Text("此 App 管理路由器上的 Clash；手机流量是否经过代理取决于路由器的转发配置。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.profile != null) item {
            TextButton(onClick = { confirmClear = true }, enabled = enabled) { Text("清除本地配置", color = MaterialTheme.colorScheme.error) }
        }
        item { Text("Clash Remote 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清除路由器配置？") },
        text = { Text("手机上保存的地址和密钥将被删除。以后连接需要重新填写。") },
        confirmButton = { TextButton(onClick = { confirmClear = false; clear() }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
