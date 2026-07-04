package com.example.hotspotmanager

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Best-effort reader of connected hotspot clients via /proc/net/arp.
 *
 * CAVEATS:
 * - /proc/net/arp is world-readable on most stock Android builds without root,
 *   but some OEMs (security-hardened skins, some MIUI/OneUI versions, newer
 *   Android with stricter /proc restrictions) may block or empty this file
 *   for non-system apps.
 * - Entries in the ARP table are not perfectly real-time; a device that
 *   disconnected recently may still show up for a short while ("stale" entries).
 * - A MAC of 00:00:00:00:00:00 means the entry hasn't resolved yet and is
 *   filtered out.
 * - This is an estimate, not an authoritative device list. There is no
 *   public non-root API that gives a guaranteed-accurate connected-clients list.
 */
class ConnectedDevicesReader {

    data class ConnectedDevice(
        val ipAddress: String,
        val macAddress: String
    )

    fun readConnectedDevices(): List<ConnectedDevice> {
        val arpFile = File("/proc/net/arp")
        if (!arpFile.exists() || !arpFile.canRead()) {
            return emptyList()
        }

        val devices = mutableListOf<ConnectedDevice>()

        try {
            BufferedReader(FileReader(arpFile)).use { reader ->
                // First line is header: IP address / HW type / Flags / HW address / Mask / Device
                var line = reader.readLine() // skip header
                while (true) {
                    line = reader.readLine() ?: break
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 4) continue

                    val ip = parts[0]
                    val mac = parts[3]

                    val isUnresolved = mac == "00:00:00:00:00:00"
                    if (!isUnresolved && mac.isNotBlank()) {
                        devices.add(ConnectedDevice(ipAddress = ip, macAddress = mac))
                    }
                }
            }
        } catch (e: Exception) {
            // Read failed (permissions, format change, etc.) — return whatever we got so far.
            return devices
        }

        return devices
    }
}
