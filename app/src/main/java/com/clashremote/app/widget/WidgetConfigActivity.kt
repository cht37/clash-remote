package com.clashremote.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clashremote.app.MainActivity
import com.clashremote.app.storage.AppearanceStore
import com.clashremote.app.ui.ClashRemoteTheme
import com.clashremote.core.AppPalette
import com.clashremote.core.ClashException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetConfigActivity : WidgetPickerActivity() {
    override val configuring = true
}

abstract class WidgetPickerActivity : ComponentActivity() {
    protected abstract val configuring: Boolean
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (configuring) setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        fun owned() = WidgetCoordinator.owns(this, id)
        val store = WidgetStore(this)
        val binding = store.binding(id)
        val expectedToken = intent.getStringExtra("bindingToken")
        val expectedRevision = intent.getStringExtra("profileRevision")
        if (!owned() || (expectedToken != null && expectedToken != "unbound" && expectedToken != binding?.token) ||
            (expectedRevision != null && expectedRevision != WidgetCoordinator.profileRevision(this))) {
            WidgetRenderer.render(this, id); finish(); return
        }
        if (!configuring && binding == null) {
            startActivity(Intent(this, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish(); return
        }
        val runtime = WidgetRuntime.from(this)
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WidgetPickerViewModel(configuring, binding,
                loadControls = { withContext(Dispatchers.IO) { runtime.loadControls() } },
                currentTicket = { runtime.ticket(id) },
                selectNode = { ticket, command -> WidgetCoordinator.submitNode(applicationContext, ticket, command) },
                bindGroup = { group, controls -> withContext(Dispatchers.IO) {
                    if (!owned() || WidgetCoordinator.profileRevision(this@WidgetPickerActivity) != controls.profileRevision)
                        throw ClashException("配置已变化，请刷新后重试")
                    val proxy = controls.snapshot.proxies[group]
                    if (proxy?.type != "Selector") throw ClashException("策略组不可用，请重新选择")
                    val selected = store.bind(id, group)
                    store.saveIfCurrent(selected, WidgetCache(controls.snapshot.mode, proxy.now,
                        System.currentTimeMillis(), routerName = controls.routerName, profileRevision = controls.profileRevision)) {
                        owned() && WidgetCoordinator.profileRevision(this@WidgetPickerActivity) == controls.profileRevision
                    }
                    WidgetRenderer.render(this@WidgetPickerActivity, id)
                    runtime.ticket(id)?.let { WidgetCoordinator.enqueue(this@WidgetPickerActivity, it, com.clashremote.core.WidgetCommand.Refresh) }
                } }) as T
        })[WidgetPickerViewModel::class.java]
        fun close() {
            if (configuring && vm.state.value.busy) return
            if (configuring && vm.state.value.completed) setResult(RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        enableEdgeToEdge()
        val palette = AppPalette.fromId(AppearanceStore(this).loadPalette())
        setContent {
            ClashRemoteTheme(palette) {
                ModalBottomSheet(onDismissRequest = ::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface) {
                    WidgetPickerScreen(vm, onClose = ::close, onReconfigure = {
                        startActivity(Intent(this@WidgetPickerActivity, WidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }, onSetup = {
                        startActivity(Intent(this@WidgetPickerActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_settings", true))
                        finish()
                    })
                }
            }
        }
    }
}
