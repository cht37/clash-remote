package com.clashremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.clashremote.app.BuildConfig
import com.clashremote.core.*

@Composable fun AppearanceSection(state: AppearanceState, select: (AppPalette) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("应用配色", style = MaterialTheme.typography.titleMedium)
        Text("选择后立即生效，深浅模式跟随系统。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppPalette.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { palette ->
                    FilterChip(selected = state.palette == palette, onClick = { select(palette) },
                        enabled = state.loaded && !state.saving, modifier = Modifier.weight(1f),
                        leadingIcon = { Box(Modifier.size(18.dp).background(paletteAccent(palette), CircleShape)) },
                        label = { Text(palette.label) })
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable fun UpdatesSection(state: UpdateState, check: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        try { uriHandler.openUri(url); linkError = null }
        catch (_: Exception) { linkError = "无法打开浏览器，请检查手机的浏览器设置" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = check, enabled = !state.checking) { Text(if (state.checking) "检查中…" else "检查更新") }
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (state.status) {
            UpdateStatus.NOT_CHECKED -> Text("从 GitHub 获取最新正式发行版。", style = MaterialTheme.typography.bodyMedium)
            UpdateStatus.NO_RELEASE -> Text("GitHub 暂无正式发行版。", style = MaterialTheme.typography.bodyMedium)
            UpdateStatus.UP_TO_DATE -> Text("已是最新版本。", color = MaterialTheme.colorScheme.primary)
            UpdateStatus.AVAILABLE -> Text("发现新版本 ${state.release?.tag.orEmpty()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val release = state.release
        if (release != null && state.status == UpdateStatus.AVAILABLE) {
            if (release.notes.isNotBlank()) Text(release.notes.take(2000), style = MaterialTheme.typography.bodyMedium)
            val apk = release.apkUrl
            if (apk != null) {
                Button(onClick = { open(apk) }) { Text("下载新版 APK${if (release.apkSize > 0) "（${bytes(release.apkSize)}）" else ""}") }
                Text("浏览器下载后，通过安卓系统确认安装。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text("本次发行版没有 APK 附件，可前往发行页面查看。", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { open(release?.pageUrl ?: RELEASES_PAGE) }) { Text("查看 GitHub 发行页面") }
        linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
