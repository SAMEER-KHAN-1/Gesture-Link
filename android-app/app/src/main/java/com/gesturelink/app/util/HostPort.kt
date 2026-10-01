package com.gesturelink.app.util

data class HostPort(val host: String, val port: Int)

/**
 * Reads what the user typed in the PC address field: "192.168.1.5" (uses [defaultPort])
 * or "192.168.1.5:9000". Returns null if it isn't a usable address.
 */
fun parseHostPort(input: String, defaultPort: Int): HostPort? {
    val text = input.trim()
    if (text.isEmpty()) return null

    // No colon = plain host. Several colons = a bare IPv6 address, which has no port suffix to split off.
    val colon = text.indexOf(':')
    if (colon == -1 || colon != text.lastIndexOf(':')) return HostPort(text, defaultPort)

    val host = text.substring(0, colon).trim()
    val port = text.substring(colon + 1).trim().toIntOrNull()
    if (host.isEmpty() || port == null || port !in 1..65535) return null
    return HostPort(host, port)
}

/** The inverse of [parseHostPort], for pre-filling the address field: the port is only shown when it isn't the default. */
fun formatHostPort(host: String, port: Int, defaultPort: Int): String =
    if (port == defaultPort) host else "$host:$port"
