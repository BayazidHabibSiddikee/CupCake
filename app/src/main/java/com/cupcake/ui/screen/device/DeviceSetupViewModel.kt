package com.cupcake.ui.screen.device

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ble.BleGattClient
import com.cupcake.network.WifiNetwork
import com.cupcake.network.WifiProvisioner
import com.cupcake.network.WifiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the "connect to a nearby ESP32 over BLE, then provision its WiFi" flow.
 */
@HiltViewModel
class DeviceSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class DeviceUi(val name: String, val address: String, val rssi: Int)

    private val client = BleGattClient(context)
    private val provisioner = WifiProvisioner(client)
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()

    private val _devices = MutableStateFlow<List<DeviceUi>>(emptyList())
    val devices: StateFlow<List<DeviceUi>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connection =
        MutableStateFlow<BleGattClient.ConnectionState>(BleGattClient.ConnectionState.Disconnected())
    val connection: StateFlow<BleGattClient.ConnectionState> = _connection.asStateFlow()

    private val _wifiNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiNetworks: StateFlow<List<WifiNetwork>> = _wifiNetworks.asStateFlow()

    private val _wifiStatus = MutableStateFlow<WifiStatus?>(null)
    val wifiStatus: StateFlow<WifiStatus?> = _wifiStatus.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _connectingAddress = MutableStateFlow<String?>(null)

    val isBleSupported: Boolean = BleGattClient.isBleSupported(context)

    init {
        viewModelScope.launch {
            client.scanResultsFlow.collect { result -> recordDevice(result.device, result.rssi) }
        }
        viewModelScope.launch {
            client.connectionState.collect { state ->
                _connection.value = state
                if (state is BleGattClient.ConnectionState.Connected) {
                    _isScanning.value = false
                    _message.value = "Authenticating with ${state.deviceName}…"
                    val address = _connectingAddress.value
                    val ok = address?.let { authenticate(it) } ?: false
                    if (ok) {
                        _message.value = "Paired with ${state.deviceName}"
                        refreshStatus()
                    }
                    _busy.value = false
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun recordDevice(device: BluetoothDevice, rssi: Int) {
        val address = device.address ?: return
        deviceMap[address] = device
        val name = runCatching { device.name }.getOrNull() ?: "Unknown device"
        val current = _devices.value
        if (current.none { it.address == address }) {
            _devices.value = (current + DeviceUi(name, address, rssi))
                .sortedByDescending { it.rssi }
        }
    }

    fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        has(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun startScan() {
        if (!hasPermissions()) {
            _message.value = "Bluetooth permission required"
            return
        }
        _devices.value = emptyList()
        deviceMap.clear()
        client.startScan()
        _isScanning.value = true
        viewModelScope.launch {
            delay(10_000)
            _isScanning.value = false
        }
    }

    fun stopScan() {
        client.stopScan()
        _isScanning.value = false
    }

    fun connect(address: String) {
        val device = deviceMap[address] ?: return
        stopScan()
        _message.value = "Connecting…"
        _busy.value = true
        _connectingAddress.value = address
        client.connect(device)
    }

    /**
     * Device-auth handshake right after GATT connect.
     * Owned devices verify our stored token; unowned ones get bound to this
     * phone with a freshly minted token. Boards owned by a DIFFERENT phone
     * are rejected (single-account device binding).
     */
    private suspend fun authenticate(address: String): Boolean {
        val stored = com.cupcake.ble.DeviceAuthStore.binding(address)
        return runCatching {
            // 1) challenge (with our token, when we have one)
            var resp: com.cupcake.protocol.CommandResponse? = client.sendCommand(
                com.cupcake.protocol.Command(
                    type = "auth",
                    action = "challenge",
                    params = mapOf("token" to (stored?.token ?: ""))
                ),
                timeoutMs = 8_000L
            )
            if (resp == null) return false
            if (resp?.status != "ok") return false
            val bound = resp?.data?.get("bound") == "true"
            val authorized = resp?.data?.get("authorized") == "true"
            val deviceId = resp?.data?.get("device_id") ?: ""

            if (bound && stored == null) {
                _message.value = "🔒 This CupCake is paired with someone else's phone"
                client.disconnect("Device owned by another phone")
                return false
            }
            if (bound && authorized) {
                return true
            }
            if (!bound) {
                // 2) bind: mint token, write it, confirm
                val fresh = com.cupcake.ble.DeviceAuthStore.mintToken(address)
                resp = client.sendCommand(
                    com.cupcake.protocol.Command(
                        type = "auth",
                        action = "pair",
                        params = mapOf("token" to fresh, "owner" to "local")
                    ),
                    timeoutMs = 8_000L
                )
                if (resp?.status != "ok") {
                    _message.value = resp?.data?.get("message") ?: "Pairing failed"
                    client.disconnect("Pairing failed")
                    return false
                }
                com.cupcake.ble.DeviceAuthStore.bind(address, fresh, deviceId)
                _message.value = "🔒 Paired & locked to this phone"
            }
            true
        }.getOrElse { e ->
            _message.value = "Auth failed: ${e.message}"
            false
        }
    }

    fun disconnect() {
        client.disconnect()
        _wifiStatus.value = null
    }

    fun scanWifiNetworks() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "Scanning WiFi networks…"
            provisioner.scanNetworks()
                .onSuccess { nets ->
                    _wifiNetworks.value = nets.sortedByDescending { it.rssi }
                    _message.value = if (nets.isEmpty()) "No networks found" else "Found ${nets.size} networks"
                }
                .onFailure { _message.value = it.message ?: "Scan failed" }
            _busy.value = false
        }
    }

    /** Sends credentials, then polls status until connected / failed / timeout. */
    fun connectWifi(ssid: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "Connecting to $ssid…"
            val started = provisioner.connect(ssid, password)
            if (started.isFailure) {
                _message.value = started.exceptionOrNull()?.message ?: "Connect failed"
                _busy.value = false
                return@launch
            }
            var status: WifiStatus? = null
            repeat(20) {
                delay(1000)
                status = provisioner.status()
                _wifiStatus.value = status
                if (status?.isConnected == true || status?.hasFailed == true) return@repeat
            }
            _busy.value = false
            val settled = status
            _message.value = when {
                settled?.isConnected == true -> "Connected to ${settled.ssid} (${settled.ip})"
                settled?.hasFailed == true -> "Could not connect to $ssid"
                else -> "Still connecting… check the device"
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            provisioner.status()?.let { _wifiStatus.value = it }
        }
    }

    fun forgetWifi() {
        viewModelScope.launch {
            if (provisioner.forget()) {
                _wifiStatus.value = null
                _message.value = "Saved WiFi cleared"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        client.shutdown()
        super.onCleared()
    }
}
