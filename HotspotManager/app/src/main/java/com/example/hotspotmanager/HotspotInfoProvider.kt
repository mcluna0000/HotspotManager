package com.example.hotspotmanager

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import java.net.NetworkInterface
import java.net.Inet4Address

/**
 * Reads best-effort information about the device's WiFi hotspot.
 *
 * IMPORTANT CAVEATS (read before relying on this in production):
 * - Android has no STABLE PUBLIC API for a regular app to read the hotspot's
 *   SSID/password on modern versions (8+). The methods used here rely on
 *   reflection into hidden WifiManager APIs (getWifiApConfiguration / similar),
 *   which:
 *     a) may not exist on all OEM skins/Android versions,
 *     b) may be blocked by SELinux / hidden-API restrictions on Android 9+,
 *     c) can change or break between Android versions without warning.
 * - All reflective calls are wrapped in try/catch and degrade to "Unknown"
 *   rather than crashing.
 * - This is intentionally "best effort" software, not guaranteed behavior.
 */
class HotspotInfoProvider(private val context: Context) {

    data class HotspotInfo(
        val ssid: String,
        val password: String,
        val localIpAddress: String,
        val isLikelyActive: Boolean
    )

    fun getHotspotInfo(): HotspotInfo {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val (ssid, password) = readApCredentialsReflectively(wifiManager)
        val ip = readApIpAddress()
        val active = ip != null

        return HotspotInfo(
            ssid = ssid ?: "Unavailable on this device/Android version",
            password = password ?: "Unavailable on this device/Android version",
            localIpAddress = ip ?: "Not active / not detected",
            isLikelyActive = active
        )
    }

    /**
     * Attempts to read SSID + password via the hidden getWifiApConfiguration() method.
     * Returns (null, null) if unavailable for any reason.
     */
    private fun readApCredentialsReflectively(wifiManager: WifiManager): Pair<String?, String?> {
        return try {
            val method = wifiManager.javaClass.getMethod("getWifiApConfiguration")
            val config = method.invoke(wifiManager) as? WifiConfiguration
            if (config != null) {
                val ssid = config.SSID
                val pass = config.preSharedKey
                Pair(ssid, pass)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            // Hidden API unavailable, blocked, or signature changed on this OS version.
            Pair(null, null)
        } catch (e: Error) {
            // Some OEMs throw Error instead of Exception for hidden API blocks.
            Pair(null, null)
        }
    }

    /**
     * Looks for an active "ap" / "softap" style interface and returns its IPv4 address.
     * Hotspot interfaces are commonly named ap0, wlan-ap0, or swlan0 depending on chipset/OEM,
     * so we scan all interfaces and pick the first private-range IPv4 that isn't the normal
     * wlan0 / rmnet (mobile data) interface.
     */
    private fun readApIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()

                val looksLikeHotspot = name.contains("ap") || name.contains("softap")
                if (!looksLikeHotspot) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
