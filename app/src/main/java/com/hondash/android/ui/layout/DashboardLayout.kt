package com.hondash.android.ui.layout

import com.hondash.android.ui.tiles.TileSpec

/** The complete, portable dashboard configuration. Alert preferences stay on the device. */
data class DashboardLayout(
    val defaultGrid: GridPreset = GridPreset.G2X2,
    val pageGrids: List<GridPreset> = emptyList(),
    val tiles: List<TileSpec> = TileSpec.DEFAULTS,
    val shiftLight: ShiftLightConfig = ShiftLightConfig(),
    val theme: ThemeMode = ThemeMode.AUTO,
)

enum class GridPreset { G2X2, G3X2, G3X3 }

enum class ThemeMode { AUTO, DAY, NIGHT }

data class ShiftLightConfig(
    val enabled: Boolean = false,
    val startRpm: Int = 6000,
    val shiftRpm: Int = 7800,
)

data class AlertPreferences(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val demoAlerts: Boolean = false,
)
