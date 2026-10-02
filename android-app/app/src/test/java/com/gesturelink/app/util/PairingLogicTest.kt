package com.gesturelink.app.util

import com.gesturelink.app.data.PairingInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingLogicTest {

    // --- parsePairingQr ---

    @Test
    fun `a pc qr code gives the address with its port and the token`() {
        val scanned = parsePairingQr("gesturelink://192.168.1.20:9000?token=abc12345")
        assertEquals(PairingQr("192.168.1.20:9000", "abc12345"), scanned)
    }

    @Test
    fun `the default port from the qr code is kept in the address`() {
        val scanned = parsePairingQr("gesturelink://192.168.1.20:8765?token=abc12345")
        assertEquals(PairingQr("192.168.1.20:8765", "abc12345"), scanned)
    }

    @Test
    fun `a qr code without a port gives a bare host`() {
        val scanned = parsePairingQr("gesturelink://192.168.1.20?token=abc12345")
        assertEquals(PairingQr("192.168.1.20", "abc12345"), scanned)
    }

    @Test
    fun `surrounding whitespace in the scanned text is ignored`() {
        val scanned = parsePairingQr("  gesturelink://10.0.0.7:8765?token=ff00ff00\n")
        assertEquals(PairingQr("10.0.0.7:8765", "ff00ff00"), scanned)
    }

    @Test
    fun `the token is found among other query parameters`() {
        val scanned = parsePairingQr("gesturelink://10.0.0.7:8765?v=1&token=ff00ff00&x=y")
        assertEquals("ff00ff00", scanned?.token)
    }

    @Test
    fun `a percent-encoded token is decoded`() {
        val scanned = parsePairingQr("gesturelink://10.0.0.7:8765?token=a%2Bb%20c")
        assertEquals("a+b c", scanned?.token)
    }

    @Test
    fun `a qr code with no token still gives the address`() {
        assertEquals(PairingQr("10.0.0.7:8765", null), parsePairingQr("gesturelink://10.0.0.7:8765"))
        assertEquals(PairingQr("10.0.0.7:8765", null), parsePairingQr("gesturelink://10.0.0.7:8765?token="))
    }

    @Test
    fun `a qr code with no host still gives the token`() {
        assertEquals(PairingQr(null, "abc12345"), parsePairingQr("gesturelink:///?token=abc12345"))
    }

    @Test
    fun `a qr code that carries neither is rejected`() {
        assertNull(parsePairingQr("gesturelink:///"))
        assertNull(parsePairingQr("just some text"))
        assertNull(parsePairingQr(""))
    }

    @Test
    fun `text that is not a valid uri is rejected instead of throwing`() {
        assertNull(parsePairingQr("gesturelink://bad host?token=abc"))
    }

    @Test
    fun `a scanned address can be fed straight into the address parser`() {
        val scanned = parsePairingQr("gesturelink://192.168.1.20:9000?token=abc12345")
        assertEquals(HostPort("192.168.1.20", 9000), parseHostPort(scanned!!.address!!, 8765))
    }

    // --- pairingInfoOnConnect ---

    @Test
    fun `a first pairing has no known mac yet`() {
        val info = pairingInfoOnConnect(null, "192.168.1.20", 8765, "tok")
        assertEquals(PairingInfo("192.168.1.20", 8765, "tok", null), info)
    }

    @Test
    fun `reconnecting to the same pc keeps its mac`() {
        val saved = PairingInfo("192.168.1.20", 8765, "old", "AA:BB:CC:DD:EE:FF")
        val info = pairingInfoOnConnect(saved, "192.168.1.20", 8765, "old")
        assertEquals("AA:BB:CC:DD:EE:FF", info.mac)
    }

    @Test
    fun `the mac survives a new token or port for the same host`() {
        val saved = PairingInfo("192.168.1.20", 8765, "old", "AA:BB:CC:DD:EE:FF")
        val info = pairingInfoOnConnect(saved, "192.168.1.20", 9000, "regenerated")
        assertEquals(PairingInfo("192.168.1.20", 9000, "regenerated", "AA:BB:CC:DD:EE:FF"), info)
    }

    @Test
    fun `pairing with a different pc does not inherit the old mac`() {
        val saved = PairingInfo("192.168.1.20", 8765, "old", "AA:BB:CC:DD:EE:FF")
        val info = pairingInfoOnConnect(saved, "192.168.1.99", 8765, "new")
        assertNull(info.mac)
    }

    @Test
    fun `connecting after a pairing with no mac stays without one`() {
        val saved = PairingInfo("192.168.1.20", 8765, "old", null)
        assertNull(pairingInfoOnConnect(saved, "192.168.1.20", 8765, "old").mac)
    }

    // --- withLearnedMac ---

    @Test
    fun `a newly learned mac is added to the pairing`() {
        val current = PairingInfo("192.168.1.20", 8765, "tok", null)
        assertEquals(current.copy(mac = "AA:BB:CC:DD:EE:FF"), withLearnedMac(current, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a changed mac replaces the old one`() {
        val current = PairingInfo("192.168.1.20", 8765, "tok", "AA:BB:CC:DD:EE:FF")
        assertEquals("11:22:33:44:55:66", withLearnedMac(current, "11:22:33:44:55:66")?.mac)
    }

    @Test
    fun `an unchanged mac needs no save`() {
        val current = PairingInfo("192.168.1.20", 8765, "tok", "AA:BB:CC:DD:EE:FF")
        assertNull(withLearnedMac(current, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `a mac reported after the pc was forgotten is dropped`() {
        assertNull(withLearnedMac(null, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `learning a mac leaves host port and token alone`() {
        val current = PairingInfo("192.168.1.20", 9000, "tok", null)
        val updated = withLearnedMac(current, "AA:BB:CC:DD:EE:FF")!!
        assertEquals(Triple("192.168.1.20", 9000, "tok"), Triple(updated.host, updated.port, updated.token))
    }
}
