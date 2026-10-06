package com.clashremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clashremote.app.storage.ProfileStore
import com.clashremote.app.storage.AppearanceStore
import com.clashremote.core.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import com.clashremote.app.widget.WidgetCoordinator

class RemoteViewModel @JvmOverloads constructor(
    application: Application,
    persistence: ProfilePersistence = ProfileStore(application),
    routerFactory: (RouterProfile) -> ClashApi = { OkHttpClashApi(it) },
    palettePersistence: AppearancePersistence = AppearanceStore(application),
    releaseSource: ReleaseSource = GitHubReleaseClient(BuildConfig.VERSION_NAME),
    io: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {
    val controller = RemoteController(viewModelScope, routerFactory)
    val remote = controller.state
    private val settingsController = SettingsController(viewModelScope, persistence, controller::connect, controller::disconnect, io, routerFactory)
    val settings = settingsController.state
    private val appearanceController = AppearanceController(viewModelScope, palettePersistence, io)
    val appearance = appearanceController.state
    private val updateController = UpdateController(viewModelScope, BuildConfig.VERSION_NAME, releaseSource)
    val updates = updateController.state
    init {
        viewModelScope.launch {
            remote.filter { it.status == ConnectionStatus.ONLINE && !it.busy }
                .map { it.mode to it.proxies }.distinctUntilChanged()
                .collect { WidgetCoordinator.refreshAll(application) }
        }
    }
    fun setForeground(value: Boolean) {
        controller.setForeground(value)
        if (!value) { clearTest(); updateController.cancel() }
    }
    fun dismissError() { controller.clearError(); settingsController.dismissError() }
    fun clearTest() = settingsController.clearTest()
    fun updateDraft(profile: RouterProfile) = settingsController.updateDraft(profile)
    fun test() = settingsController.test()
    fun save() = settingsController.save()
    fun clear() = settingsController.clear()
    fun selectPalette(palette: AppPalette) = appearanceController.select(palette)
    fun checkUpdates() = updateController.check()
    override fun onCleared() { controller.disconnect(); updateController.close(); super.onCleared() }
}
