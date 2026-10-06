package com.clashremote.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceControllerTest {
    private class Store(var value: String? = null) : AppearancePersistence {
        var fail = false
        override fun loadPalette() = value
        override fun savePalette(id: String) { if (fail) throw ClashException("保存失败"); value = id }
    }
    @Test fun selectionAppliesImmediatelyAndSurvivesReload() = runTest {
        val store = Store()
        val controller = AppearanceController(backgroundScope, store, StandardTestDispatcher(testScheduler))
        runCurrent()
        controller.select(AppPalette.PURPLE)
        assertEquals(AppPalette.PURPLE, controller.state.value.palette)
        runCurrent()
        assertEquals("purple", store.value)
        val restored = AppearanceController(backgroundScope, store, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(AppPalette.PURPLE, restored.state.value.palette)
    }
    @Test fun failedSaveRestoresPreviousPaletteAndReportsError() = runTest {
        val store = Store("blue").apply { fail = true }
        val controller = AppearanceController(backgroundScope, store, StandardTestDispatcher(testScheduler))
        runCurrent(); controller.select(AppPalette.ORANGE); runCurrent()
        assertEquals(AppPalette.BLUE, controller.state.value.palette)
        assertNotNull(controller.state.value.error)
    }
    @Test fun unknownSavedPaletteFallsBackToGreen() = runTest {
        val controller = AppearanceController(backgroundScope, Store("future-value"), StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(AppPalette.GREEN, controller.state.value.palette)
        assertTrue(controller.state.value.loaded)
    }
}
