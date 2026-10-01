package com.gesturelink.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanTest {

    private val macBytes = byteArrayOf(0xA4.toByte(), 0xBB.toByte(), 0x6D, 0x12, 0x34, 0x56)

    @Test
    fun `magic packet is 6 bytes of ff then the mac repeated 16 times`() {
        val packet = buildMagicPacket("A4:BB:6D:12:34:56")!!

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (repeat in 0 until 16) {
            assertArrayEquals(macBytes, packet.copyOfRange(6 + repeat * 6, 12 + repeat * 6))
        }
    }

    @Test
    fun `dashes lowercase and surrounding whitespace are accepted`() {
        assertArrayEquals(buildMagicPacket("A4:BB:6D:12:34:56"), buildMagicPacket(" a4-bb-6d-12-34-56 "))
    }

    @Test
    fun `malformed macs are rejected`() {
        assertNull(buildMagicPacket(""))
        assertNull(buildMagicPacket("A4:BB:6D:12:34"))
        assertNull(buildMagicPacket("A4:BB:6D:12:34:56:78"))
        assertNull(buildMagicPacket("A4:BB:6D:12:34:ZZ"))
        assertNull(buildMagicPacket("A4:BB:6D:12:34:+5"))
        assertNull(buildMagicPacket("A4BB6D123456"))
    }

    @Test
    fun `a valid mac is not rejected`() {
        assertNotNull(buildMagicPacket("00:00:00:00:00:00"))
    }

    @Test
    fun `subnet broadcast replaces the last octet with 255`() {
        assertEquals("192.168.1.255", subnetBroadcastAddress("192.168.1.20"))
        assertEquals("10.0.0.255", subnetBroadcastAddress(" 10.0.0.7 "))
    }

    @Test
    fun `subnet broadcast is null for anything but a plain ipv4 address`() {
        assertNull(subnetBroadcastAddress("my-pc.local"))
        assertNull(subnetBroadcastAddress("fe80::1"))
        assertNull(subnetBroadcastAddress("192.168.1"))
        assertNull(subnetBroadcastAddress("192.168.1.300"))
        assertNull(subnetBroadcastAddress(""))
    }
}
