package com.hondash.android.ui.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Long-press configuration dialog for a single tile.
 *
 * Lets the user override the label (only when the source is
 * user-configurable, e.g. an analog channel), pick a display
 * unit when more than one is available, set warning/alarm
 * thresholds, or remove the tile entirely.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TileConfigDialog(
    spec: TileSpec,
    onSave: (TileSpec) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(spec.label.orEmpty()) }
    var unit by remember { mutableStateOf(spec.unit) }
    var warnHigh by remember { mutableStateOf(spec.warnHigh?.fmt() ?: "") }
    var alarmHigh by remember { mutableStateOf(spec.alarmHigh?.fmt() ?: "") }
    var warnLow by remember { mutableStateOf(spec.warnLow?.fmt() ?: "") }
    var alarmLow by remember { mutableStateOf(spec.alarmLow?.fmt() ?: "") }
    var vMin by remember { mutableStateOf(spec.inputMinV?.fmt() ?: "") }
    var vMax by remember { mutableStateOf(spec.inputMaxV?.fmt() ?: "") }
    var outMin by remember { mutableStateOf(spec.outputMin?.fmt() ?: "") }
    var outMax by remember { mutableStateOf(spec.outputMax?.fmt() ?: "") }
    val units = TileUnits.availableFor(spec.source)

    val showCalibration = spec.source.configurable &&
        (unit == TileUnit.PSI || unit == TileUnit.BAR)
    val validation = validate(
        warnHigh, alarmHigh, warnLow, alarmLow,
        vMin, vMax, outMin, outMax, showCalibration,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${spec.source.defaultLabel}") },
        confirmButton = {
            TextButton(
                enabled = validation == null,
                onClick = {
                    onSave(
                        spec.copy(
                            label = label.takeIf { it.isNotBlank() },
                            unit = unit,
                            warnHigh = warnHigh.toFloatOrNull(),
                            alarmHigh = alarmHigh.toFloatOrNull(),
                            warnLow = warnLow.toFloatOrNull(),
                            alarmLow = alarmLow.toFloatOrNull(),
                            inputMinV = if (showCalibration) vMin.toFloatOrNull() else spec.inputMinV,
                            inputMaxV = if (showCalibration) vMax.toFloatOrNull() else spec.inputMaxV,
                            outputMin = if (showCalibration) outMin.toFloatOrNull() else spec.outputMin,
                            outputMax = if (showCalibration) outMax.toFloatOrNull() else spec.outputMax,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (spec.source.configurable) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (units.size > 1) {
                    Text("Unit", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        units.forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u.symbol.ifEmpty { u.name }) },
                            )
                        }
                    }
                }
                if (showCalibration) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sensor calibration (e.g. 0.5–4.5 V → 0–100 ${unit.symbol})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("V @ min", vMin, Modifier.weight(1f)) { vMin = it }
                        NumField("V @ max", vMax, Modifier.weight(1f)) { vMax = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("${unit.symbol} @ min", outMin, Modifier.weight(1f)) { outMin = it }
                        NumField("${unit.symbol} @ max", outMax, Modifier.weight(1f)) { outMax = it }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Thresholds (leave blank to disable)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Warn ≥", warnHigh, Modifier.weight(1f)) { warnHigh = it }
                    NumField("Warn ≤", warnLow, Modifier.weight(1f)) { warnLow = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Alarm ≥", alarmHigh, Modifier.weight(1f)) { alarmHigh = it }
                    NumField("Alarm ≤", alarmLow, Modifier.weight(1f)) { alarmLow = it }
                }
                if (validation != null) {
                    Text(
                        validation,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

/** Returns the first validation error message, or null if all fields are consistent. */
private fun validate(
    warnHigh: String, alarmHigh: String, warnLow: String, alarmLow: String,
    vMin: String, vMax: String, outMin: String, outMax: String,
    checkCalibration: Boolean,
): String? {
    val wh = warnHigh.toFloatOrNull()
    val ah = alarmHigh.toFloatOrNull()
    val wl = warnLow.toFloatOrNull()
    val al = alarmLow.toFloatOrNull()
    if (wh != null && ah != null && ah < wh) return "Alarm ≥ must be ≥ Warn ≥"
    if (wl != null && al != null && al > wl) return "Alarm ≤ must be ≤ Warn ≤"
    if (wl != null && wh != null && wl >= wh) return "Warn ≤ must be less than Warn ≥"
    if (checkCalibration) {
        val vLo = vMin.toFloatOrNull()
        val vHi = vMax.toFloatOrNull()
        val oLo = outMin.toFloatOrNull()
        val oHi = outMax.toFloatOrNull()
        val anyCal = listOfNotNull(vLo, vHi, oLo, oHi).isNotEmpty()
        val allCal = vLo != null && vHi != null && oLo != null && oHi != null
        if (anyCal && !allCal) return "Calibration needs all four fields"
        if (allCal && vLo!! >= vHi!!) return "V @ min must be less than V @ max"
        if (allCal && oLo == oHi) return "Output range must not be zero"
    }
    return null
}

@Composable
private fun NumField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun Float.fmt(): String =
    if (this == toInt().toFloat()) toInt().toString() else toString()
