package com.clashremote.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clashremote.core.ConnectionStatus
import java.util.Locale

fun bytes(value: Long): String {
    val amount = value.coerceAtLeast(0).toDouble()
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var number = amount; var index = 0
    while (number >= 1024 && index < units.lastIndex) { number /= 1024; index++ }
    return if (index == 0) "${number.toLong()} B" else String.format(Locale.ROOT, "%.1f %s", number, units[index])
}
fun statusLabel(status: ConnectionStatus) = when (status) {
    ConnectionStatus.ONLINE -> "已连接"
    ConnectionStatus.CONNECTING -> "连接中"
    ConnectionStatus.OFFLINE -> "已离线"
    ConnectionStatus.PAUSED -> "后台暂停"
    ConnectionStatus.DISCONNECTED -> "未连接"
}
@Composable fun PageHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun EmptyPanel(title: String, description: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.Router, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (button != null) Button(onClick = onClick) { Text(button) }
    }
}
@Composable fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}
