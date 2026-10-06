package com.clashremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clashremote.app.storage.ProfileStore
import com.clashremote.core.*
class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    val controller = RemoteController(viewModelScope)
    val remote = controller.state
    private val settingsController = SettingsController(viewModelScope, ProfileStore(application), controller::connect, controller::disconnect)
    val settings = settingsController.state
    fun setForeground(value: Boolean) {
        controller.setForeground(value)
        if (!value) clearTest()
    }
    fun dismissError() { controller.clearError(); settingsController.dismissError() }
    fun clearTest() = settingsController.clearTest()
    fun updateDraft(profile: RouterProfile) = settingsController.updateDraft(profile)
    fun test() = settingsController.test()
    fun save() = settingsController.save()
    fun clear() = settingsController.clear()
    override fun onCleared() { controller.disconnect(); super.onCleared() }
}
