package com.cupcake.ui.screen.device

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.ble.BleGattClient
import com.cupcake.ui.theme.CupCakeTheme

/**
 * Connect to a nearby ESP32 over Bluetooth and configure its WiFi credentials.
 */
@Composable
fun WifiSetupScreen(
    onClose: () -> Unit,
    viewModel: DeviceSetupViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val networks by viewModel.wifiNetworks.collectAsStateWithLifecycle()
    val status by viewModel.wifiStatus.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(viewModel.hasPermissions()) }
    var pendingSsid by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = viewModel.hasPermissions()
        if (hasPermission) viewModel.startScan()
    }
    val blePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val connectedName = (connection as? BleGattClient.ConnectionState.Connected)?.deviceName

    CupCakeTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Connect Device",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!viewModel.isBleSupported) {
                    item { InfoCard("Bluetooth is off or unsupported. Enable Bluetooth and retry.") }
                    return@LazyColumn
                }

                message?.let { text ->
                    item {
                        InfoCard(text, onDismiss = { viewModel.clearMessage() })
                    }
                }

                item { SectionHeader("1. Pair over Bluetooth") }

                if (!hasPermission) {
                    item {
                        Button(onClick = { permissionLauncher.launch(blePermissions) }) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Grant Bluetooth permission")
                        }
                    }
                } else if (connectedName == null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() }
                            ) {
                                Text(if (isScanning) "Stop scan" else "Scan for devices")
                            }
                        }
                    }
                    items(devices, key = { it.address }) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.connect(device.address) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(device.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${device.address}   ${device.rssi} dBm",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (isScanning && devices.isEmpty()) {
                        item { InfoCard("Searching for nearby CupCake devices…") }
                    }
                } else {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Connected to $connectedName", modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
                            }
                        }
                    }

                    item { SectionHeader("2. WiFi") }

                    status?.let { s ->
                        item {
                            InfoCard(
                                "State: ${s.state}" +
                                    (if (s.ssid.isNotBlank()) "  •  ${s.ssid}" else "") +
                                    (if (s.ip.isNotBlank()) "  •  ${s.ip}" else "")
                            )
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.scanWifiNetworks() },
                                enabled = !busy
                            ) { Text("Scan WiFi networks") }
                            OutlinedButton(
                                onClick = { viewModel.forgetWifi() },
                                enabled = !busy
                            ) { Text("Forget") }
                        }
                    }

                    items(networks, key = { it.ssid }) { network ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    if (network.secure) {
                                        pendingSsid = network.ssid
                                        password = ""
                                    } else {
                                        viewModel.connectWifi(network.ssid, "")
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Wifi, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(network.ssid, modifier = Modifier.weight(1f))
                                if (network.secure) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = "Secured",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.size(8.dp))
                                Text("${network.rssi} dBm", fontSize = 12.sp)
                            }
                        }
                    }

                    if (busy) {
                        item { InfoCard("Working…") }
                    }
                }
            }
        }
    }

    pendingSsid?.let { ssid ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingSsid = null },
            title = { Text("WiFi password") },
            text = {
                Column {
                    Text(ssid)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = password
                    pendingSsid = null
                    viewModel.connectWifi(ssid, p)
                }) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSsid = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun InfoCard(text: String, onDismiss: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
