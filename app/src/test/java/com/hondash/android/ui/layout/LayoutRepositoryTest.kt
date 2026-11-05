package com.hondash.android.ui.layout

import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.ui.tiles.TileStyle
import org.json.JSONObject
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

    @Test fun newerStoredVersionSurvivesLoadsAndSaveAttemptsEvenBeforeLoading() {
        val fixture = javaClass.getResource("/tiles_v1.json")!!.readText()
        val newer = JSONObject(LayoutCodec.encode(DashboardLayout(theme = ThemeMode.NIGHT)))
            .put("version", LayoutCodec.VERSION + 1).put("futureSetting", "keep me").toString()
        listOf(fixture, null).forEach { legacy ->
            val prefs = MemoryPreferences()
            prefs.values["layout_v2"] = newer
            legacy?.let { prefs.values["tiles_v1"] = it }
            val original = prefs.values.toMap()
            val repo = LayoutRepository(prefs.preferences)
            assertThrows(NewerLayoutVersionException::class.java) { repo.save(DashboardLayout()) }
            repeat(2) {
                val error = assertThrows(NewerLayoutVersionException::class.java) { repo.load() }
                assertEquals(LayoutCodec.NEWER_VERSION_MESSAGE, error.message)
                assertThrows(NewerLayoutVersionException::class.java) { repo.save(DashboardLayout()) }
            }
            assertEquals(original, prefs.values)
            assertTrue(prefs.writes.isEmpty())
        }
    }

    @Test fun legacyThatGrowsPastDocumentLimitRecoversWithoutChangingTheOriginal() {
        val base = """[{"source":"RPM","unit":"RPM","label":""}]"""
        val label = "x".repeat(LayoutCodec.MAX_BYTES - base.toByteArray(Charsets.UTF_8).size)
        val legacy = base.replace("\"label\":\"\"", "\"label\":\"$label\"")
        assertEquals(LayoutCodec.MAX_BYTES, legacy.toByteArray(Charsets.UTF_8).size)
        val decoded = LayoutCodec.decode(legacy).layout
        assertEquals(label, decoded.tiles.single().label)
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.encode(decoded) }
        listOf(null, "{broken").forEach { stored ->
            val prefs = MemoryPreferences()
            prefs.values["tiles_v1"] = legacy
            stored?.let { prefs.values["layout_v2"] = it }
            val repo = LayoutRepository(prefs.preferences)
            assertEquals(DashboardLayout(), repo.load())
            assertEquals(DashboardLayout(), LayoutRepository(prefs.preferences).load())
            assertEquals(DashboardLayout(), LayoutCodec.decode(prefs.values.getValue("layout_v2")).layout)
            assertEquals(legacy, prefs.values["tiles_v1"])
            assertEquals(listOf("layout_v2"), prefs.writes)
        }
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
