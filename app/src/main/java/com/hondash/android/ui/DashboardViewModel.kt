package com.hondash.android.ui

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hondash.android.kpro.KproSnapshot
import com.hondash.android.kpro.KproUsbConnection
import com.hondash.android.kpro.KproV4
import com.hondash.android.ui.tiles.TileRepository
import com.hondash.android.ui.tiles.TileSpec
import com.hondash.android.usb.UsbController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionState { Disconnected, RequestingPermission, Connecting, Connected, Simulating, Error }

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val usb = UsbController(app)
    private val tileRepo = TileRepository(app)

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow(KproSnapshot.EMPTY)
    val snapshot: StateFlow<KproSnapshot> = _snapshot.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tiles = MutableStateFlow(tileRepo.load())
    val tiles: StateFlow<List<TileSpec>> = _tiles.asStateFlow()

    private var pollJob: Job? = null
    private var kpro: KproV4? = null

    fun addTile(spec: TileSpec) {
        _tiles.value = _tiles.value + spec
        tileRepo.save(_tiles.value)
    }

    fun updateTile(index: Int, spec: TileSpec) {
        val list = _tiles.value.toMutableList()
        if (index !in list.indices) return
        list[index] = spec
        _tiles.value = list
        tileRepo.save(list)
    }

    fun removeTile(index: Int) {
        val list = _tiles.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _tiles.value = list
        tileRepo.save(list)
    }

    /** Look for a K-Pro on the bus, prompting for permission if needed. */
    fun tryConnect(deviceHint: UsbDevice? = null) {
        if (_state.value == ConnectionState.Connected ||
            _state.value == ConnectionState.RequestingPermission
        ) return

        val device = deviceHint?.takeIf(usb::isKpro) ?: usb.findKpro()
        if (device == null) {
            _state.value = ConnectionState.Disconnected
            _error.value = "No K-Pro V4 detected. Plug in the USB-OTG cable."
            return
        }
        if (!usb.hasPermission(device)) {
            _state.value = ConnectionState.RequestingPermission
            usb.requestPermission(device) { granted ->
                if (granted) connect(device) else {
                    _state.value = ConnectionState.Disconnected
                    _error.value = "USB permission denied."
                }
            }
        } else {
            connect(device)
        }
    }

    private fun connect(device: UsbDevice) {
        _state.value = ConnectionState.Connecting
        _error.value = null
        viewModelScope.launch {
            val conn = withContext(Dispatchers.IO) {
                KproUsbConnection.open(usb.usbManager, device)
            }
            if (conn == null) {
                _state.value = ConnectionState.Error
                _error.value = "Failed to open USB device."
                return@launch
            }
            kpro = KproV4(conn)
            _state.value = ConnectionState.Connected
            startPolling()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            val device = kpro ?: return@launch
            var consecutiveFailures = 0
            while (true) {
                val ok = device.pollOnce()
                if (ok) {
                    consecutiveFailures = 0
                    _snapshot.value = device.snapshot()
                    delay(POLL_INTERVAL_MS)
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_POLL_FAILURES) {
                        _state.value = ConnectionState.Error
                        _error.value = "USB read failed; reconnect required."
                        break
                    }
                    // Brief backoff before retrying — survives transient bus hiccups.
                    delay(POLL_BACKOFF_MS * consecutiveFailures)
                }
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        kpro?.close()
        kpro = null
        _state.value = ConnectionState.Disconnected
        _snapshot.value = KproSnapshot.EMPTY
    }

    /** Stream fake but plausible snapshots so the UI can be exercised without an ECU. */
    fun startSimulation() {
        if (_state.value == ConnectionState.Simulating) return
        disconnect()
        _error.value = null
        _state.value = ConnectionState.Simulating
        pollJob = viewModelScope.launch(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            while (true) {
                val tSec = (System.currentTimeMillis() - start) / 1000.0
                _snapshot.value = SimulatedKpro.sample(tSec)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
        const val POLL_BACKOFF_MS = 200L
        const val MAX_POLL_FAILURES = 3
    }
}
