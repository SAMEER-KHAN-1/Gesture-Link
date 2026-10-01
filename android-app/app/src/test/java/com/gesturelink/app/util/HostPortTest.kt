package com.gesturelink.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostPortTest {

    @Test
    fun `a plain host uses the default port`() {
        assertEquals(HostPort("192.168.1.5", 8765), parseHostPort("192.168.1.5", 8765))
    }

    @Test
    fun `an explicit port overrides the default`() {
        assertEquals(HostPort("192.168.1.5", 9000), parseHostPort("192.168.1.5:9000", 8765))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(HostPort("192.168.1.5", 9000), parseHostPort("  192.168.1.5:9000 ", 8765))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(parseHostPort("", 8765))
        assertNull(parseHostPort("   ", 8765))
    }

    @Test
    fun `a missing host or port number is rejected`() {
        assertNull(parseHostPort(":9000", 8765))
        assertNull(parseHostPort("192.168.1.5:", 8765))
        assertNull(parseHostPort("192.168.1.5:abc", 8765))
    }

    @Test
    fun `ports outside 1 to 65535 are rejected`() {
        assertNull(parseHostPort("192.168.1.5:0", 8765))
        assertNull(parseHostPort("192.168.1.5:65536", 8765))
        assertEquals(HostPort("192.168.1.5", 65535), parseHostPort("192.168.1.5:65535", 8765))
    }

    @Test
    fun `a bare ipv6 address is kept whole with the default port`() {
        assertEquals(HostPort("fe80::1", 8765), parseHostPort("fe80::1", 8765))
    }

    @Test
    fun `the default port is left out when formatting`() {
        assertEquals("192.168.1.5", formatHostPort("192.168.1.5", 8765, 8765))
    }

    @Test
    fun `a non-default port is included when formatting`() {
        assertEquals("192.168.1.5:9000", formatHostPort("192.168.1.5", 9000, 8765))
    }

    @Test
    fun `formatting then parsing gives back the same host and port`() {
        val formatted = formatHostPort("10.0.0.7", 4000, 8765)
        assertEquals(HostPort("10.0.0.7", 4000), parseHostPort(formatted, 8765))
    }
}
