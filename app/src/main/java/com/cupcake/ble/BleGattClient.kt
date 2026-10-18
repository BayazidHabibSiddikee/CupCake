package com.cupcake.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cupcake.network.ProtocolDecoder
import com.cupcake.protocol.DecodedMessage
import com.cupcake.protocol.Command
import com.cupcake.protocol.CommandResponse
import com.cupcake.protocol.SensorData
import com.cupcake.protocol.BatteryData
import com.cupcake.protocol.EventData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.Continuation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * BLE GATT Client for Nordic UART Service (NUS) communication with ESP32.
 * Handles scanning, connection, MTU negotiation, notifications, and command sending.
 * 
 * Nordic UART Service UUIDs:
 * - Service: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
 * - TX Characteristic (notify): 6E400003-B5A3-F393-E0A9-E50E24DCCA9E
 * - RX Characteristic (write): 6E400002-B5A3-F393-E0A9-E50E24DCCA9E
 * 
 * Uses ProtocolDecoder for binary protocol parsing.
 *
 * Permissions: the BLUETOOTH_SCAN/CONNECT checks live in the callers
 * (DeviceSetupViewModel/WifiSetupScreen gate the UI on them), so lint's
 * per-call-site requirement is suppressed here on purpose.
 */
@SuppressLint("MissingPermission")
class BleGattClient(private val context: Context) {

    companion object {
        private const val TAG = "BleGattClient"
        
        // Connection parameters
        private const val SCAN_TIMEOUT_MS = 10000L
        private const val CONNECT_TIMEOUT_MS = 15000L
        private const val SERVICE_DISCOVERY_TIMEOUT_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RECONNECT_DELAY_MS = 1000L
        private const val MAX_MTU = 517
        
        // Nordic UART Service UUIDs
        private const val NUS_SERVICE_UUID_STR = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
        private const val NUS_TX_UUID_STR = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
        private const val NUS_RX_UUID_STR = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
        private const val CCCD_UUID_STR = "00002902-0000-1000-8000-00805F9B34FB"

        /**
         * Check if BLE is supported and enabled.
         */
        fun isBleSupported(context: Context): Boolean {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
            return adapter != null && adapter.isEnabled
        }

        /**
         * Request BLE permissions (Android 12+).
         */
        fun requestPermissions(activity: android.app.Activity, requestCode: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.requestPermissions(
                    arrayOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE
                    ),
                    requestCode
                )
            } else {
                activity.requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    requestCode
                )
            }
        }
    }

    // Nordic UART Service UUIDs
    private val NUS_SERVICE_UUID = UUID.fromString(NUS_SERVICE_UUID_STR)
    private val NUS_TX_UUID = UUID.fromString(NUS_TX_UUID_STR)
    private val NUS_RX_UUID = UUID.fromString(NUS_RX_UUID_STR)
    private val CCCD_UUID = UUID.fromString(CCCD_UUID_STR)

    // State
    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val gattRef = AtomicReference<BluetoothGatt?>(null)
    private val txCharacteristicRef = AtomicReference<BluetoothGattCharacteristic?>(null)
    private val rxCharacteristicRef = AtomicReference<BluetoothGattCharacteristic?>(null)

    private val isScanning = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val isConnected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val currentMtu = AtomicInteger(23)
    private val targetDeviceRef = AtomicReference<BluetoothDevice?>(null)
    private val pendingScanCallback = AtomicReference<ScanCallback?>(null)
    private val pendingGattCallback = AtomicReference<BluetoothGattCallback?>(null)

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val handler = Handler(Looper.getMainLooper())

    // Public reactive streams
    private val decodedMessages = MutableSharedFlow<DecodedMessage>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val messages: SendChannel<DecodedMessage> = Channel(100)
    
    // Connection state flow
    private val connectionStateFlow = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState = connectionStateFlow.asSharedFlow()

    private val scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 20)
    val scanResultsFlow = scanResults.asSharedFlow()

    // Pending command responses
    private val pendingCommands = ConcurrentHashMap<String, CommandCompleter>()

    sealed interface ConnectionState {
        data class Disconnected(val reason: String? = null) : ConnectionState
        data class Scanning(val deviceName: String? = null) : ConnectionState
        data class Connecting(val deviceName: String) : ConnectionState
        data class Connected(val deviceName: String, val mtu: Int) : ConnectionState
        data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState
        data class Failed(val error: String) : ConnectionState
    }

    private class CommandCompleter(
        val commandId: String,
        val continuation: Continuation<CommandResponse>
    )

    /**
     * Start scanning for Nordic UART Service devices.
     */
    fun startScan(deviceNameFilter: String? = null): Boolean {
        if (isScanning.getAndSet(true)) {
            return false
        }

        val filters = mutableListOf<ScanFilter>()
        if (deviceNameFilter != null && deviceNameFilter.isNotBlank()) {
            filters.add(ScanFilter.Builder().setDeviceName(deviceNameFilter).build())
        }
        // Also filter by NUS service UUID
        filters.add(ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(NUS_SERVICE_UUID)).build())

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch {
                    scanResults.tryEmit(result)
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                scope.launch {
                    results.forEach { scanResults.tryEmit(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                stopScan()
                connectionStateFlow.tryEmit(ConnectionState.Failed("Scan failed: $errorCode"))
            }
        }

        pendingScanCallback.set(callback)

        val s = scanner
        if (s == null) {
            Log.e(TAG, "BluetoothLeScanner not available")
            isScanning.set(false)
            return false
        }
        s.startScan(filters, settings, callback)

        // Auto-stop scan after timeout
        handler.postDelayed({
            stopScan()
            if (!isConnected.get()) {
                connectionStateFlow.tryEmit(ConnectionState.Failed("Scan timeout"))
            }
        }, SCAN_TIMEOUT_MS)

        connectionStateFlow.tryEmit(ConnectionState.Scanning(deviceNameFilter))
        return true
    }

    /**
     * Stop scanning.
     */
    fun stopScan() {
        if (isScanning.getAndSet(false)) {
            val callback = pendingScanCallback.getAndSet(null)
            callback?.let { scanner?.stopScan(it) }
        }
    }

    /**
     * Connect to a specific device.
     */
    fun connect(device: BluetoothDevice): Boolean {
        if (isConnecting.getAndSet(true)) {
            return false
        }

        targetDeviceRef.set(device)
        reconnectAttempts.set(0)

        connectionStateFlow.tryEmit(ConnectionState.Connecting(device.name ?: "Unknown"))

        val callback = createGattCallback(device)
        pendingGattCallback.set(callback)

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        gattRef.set(gatt)

        // Connection timeout
        handler.postDelayed({
            if (isConnecting.get() && !isConnected.get()) {
                disconnect("Connection timeout")
            }
        }, CONNECT_TIMEOUT_MS)

        return true
    }

    private fun createGattCallback(device: BluetoothDevice): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                scope.launch {
                    when {
                        newState == BluetoothProfile.STATE_CONNECTED -> {
                            Log.i(TAG, "Connected to ${device.name}")
                            isConnecting.set(false)
                            
                            // Request MTU
                            val mtuRequested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                gatt.requestMtu(MAX_MTU)
                            } else {
                                false
                            }
                            if (!mtuRequested) {
                                discoverServices(gatt)
                            }
                        }
                        newState == BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.i(TAG, "Disconnected from ${device.name}: status=$status")
                            handleDisconnect(status)
                        }
                        else -> {
                            Log.w(TAG, "Connection state change: newState=$newState, status=$status")
                        }
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                scope.launch {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "MTU negotiated: $mtu")
                        currentMtu.set(mtu)
                        discoverServices(gatt)
                    } else {
                        Log.w(TAG, "MTU negotiation failed: $status, proceeding with default")
                        discoverServices(gatt)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                scope.launch {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val deviceName = targetDeviceRef.get()?.name ?: "Unknown"
                        setupNordicUART(gatt, deviceName)
                    } else {
                        Log.e(TAG, "Service discovery failed: $status")
                        disconnect("Service discovery failed: $status")
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                scope.launch {
                    val value = characteristic.value
                    if (value != null && value.isNotEmpty()) {
                        val decoded = ProtocolDecoder.decode(value)
                        decoded?.let {
                            decodedMessages.tryEmit(it)
                            messages.trySend(it)
                            
                            if (it is CommandResponse) {
                                pendingCommands.remove(it.id)?.continuation?.resume(it)
                            }
                        }
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Characteristic write failed: $status")
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Descriptor write failed: $status")
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "RSSI: $rssi dBm")
                }
            }
        }
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        val discovered = gatt.discoverServices()
        if (!discovered) {
            Log.e(TAG, "discoverServices() returned false")
            disconnect("discoverServices() failed")
        } else {
            handler.postDelayed({
                if (isConnecting.get() && !isConnected.get()) {
                    disconnect("Service discovery timeout")
                }
            }, SERVICE_DISCOVERY_TIMEOUT_MS)
        }
    }

    private fun setupNordicUART(gatt: BluetoothGatt, deviceName: String) {
        val service = gatt.getService(NUS_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "NUS service not found")
            disconnect("NUS service not found")
            return
        }

        val txChar = service.getCharacteristic(NUS_TX_UUID)
        val rxChar = service.getCharacteristic(NUS_RX_UUID)

        if (txChar == null || rxChar == null) {
            Log.e(TAG, "NUS characteristics not found: tx=${txChar != null}, rx=${rxChar != null}")
            disconnect("NUS characteristics not found")
            return
        }

        val notifyEnabled = gatt.setCharacteristicNotification(txChar, true)
        if (!notifyEnabled) {
            Log.e(TAG, "Failed to enable notifications")
            disconnect("Failed to enable notifications")
            return
        }

        val cccd = txChar.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val written = gatt.writeDescriptor(cccd)
            if (!written) {
                Log.e(TAG, "Failed to write CCCD")
                disconnect("Failed to write CCCD")
                return
            }
        } else {
            Log.w(TAG, "CCCD descriptor not found")
        }

        txCharacteristicRef.set(txChar)
        rxCharacteristicRef.set(rxChar)
        isConnecting.set(false)
        isConnected.set(true)
        reconnectAttempts.set(0)

        val mtu = currentMtu.get()

        Log.i(TAG, "Nordic UART setup complete, MTU=$mtu")
        connectionStateFlow.tryEmit(ConnectionState.Connected(deviceName, mtu))
    }

    private fun handleDisconnect(status: Int) {
        isConnecting.set(false)
        isConnected.set(false)
        txCharacteristicRef.set(null)
        rxCharacteristicRef.set(null)
        gattRef.getAndSet(null)?.close()

        val targetDevice = targetDeviceRef.get()
        
        if (reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
            val attempt = reconnectAttempts.incrementAndGet()
            val delay = BASE_RECONNECT_DELAY_MS * (1 shl (attempt - 1))
            
            Log.i(TAG, "Scheduling reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${delay}ms")
            connectionStateFlow.tryEmit(ConnectionState.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS))
            
            handler.postDelayed({
                targetDevice?.let { connect(it) }
            }, delay.toLong())
        } else {
            Log.e(TAG, "Max reconnect attempts reached")
            connectionStateFlow.tryEmit(ConnectionState.Failed("Max reconnect attempts reached"))
        }
    }

    /**
     * Send a command to the ESP32 and await its matching response.
     * [timeoutMs] should exceed the firmware's handling time (e.g. WiFi scans).
     */
    suspend fun sendCommand(command: Command, timeoutMs: Long = 5000L): CommandResponse? {
        if (!isConnected.get()) {
            Log.w(TAG, "Not connected, cannot send command")
            return CommandResponse(
                id = command.id,
                status = "error",
                error = "Not connected"
            )
        }

        val rxChar = rxCharacteristicRef.get() ?: return CommandResponse(
            id = command.id,
            status = "error",
            error = "RX characteristic not available"
        )

        // Auto-attach the pairing token so every command is device-authenticated.
        val effective = if (command.type != "auth") {
            DeviceAuthStore.token(targetDeviceRef.get()?.address ?: "")?.let { t ->
                command.copy(params = command.params + ("token" to t))
            } ?: command
        } else command

        val data = ProtocolDecoder.encodeCommand(effective)
        if (data.isEmpty()) {
            return CommandResponse(
                id = command.id,
                status = "error",
                error = "Failed to encode command"
            )
        }

        rxChar.value = data
        rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val sent = gattRef.get()?.writeCharacteristic(rxChar) ?: false
        if (!sent) {
            return CommandResponse(
                id = command.id,
                status = "error",
                error = "writeCharacteristic() returned false"
            )
        }

        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                pendingCommands[command.id] = CommandCompleter(command.id, cont)
                cont.invokeOnCancellation {
                    pendingCommands.remove(command.id)
                }
            }
        }
    }

    /**
     * Send raw bytes to RX characteristic.
     */
    suspend fun sendRaw(data: ByteArray): Boolean {
        if (!isConnected.get()) return false
        
        val rxChar = rxCharacteristicRef.get() ?: return false
        rxChar.value = data
        rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        
        return gattRef.get()?.writeCharacteristic(rxChar) ?: false
    }

    /**
     * Disconnect and cleanup.
     */
    fun disconnect(reason: String = "User requested") {
        stopScan()
        
        val gatt = gattRef.getAndSet(null)
        gatt?.close()
        
        txCharacteristicRef.set(null)
        rxCharacteristicRef.set(null)
        isConnecting.set(false)
        isConnected.set(false)
        reconnectAttempts.set(0)
        targetDeviceRef.set(null)
        
        pendingCommands.clear()
        
        connectionStateFlow.tryEmit(ConnectionState.Disconnected(reason))
        Log.i(TAG, "Disconnected: $reason")
    }

    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * Get current connected device name.
     */
    fun getConnectedDeviceName(): String? = targetDeviceRef.get()?.name

    /**
     * Shutdown client completely.
     */
    fun shutdown() {
        disconnect("Shutdown")
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}

