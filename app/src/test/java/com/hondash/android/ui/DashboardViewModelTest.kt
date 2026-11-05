package com.hondash.android.ui

import com.hondash.android.ui.layout.AlertPreferences
import com.hondash.android.ui.layout.DashboardLayout
import com.hondash.android.ui.layout.GridPreset
import com.hondash.android.ui.layout.LayoutCodec
import com.hondash.android.ui.layout.LayoutRepository
import com.hondash.android.ui.layout.MemoryPreferences
import com.hondash.android.ui.layout.ShiftLightConfig
import com.hondash.android.ui.layout.ThemeMode
import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.ui.tiles.TileStyle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DashboardViewModelTest {
    private fun viewModel(repo: LayoutRepository) = DashboardViewModel(repo) {
        error("Layout operations must not access Android USB services")
    }

    private fun configuredLayout() = DashboardLayout(
        defaultGrid = GridPreset.G3X3,
        pageGrids = listOf(GridPreset.G3X2, GridPreset.G2X2),
        tiles = TileSpec.DEFAULTS.take(2),
        shiftLight = ShiftLightConfig(true, 6500, 8500),
        theme = ThemeMode.NIGHT,
    )

    @Test fun tileCrudPreservesLayoutSettingsAndDeviceAlertsAndPersistsEachChange() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        var expected = configuredLayout()
        val alerts = AlertPreferences(sound = false, vibration = false, demoAlerts = true)
        repo.save(expected)
        repo.saveAlertPreferences(alerts)
        val alertJson = prefs.values["alerts_v1"]
        prefs.writes.clear()
        val vm = viewModel(repo)
        assertEquals(expected, vm.layout.value)
        assertEquals(alerts, vm.alertPreferences.value)

        fun assertSavedState() {
            assertEquals(expected, vm.layout.value)
            assertEquals(expected, LayoutRepository(prefs.preferences).load())
            assertEquals(alerts, vm.alertPreferences.value)
            assertEquals(alerts, repo.loadAlertPreferences())
            assertEquals(alertJson, prefs.values["alerts_v1"])
        }

        val added = TileSpec.DEFAULTS.last().copy(label = "Added tile")
        vm.addTile(added)
        expected = expected.copy(tiles = expected.tiles + added)
        assertSavedState()

        val edited = expected.tiles[1].copy(label = "Edited tile", style = TileStyle.SWEEP, alarmSilenced = true)
        vm.updateTile(1, edited)
        expected = expected.copy(tiles = expected.tiles.toMutableList().apply { this[1] = edited })
        assertSavedState()

        vm.removeTile(0)
        expected = expected.copy(tiles = expected.tiles.drop(1))
        assertSavedState()
        assertEquals(List(3) { "layout_v2" }, prefs.writes)
    }

    @Test fun addingTile200SucceedsButTile201ChangesNeitherStateNorStorage() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        val initial = configuredLayout().copy(tiles = List(LayoutCodec.MAX_TILES - 1) { TileSpec.DEFAULTS.first() })
        repo.save(initial)
        val vm = viewModel(repo)
        val last = TileSpec.DEFAULTS.last()
        vm.addTile(last)
        val atLimit = initial.copy(tiles = initial.tiles + last)
        assertEquals(atLimit, vm.layout.value)
        assertEquals(atLimit, LayoutRepository(prefs.preferences).load())
        val published = vm.layout.value
        val stored = prefs.values.toMap()
        prefs.writes.clear()

        vm.addTile(TileSpec.DEFAULTS.first())
        assertSame(published, vm.layout.value)
        assertEquals(stored, prefs.values)
        assertTrue(prefs.writes.isEmpty())
        assertEquals("Layouts may contain at most ${LayoutCodec.MAX_TILES} tiles", vm.error.value)
    }

    @Test fun invalidTileIndicesDoNotChangeStateOrStorage() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        repo.save(configuredLayout())
        val vm = viewModel(repo)
        val published = vm.layout.value
        val stored = prefs.values.toMap()
        prefs.writes.clear()
        listOf(-1, published.tiles.size).forEach { index ->
            vm.updateTile(index, TileSpec.DEFAULTS.last())
            vm.removeTile(index)
        }
        assertSame(published, vm.layout.value)
        assertEquals(stored, prefs.values)
        assertTrue(prefs.writes.isEmpty())
    }

    @Test fun newerLayoutReportsUpdateErrorAndCannotBeOverwrittenByTileCrud() {
        val prefs = MemoryPreferences()
        val newer = JSONObject(LayoutCodec.encode(configuredLayout()))
            .put("version", LayoutCodec.VERSION + 1).put("futureSetting", "keep me").toString()
        prefs.values["layout_v2"] = newer
        prefs.values["tiles_v1"] = "[]"
        val stored = prefs.values.toMap()
        repeat(2) {
            val vm = viewModel(LayoutRepository(prefs.preferences))
            assertEquals(LayoutCodec.NEWER_VERSION_MESSAGE, vm.error.value)
            val published = vm.layout.value
            vm.addTile(TileSpec.DEFAULTS.last())
            vm.updateTile(0, TileSpec.DEFAULTS.last())
            vm.removeTile(0)
            vm.updateLayout(configuredLayout())
            assertSame(published, vm.layout.value)
            assertEquals(LayoutCodec.NEWER_VERSION_MESSAGE, vm.error.value)
            assertEquals(stored, prefs.values)
            assertTrue(prefs.writes.isEmpty())
        }
    }

    @Test fun oversizedTileEditReportsErrorWithoutChangingStateOrStorage() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        repo.save(configuredLayout())
        val vm = viewModel(repo)
        val published = vm.layout.value
        val stored = prefs.values.toMap()
        prefs.writes.clear()
        vm.updateTile(0, published.tiles.first().copy(label = "x".repeat(LayoutCodec.MAX_BYTES)))
        assertSame(published, vm.layout.value)
        assertEquals(stored, prefs.values)
        assertTrue(prefs.writes.isEmpty())
        assertEquals("Layout exceeds 256 KB", vm.error.value)
    }
}
