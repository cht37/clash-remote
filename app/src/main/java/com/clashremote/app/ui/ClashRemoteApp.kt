package com.clashremote.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashremote.app.RemoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ClashRemoteApp(viewModel: RemoteViewModel) {
    val state by viewModel.remote.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var choseInitialPage by rememberSaveable { mutableStateOf(false) }
    var handledSaveRevision by rememberSaveable { mutableLongStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.setForeground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        viewModel.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(settings.loaded) {
        if (settings.loaded && !choseInitialPage) {
            if (settings.profile == null) page = 3
            choseInitialPage = true
        }
    }
    LaunchedEffect(settings.saveRevision) {
        if (settings.saveRevision != handledSaveRevision) {
            handledSaveRevision = settings.saveRevision
            if (settings.saveRevision > 0) {
                page = 0
                snackbar.showSnackbar("路由器配置已保存")
            }
        }
    }
    val titles = listOf("概览", "代理", "连接", "设置")
    val icons = listOf(Icons.Outlined.Dashboard, Icons.Outlined.Tune, Icons.Outlined.Lan, Icons.Outlined.Settings)
    Scaffold(Modifier.imePadding(), containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("Clash Remote", style = MaterialTheme.typography.titleLarge) }, actions = {
                if (page != 3 && state.profile != null) {
                    IconButton(onClick = { viewModel.controller.refresh() }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Refresh, "刷新或重连")
                    }
                }
            })
        }, bottomBar = {
            NavigationBar {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Icon(icons[index], title) }, label = { Text(title) })
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val error = settings.error ?: state.error
            if (error != null) {
                Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(error, Modifier.weight(1f).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = viewModel::dismissError) { Icon(Icons.Outlined.Close, "关闭提示") }
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                when (page) {
                    0 -> OverviewScreen(state, viewModel.controller, { page = 3 })
                    1 -> ProxiesScreen(state, viewModel.controller, { page = 3 })
                    2 -> ConnectionsScreen(state, viewModel.controller, { page = 3 })
                    3 -> SettingsScreen(settings, viewModel::test, viewModel::save, viewModel::clear, viewModel::updateDraft,
                        appearance, viewModel::selectPalette, updates, viewModel::checkUpdates)
                }
            }
        }
    }
}
