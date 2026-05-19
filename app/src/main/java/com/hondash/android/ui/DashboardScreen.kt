package com.hondash.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hondash.android.kpro.KproSnapshot
import com.hondash.android.ui.tiles.AddTileDialog
import com.hondash.android.ui.tiles.TileConfigDialog
import com.hondash.android.ui.tiles.TilePager
import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.ui.tiles.TileUnits

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()

    var configureIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        StatusBar(
            state = state,
            snapshot = snapshot,
            onConnect = viewModel::tryConnect,
            onSimulate = viewModel::startSimulation,
            onStop = viewModel::disconnect,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        TilePager(
            tiles = tiles,
            snapshot = snapshot,
            live = state == ConnectionState.Connected || state == ConnectionState.Simulating,
            onTileLongPress = { configureIndex = it },
            onAddTile = { showAdd = true },
            modifier = Modifier.fillMaxSize(),
        )
    }

    configureIndex?.let { idx ->
        val spec = tiles.getOrNull(idx)
        if (spec != null) {
            TileConfigDialog(
                spec = spec,
                onSave = { updated ->
                    viewModel.updateTile(idx, updated)
                    configureIndex = null
                },
                onRemove = {
                    viewModel.removeTile(idx)
                    configureIndex = null
                },
                onDismiss = { configureIndex = null },
            )
        } else {
            configureIndex = null
        }
    }

    if (showAdd) {
        AddTileDialog(
            onAdd = { source ->
                viewModel.addTile(TileSpec(source, TileUnits.defaultFor(source)))
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun StatusBar(
    state: ConnectionState,
    snapshot: KproSnapshot,
    onConnect: () -> Unit,
    onSimulate: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val statusText = when (state) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.RequestingPermission -> "Requesting USB permission"
            ConnectionState.Connecting -> "Connecting"
            ConnectionState.Connected ->
                "Connected  •  fw ${snapshot.firmware}  •  s/n ${snapshot.serial}"
            ConnectionState.Simulating -> "Simulating  •  no ECU attached"
            ConnectionState.Error -> "Error"
        }
        Text(
            "HonDash",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            statusText,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        when (state) {
            ConnectionState.Connected, ConnectionState.Simulating -> {
                Button(onClick = onStop) { Text("Stop") }
            }
            else -> {
                Button(onClick = onConnect) { Text("Connect") }
                Button(onClick = onSimulate) { Text("Demo") }
            }
        }
    }
}
