package com.clashremote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clashremote.core.*

@Composable fun OverviewScreen(state: RemoteState, controller: RemoteController, settings: () -> Unit) {
    val profile = state.profile
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageHeading("路由器代理", "在手机上管理你的局域网代理") }
        if (profile == null) {
            item { EmptyPanel("连接你的路由器", "填写 Clash 控制地址和密钥，即可开始管理。", "添加路由器", settings) }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Outlined.Router, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(statusLabel(state.status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            if (state.status == ConnectionStatus.CONNECTING) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        Text(profile.endpoint, style = MaterialTheme.typography.bodyMedium)
                        if (state.version.isNotEmpty()) Text("内核版本 ${state.version}", style = MaterialTheme.typography.bodySmall)
                        if (state.status == ConnectionStatus.OFFLINE) {
                            Button(onClick = controller::refresh) { Text("重新连接") }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric("↓ 下载速率", "${bytes(state.traffic.down)}/s", Modifier.weight(1f))
                        Metric("↑ 上传速率", "${bytes(state.traffic.up)}/s", Modifier.weight(1f))
                    }
                    TrafficChart(state.trafficHistory)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("↓ 下载", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("↑ 上传（虚线）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric("累计下载", bytes(state.snapshot.downloadTotal), Modifier.weight(1f))
                        Metric("累计上传", bytes(state.snapshot.uploadTotal), Modifier.weight(1f))
                    }
                    Text("${state.snapshot.connections.size} 个活跃连接", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                HorizontalDivider()
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("代理模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("rule" to "规则", "global" to "全局", "direct" to "直连").forEach { (mode, label) ->
                            FilterChip(selected = state.mode.equals(mode, true), onClick = { controller.changeMode(mode) },
                                enabled = state.status == ConnectionStatus.ONLINE && !state.busy, label = { Text(label) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Text(when (state.mode.lowercase()) {
                        "global" -> "路由器 Clash 通过全局策略组转发流量。"
                        "direct" -> "路由器 Clash 直接连接目标地址。"
                        else -> "路由器 Clash 按规则为每个请求选择代理。"
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun TrafficChart(history: List<Traffic>) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(88.dp)) {
        for (i in 0..2) drawLine(grid, Offset(0f, size.height * i / 2), Offset(size.width, size.height * i / 2), 1f)
        if (history.size >= 2) {
            val maximum = history.maxOf { maxOf(it.up, it.down) }.coerceAtLeast(1).toFloat()
            listOf(false, true).forEach { upload ->
                val path = Path()
                history.forEachIndexed { index, sample ->
                    val x = size.width * index / (history.size - 1)
                    val y = size.height - size.height * (if (upload) sample.up else sample.down).coerceAtLeast(0) / maximum
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, if (upload) upColor else downColor, style = Stroke(2.dp.toPx(),
                    pathEffect = if (upload) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null))
            }
        }
    }
}
