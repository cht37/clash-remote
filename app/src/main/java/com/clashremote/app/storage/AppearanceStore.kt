package com.clashremote.app.storage

import android.content.Context
import com.clashremote.core.*
import com.clashremote.app.widget.WidgetCoordinator

class AppearanceStore(private val context: Context) : AppearancePersistence {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    override fun loadPalette(): String? = preferences.getString("palette", null)
    override fun savePalette(id: String) {
        if (!preferences.edit().putString("palette", id).commit()) throw ClashException("配色保存失败，请重试")
        runCatching { WidgetCoordinator.appearanceChanged(context) }
    }
}
