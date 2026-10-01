package com.gesturelink.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val WOL_PORT = 9
private val MAC_BYTE = Regex("[0-9a-fA-F]{2}")
private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

/**
 * A Wake-on-LAN "magic packet": 6 bytes of 0xFF followed by the target's MAC address
 * repeated 16 times. Returns null if [mac] isn't six hex bytes separated by ':' or '-'.
 */
fun buildMagicPacket(mac: String): ByteArray? {
    val parts = mac.trim().split(':', '-')
    if (parts.size != 6 || parts.any { !MAC_BYTE.matches(it) }) return null
    val macBytes = parts.map { it.toInt(16).toByte() }

    val packet = ByteArray(6 + 16 * 6)
    for (i in 0 until 6) packet[i] = 0xFF.toByte()
    for (repeat in 0 until 16) {
        for (i in 0 until 6) packet[6 + repeat * 6 + i] = macBytes[i]
    }
    return packet
}

/**
 * The broadcast address of the PC's (assumed /24) home network, e.g. 192.168.1.20 ->
 * 192.168.1.255. Null for anything that isn't a plain IPv4 address.
 */
fun subnetBroadcastAddress(host: String): String? {
    val match = IPV4.matchEntire(host.trim()) ?: return null
    val octets = match.groupValues.drop(1).map { it.toInt() }
    if (octets.any { it > 255 }) return null
    return "${octets[0]}.${octets[1]}.${octets[2]}.255"
}

/**
 * Broadcasts a magic packet for [mac]. Sent to both the limited broadcast address and,
 * when [pcHost] is known, its subnet's broadcast address, since routers/phones differ
 * on which one gets through. Returns whether the packets could be sent - not whether
 * the PC actually woke up, which UDP can't tell us.
 */
suspend fun sendMagicPacket(mac: String, pcHost: String?): Boolean = withContext(Dispatchers.IO) {
    val packet = buildMagicPacket(mac) ?: return@withContext false
    val targets = listOfNotNull("255.255.255.255", pcHost?.let(::subnetBroadcastAddress)).distinct()
    runCatching {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            targets.forEach { target ->
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), WOL_PORT))
            }
        }
    }.isSuccess
}
