package com.hondash.android.ui.layout

import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.ui.tiles.TileStyle
import org.junit.Assert.*
import org.junit.Test

class LayoutRepositoryTest {
    @Test fun firstRunSavesDefaultLayoutOnlyOnce() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        assertEquals(DashboardLayout(), repo.load())
        assertNotNull(prefs.values["layout_v2"])
        assertEquals(DashboardLayout(), repo.load())
        assertEquals(listOf("layout_v2"), prefs.writes)
        assertFalse(prefs.values.containsKey("tiles_v1"))
    }

    @Test fun migrationKeepsEveryMvpFieldAndNeverWritesTheLegacyKey() {
        val prefs = MemoryPreferences()
        val fixture = javaClass.getResource("/tiles_v1.json")!!.readText()
        prefs.values["tiles_v1"] = fixture
        val repo = LayoutRepository(prefs.preferences)
        val migrated = repo.load()
        assertEquals(LayoutCodec.decode(fixture).layout, migrated)
        assertEquals(migrated, LayoutCodec.decode(prefs.values.getValue("layout_v2")).layout)
        assertEquals(fixture, prefs.values["tiles_v1"])
        assertEquals(listOf("layout_v2"), prefs.writes)
        repo.save(migrated.copy(tiles = migrated.tiles.drop(1), theme = ThemeMode.DAY))
        assertEquals(migrated.tiles.drop(1), repo.load().tiles)
        assertEquals(ThemeMode.DAY, repo.load().theme)
        assertEquals(fixture, prefs.values["tiles_v1"])
        assertFalse(prefs.writes.contains("tiles_v1"))
    }

    @Test fun validV2WinsOverLegacyAndPreservesAllNewFields() {
        val prefs = MemoryPreferences()
        prefs.values["tiles_v1"] = "not JSON"
        val layout = DashboardLayout(
            defaultGrid = GridPreset.G3X3, pageGrids = listOf(GridPreset.G3X2),
            tiles = listOf(TileSpec.DEFAULTS.first().copy(style = TileStyle.SWEEP, gaugeMin = 0f, gaugeMax = 9000f, alarmSilenced = true)),
            shiftLight = ShiftLightConfig(true, 6000, 8500), theme = ThemeMode.NIGHT,
        )
        prefs.values["layout_v2"] = LayoutCodec.encode(layout)
        assertEquals(layout, LayoutRepository(prefs.preferences).load())
        assertTrue(prefs.writes.isEmpty())
    }

    @Test fun corruptV2FallsBackToLegacyFixtureAndRepairsOnlyV2() {
        val prefs = MemoryPreferences()
        val fixture = javaClass.getResource("/tiles_v1.json")!!.readText()
        prefs.values["tiles_v1"] = fixture
        prefs.values["layout_v2"] = "{broken"
        val migrated = LayoutRepository(prefs.preferences).load()
        assertEquals(LayoutCodec.decode(fixture).layout, migrated)
        assertEquals(migrated, LayoutCodec.decode(prefs.values.getValue("layout_v2")).layout)
        assertEquals(fixture, prefs.values["tiles_v1"])
        assertEquals(listOf("layout_v2"), prefs.writes)
    }

    @Test fun corruptOrMissingLegacyUsesMvpDefaults() {
        listOf("{broken", "", null).forEach { legacy ->
            val prefs = MemoryPreferences()
            prefs.values["layout_v2"] = "{broken"
            legacy?.let { prefs.values["tiles_v1"] = it }
            assertEquals(DashboardLayout(), LayoutRepository(prefs.preferences).load())
            assertEquals(legacy, prefs.values["tiles_v1"])
        }
    }

    @Test fun removingAllTilesPersistsAcrossRestart() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        repo.save(DashboardLayout(tiles = emptyList()))
        assertTrue(LayoutRepository(prefs.preferences).load().tiles.isEmpty())
    }

    @Test fun emptyLegacyArrayMigratesWithoutInventingTiles() {
        val prefs = MemoryPreferences()
        prefs.values["tiles_v1"] = "[]"
        assertTrue(LayoutRepository(prefs.preferences).load().tiles.isEmpty())
        assertEquals("[]", prefs.values["tiles_v1"])
    }

    @Test fun alertsAreDeviceLocalAndUnaffectedByLayoutReplacement() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        assertEquals(AlertPreferences(), repo.loadAlertPreferences())
        val alerts = AlertPreferences(sound = false, vibration = false, demoAlerts = true)
        repo.saveAlertPreferences(alerts)
        val alertJson = prefs.values["alerts_v1"]
        repo.save(DashboardLayout(theme = ThemeMode.DAY))
        assertEquals(alerts, LayoutRepository(prefs.preferences).loadAlertPreferences())
        assertEquals(alertJson, prefs.values["alerts_v1"])
        assertFalse(prefs.values.getValue("layout_v2").contains("demoAlerts"))
        assertFalse(prefs.writes.contains("tiles_v1"))
    }

    @Test fun corruptAlertPrefsUseDefaultsAndMissingFieldsDefaultIndividually() {
        val prefs = MemoryPreferences()
        val repo = LayoutRepository(prefs.preferences)
        prefs.values["alerts_v1"] = "{broken"
        assertEquals(AlertPreferences(), repo.loadAlertPreferences())
        prefs.values["alerts_v1"] = """{"sound":false,"vibration":"invalid"}"""
        assertEquals(AlertPreferences(sound = false), repo.loadAlertPreferences())
    }
}
