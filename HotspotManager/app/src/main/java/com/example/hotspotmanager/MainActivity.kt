package com.example.hotspotmanager

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var hotspotInfoProvider: HotspotInfoProvider
    private val connectedDevicesReader = ConnectedDevicesReader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hotspotInfoProvider = HotspotInfoProvider(applicationContext)

        setContent {
            MaterialTheme {
                HotspotManagerScreen(
                    onOpenHotspotSettings = { openHotspotSettings() },
                    hotspotInfoProvider = hotspotInfoProvider,
                    connectedDevicesReader = connectedDevicesReader
                )
            }
        }
    }

    /**
     * Deep-links into Android's own Tethering/Hotspot settings screen.
     *
     * There is no stable public API on modern Android for a third-party app to
     * silently flip the internet-sharing hotspot on/off. The user must tap the
     * toggle themselves on the system Settings screen — this is a deliberate
     * Android platform restriction (since Android 8), not a limitation of this app.
     *
     * We try the dedicated tethering settings action first, since it's more direct,
     * and fall back to general wireless settings if that's not available on this
     * device/OEM skin.
     */
    private fun openHotspotSettings() {
        val tetherIntent = Intent("android.settings.TETHER_SETTINGS")
        try {
            startActivity(tetherIntent)
            return
        } catch (e: Exception) {
            // Some OEMs don't expose this action; fall through to general settings.
        }

        try {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (e: Exception) {
            // If even this fails, there's nothing more we can do without root.
        }
    }
}

@Composable
fun HotspotManagerScreen(
    onOpenHotspotSettings: () -> Unit,
    hotspotInfoProvider: HotspotInfoProvider,
    connectedDevicesReader: ConnectedDevicesReader
) {
    var hotspotInfo by remember { mutableStateOf<HotspotInfoProvider.HotspotInfo?>(null) }
    var devices by remember { mutableStateOf<List<ConnectedDevicesReader.ConnectedDevice>>(emptyList()) }

    // Refresh info every 3 seconds while screen is visible.
    LaunchedEffect(Unit) {
        while (true) {
            hotspotInfo = hotspotInfoProvider.getHotspotInfo()
            devices = connectedDevicesReader.readConnectedDevices()
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Hotspot Manager") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Button(
                onClick = onOpenHotspotSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Hotspot Settings to Toggle")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Android doesn't allow apps to silently turn the hotspot " +
                        "on/off without root. Tap above, then flip the switch on the " +
                        "system screen — this app will pick up the new status automatically.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            InfoCard(title = "Status") {
                val active = hotspotInfo?.isLikelyActive ?: false
                Text(if (active) "Hotspot likely ACTIVE" else "Hotspot likely INACTIVE")
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoCard(title = "Network Name (SSID)") {
                Text(hotspotInfo?.ssid ?: "Loading...")
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoCard(title = "Password") {
                Text(hotspotInfo?.password ?: "Loading...")
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoCard(title = "Local IP Address") {
                Text(hotspotInfo?.localIpAddress ?: "Loading...")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Connected Devices (estimated): ${devices.size}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    "No devices detected, or device list unavailable on this phone.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(device.ipAddress)
                            Text(device.macAddress)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            content()
        }
    }
}
