package com.hondash.android.ui.layout

import com.hondash.android.ui.tiles.TileSource
import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.ui.tiles.TileStyle
import com.hondash.android.ui.tiles.TileUnit
import com.hondash.android.ui.tiles.TileUnits
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LayoutCodecTest {
    private fun document(): JSONObject = JSONObject(LayoutCodec.encode(DashboardLayout()))
    private fun decode(json: JSONObject) = LayoutCodec.decode(json.toString())
    private fun tileDocument(tile: JSONObject): JSONObject = document().put("tiles", JSONArray().put(tile))
    private fun tile(): JSONObject = JSONObject().put("source", "RPM").put("unit", "RPM")

    @Test fun roundTripRetainsEveryField() {
        val layout = DashboardLayout(
            defaultGrid = GridPreset.G3X2,
            pageGrids = GridPreset.entries,
            tiles = TileStyle.entries.map { style ->
                TileSpec(
                    source = TileSource.AN0, unit = TileUnit.PSI, label = "Oil P — 油",
                    warnHigh = 95f, alarmHigh = 100f, warnLow = 20f, alarmLow = 10f,
                    inputMinV = 0.5f, inputMaxV = 4.5f, outputMin = 0f, outputMax = 100f,
                    style = style, gaugeMin = -10f, gaugeMax = 110f, alarmSilenced = true,
                )
            },
            shiftLight = ShiftLightConfig(true, 6500, 8500),
            theme = ThemeMode.NIGHT,
        )
        val encoded = LayoutCodec.encode(layout)
        val decoded = LayoutCodec.decode(encoded.toByteArray())
        assertEquals(layout, decoded.layout)
        assertTrue(decoded.warnings.isEmpty())
        assertEquals(0, decoded.droppedTiles)
        assertEquals("hondash-layout", JSONObject(encoded).getString("format"))
        assertEquals(1, JSONObject(encoded).getInt("version"))
        assertFalse(JSONObject(encoded).has("alerts"))
    }

    @Test fun allSourcesAndUnitsAndThemesRoundTrip() {
        val tiles = TileSource.entries.flatMap { source ->
            TileUnits.availableFor(source).map { TileSpec(source, it) }
        }
        ThemeMode.entries.forEach { theme ->
            val layout = DashboardLayout(tiles = tiles, theme = theme)
            assertEquals(layout, LayoutCodec.decode(LayoutCodec.encode(layout)).layout)
        }
    }

    @Test fun missingOptionalFieldsUseMvpDefaults() {
        val json = JSONObject("""{"format":"hondash-layout","version":1,"tiles":[{"source":"RPM"}]}""")
        val decoded = decode(json)
        assertEquals(DashboardLayout(tiles = listOf(TileSpec(TileSource.RPM, TileUnit.RPM))), decoded.layout)
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test fun unknownFieldsAreIgnored() {
        val json = tileDocument(tile().put("futureTileField", JSONObject().put("x", 1)))
            .put("futureRootField", JSONArray().put("new"))
        json.getJSONObject("shiftLight").put("futureShiftField", true)
        assertTrue(decode(json).warnings.isEmpty())
    }

    @Test fun unknownSourcesAreDroppedWithoutReorderingKnownTiles() {
        val tiles = JSONArray().put(tile()).put(tile().put("source", "FUTURE"))
            .put(tile().put("source", "ECT").put("unit", "CELSIUS"))
        val decoded = decode(document().put("tiles", tiles))
        assertEquals(listOf(TileSource.RPM, TileSource.ECT), decoded.layout.tiles.map { it.source })
        assertEquals(1, decoded.droppedTiles)
        assertTrue(decoded.warnings.single().contains("tiles[1].source"))
    }

    @Test fun unknownEnumsFallBackAndProducePreviewWarnings() {
        val json = tileDocument(tile().put("unit", "NEW_UNIT").put("style", "NEW_STYLE"))
            .put("defaultGrid", "NEW_GRID").put("pageGrids", JSONArray().put("NEW_GRID"))
            .put("theme", "NEW_THEME")
        val decoded = decode(json)
        assertEquals(GridPreset.G2X2, decoded.layout.defaultGrid)
        assertEquals(listOf(GridPreset.G2X2), decoded.layout.pageGrids)
        assertEquals(ThemeMode.AUTO, decoded.layout.theme)
        assertEquals(TileStyle.NUMERIC, decoded.layout.tiles.single().style)
        assertEquals(TileUnit.RPM, decoded.layout.tiles.single().unit)
        assertEquals(5, decoded.warnings.size)
    }

    @Test fun invalidPageGridUsesTheLayoutsDefaultGrid() {
        val json = document().put("defaultGrid", "G3X3").put("pageGrids", JSONArray().put("future"))
        assertEquals(listOf(GridPreset.G3X3), decode(json).layout.pageGrids)
    }

    @Test fun invalidNumbersResetEveryNumericTileField() {
        val fields = listOf(
            "warnHigh", "alarmHigh", "warnLow", "alarmLow", "inputMinV", "inputMaxV",
            "outputMin", "outputMax", "gaugeMin", "gaugeMax",
        )
        listOf("NaN", "Infinity", "-Infinity", "1e400", "3.5e38", "not a number").forEach { invalid ->
            val tile = tile().apply { fields.forEach { put(it, invalid) } }
            val decoded = decode(tileDocument(tile))
            assertEquals(TileSpec(TileSource.RPM, TileUnit.RPM), decoded.layout.tiles.single())
            assertEquals(fields.size, decoded.warnings.size)
        }
        val overflowing = """{"format":"hondash-layout","version":1,"tiles":[{"source":"RPM","warnHigh":1e400}]}"""
        assertNull(LayoutCodec.decode(overflowing).layout.tiles.single().warnHigh)
    }

    @Test fun nullOptionalNumbersAreUnsetWithoutWarnings() {
        val decoded = decode(tileDocument(tile().put("gaugeMin", JSONObject.NULL).put("warnHigh", JSONObject.NULL)))
        assertNull(decoded.layout.tiles.single().gaugeMin)
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test fun equalOrReversedGaugeRangesResetBothBounds() {
        listOf(100 to 100, 100 to 0).forEach { (min, max) ->
            val decoded = decode(tileDocument(tile().put("gaugeMin", min).put("gaugeMax", max)))
            assertNull(decoded.layout.tiles.single().gaugeMin)
            assertNull(decoded.layout.tiles.single().gaugeMax)
            assertEquals(1, decoded.warnings.size)
        }
    }

    @Test fun singleGaugeBoundIsPreserved() {
        val decoded = decode(tileDocument(tile().put("gaugeMin", -10)))
        assertEquals(-10f, decoded.layout.tiles.single().gaugeMin)
        assertNull(decoded.layout.tiles.single().gaugeMax)
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test fun invalidShiftRangesResetToSafeDefaultsAndWarn() {
        listOf(0 to 7800, -1 to 7800, 7800 to 7800, 8000 to 7800, 6000 to 12001).forEach { (start, shift) ->
            val config = JSONObject().put("enabled", true).put("startRpm", start).put("shiftRpm", shift)
            val decoded = decode(document().put("shiftLight", config))
            assertEquals(ShiftLightConfig(enabled = true), decoded.layout.shiftLight)
            assertEquals(1, decoded.warnings.size)
        }
        listOf("NaN", "Infinity", 6500.5, JSONObject.NULL).forEach { start ->
            val decoded = decode(document().put("shiftLight", JSONObject().put("startRpm", start)))
            assertEquals(ShiftLightConfig(), decoded.layout.shiftLight)
            assertEquals(1, decoded.warnings.size)
        }
    }

    @Test fun shiftRpmUpperBoundaryIsAccepted() {
        val config = JSONObject().put("enabled", true).put("startRpm", 1).put("shiftRpm", 12000)
        assertEquals(ShiftLightConfig(true, 1, 12000), decode(document().put("shiftLight", config)).layout.shiftLight)
    }

    @Test fun newerVersionHasActionableUpdateMessage() {
        val error = assertThrows(LayoutCodecException::class.java) { decode(document().put("version", 2)) }
        assertEquals(LayoutCodec.NEWER_VERSION_MESSAGE, error.message)
    }

    @Test fun invalidVersionsAreRejected() {
        listOf(0, -1, 1.5, "1", JSONObject.NULL).forEach { version ->
            assertThrows(LayoutCodecException::class.java) { decode(document().put("version", version)) }
        }
        assertThrows(LayoutCodecException::class.java) { decode(document().apply { remove("version") }) }
    }

    @Test fun wrongOrMissingFormatIsRejected() {
        listOf("other-layout", "", JSONObject.NULL).forEach { format ->
            assertThrows(LayoutCodecException::class.java) { decode(document().put("format", format)) }
        }
        assertThrows(LayoutCodecException::class.java) { decode(document().apply { remove("format") }) }
    }

    @Test fun invalidJsonAndTrailingContentAreRejected() {
        listOf("", "not JSON", "{", "{\"tiles\":[", "null", "123", "true", "{} {}").forEach { text ->
            assertThrows("Should reject $text", LayoutCodecException::class.java) { LayoutCodec.decode(text) }
        }
    }

    @Test fun lenientJsonExtensionsAndEmbeddedNulAreRejected() {
        val text = document().toString()
        val invalid = listOf(
            text.replace("\"format\"", "format"),
            text.replace("\"format\"", "'format'"),
            text.dropLast(1) + ",}",
            text + "\u0000garbage",
            text + "/* comment */",
            text.replace("\"version\":1", "\"version\":01"),
            text.replace("\"version\":1", "\"version\":+1"),
            text.replace("\"version\":1", "\"version\":1."),
            text.replace("\"version\":1", "\"version\":1e"),
            """{"format":"hondash-layout","version":1,"tiles":[,]}""",
            text.replace("hondash-layout", "hon\ndash-layout"),
            text.replace("hondash-layout", "hon\\xdash-layout"),
        )
        invalid.forEach { json ->
            assertThrows("Should reject lenient syntax", LayoutCodecException::class.java) {
                LayoutCodec.decode(json)
            }
        }
    }

    @Test fun deeplyNestedUnknownFieldsAreRejectedWithoutStackExhaustion() {
        val json = document().put("future", "placeholder").toString()
            .replace("\"placeholder\"", "[".repeat(1000) + "0" + "]".repeat(1000))
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.decode(json) }
    }

    @Test fun overflowingUnknownNumbersAreIgnoredAndValidEscapesSurvive() {
        val text = """{"format":"hondash-layout","version":1,"future":1e400,"tiles":[{"source":"RPM","label":"Oil \"P\" \u6cb9"}]}"""
        val decoded = LayoutCodec.decode(text)
        assertEquals("Oil \"P\" 油", decoded.layout.tiles.single().label)
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test fun malformedContainersAreRejectedRatherThanReplacingTheLayout() {
        listOf("tiles" to JSONObject(), "pageGrids" to "G2X2", "shiftLight" to true).forEach { (key, value) ->
            assertThrows(LayoutCodecException::class.java) { decode(document().put(key, value)) }
        }
        assertThrows(LayoutCodecException::class.java) { decode(document().apply { remove("tiles") }) }
        assertThrows(LayoutCodecException::class.java) { decode(document().put("tiles", JSONArray().put(42))) }
    }

    @Test fun atMost200TilesAreAcceptedIncludingUnknownSources() {
        val tiles = JSONArray().apply { repeat(200) { put(tile()) } }
        assertEquals(200, decode(document().put("tiles", tiles)).layout.tiles.size)
        tiles.put(tile().put("source", "future"))
        assertThrows(LayoutCodecException::class.java) { decode(document().put("tiles", tiles)) }
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.decode(tiles.toString()) }
        assertThrows(LayoutCodecException::class.java) {
            LayoutCodec.encode(DashboardLayout(tiles = List(201) { TileSpec.DEFAULTS.first() }))
        }
    }

    @Test fun sizeLimitCountsUtf8BytesAndAcceptsExactBoundary() {
        val text = document().toString()
        val atLimit = text + " ".repeat(LayoutCodec.MAX_BYTES - text.toByteArray().size)
        assertEquals(DashboardLayout(), LayoutCodec.decode(atLimit.toByteArray()).layout)
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.decode(atLimit + " ") }
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.decode((atLimit + " ").toByteArray()) }
        val unicode = tileDocument(tile().put("label", "油".repeat(90000))).toString()
        assertTrue(unicode.length < LayoutCodec.MAX_BYTES)
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.decode(unicode) }
        assertThrows(LayoutCodecException::class.java) {
            LayoutCodec.encode(DashboardLayout(tiles = listOf(TileSpec.DEFAULTS.first().copy(label = "油".repeat(90000)))))
        }
    }

    @Test fun malformedUtf8IsRejected() {
        assertThrows(LayoutCodecException::class.java) { LayoutCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    }

    @Test fun emptyLayoutsAndAllDroppedTilesStayEmpty() {
        assertTrue(decode(document().put("tiles", JSONArray())).layout.tiles.isEmpty())
        val decoded = decode(tileDocument(tile().put("source", "future")))
        assertTrue(decoded.layout.tiles.isEmpty())
        assertEquals(1, decoded.droppedTiles)
    }

    @Test fun legacyMvpFixtureKeepsThresholdsCalibrationAndNumericStyle() {
        val text = javaClass.getResource("/tiles_v1.json")!!.readText()
        val decoded = LayoutCodec.decode(text)
        assertEquals(6, decoded.layout.tiles.size)
        assertTrue(decoded.warnings.isEmpty())
        assertEquals(7500f, decoded.layout.tiles[0].warnHigh)
        assertEquals(8200f, decoded.layout.tiles[0].alarmHigh)
        assertEquals(TileUnit.FAHRENHEIT, decoded.layout.tiles[1].unit)
        assertEquals(80f, decoded.layout.tiles[1].alarmLow)
        val oil = decoded.layout.tiles[2]
        assertEquals("Oil P", oil.label)
        assertEquals(0.5f, oil.inputMinV)
        assertEquals(4.5f, oil.inputMaxV)
        assertEquals(0f, oil.outputMin)
        assertEquals(100f, oil.outputMax)
        assertEquals(50f, oil.calibration!!.apply(2.5f), 0.001f)
        assertEquals(6.895f, decoded.layout.tiles[3].calibration!!.apply(0f), 0.001f)
        assertTrue(decoded.layout.tiles.all { it.style == TileStyle.NUMERIC && !it.alarmSilenced })
        assertEquals(GridPreset.G2X2, decoded.layout.defaultGrid)
        assertEquals(ShiftLightConfig(), decoded.layout.shiftLight)
        assertEquals(ThemeMode.AUTO, decoded.layout.theme)
        assertEquals(decoded.layout, LayoutCodec.decode(LayoutCodec.encode(decoded.layout)).layout)
    }
}
