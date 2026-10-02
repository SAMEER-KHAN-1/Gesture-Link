package com.gesturelink.app.util

import com.gesturelink.app.data.PairingInfo
import java.net.URI
import java.net.URLDecoder

/** What a scanned pairing QR code fills in. Either field is null if the code didn't carry it. */
data class PairingQr(val address: String?, val token: String?)

/**
 * Reads the "gesturelink://<ip>:<port>?token=<token>" payload the PC's tray QR code encodes.
 * The address keeps its port exactly as scanned (it goes straight into the address field, which
 * [parseHostPort] reads later). Returns null if it isn't a URI or carries neither a host nor a token.
 */
fun parsePairingQr(raw: String): PairingQr? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null

    val host = uri.host?.takeIf { it.isNotEmpty() }
    val address = host?.let { if (uri.port != -1) "$it:${uri.port}" else it }
    val token = uri.rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            if (name == "token" && value.isNotEmpty()) URLDecoder.decode(value, "UTF-8") else null
        }

    if (address == null && token == null) return null
    return PairingQr(address, token)
}

/**
 * The pairing to remember once a connection succeeds. The stored MAC address is kept only while
 * we're still talking to the same PC (same host) - pairing with a different machine must not inherit
 * the old one's MAC, or Wake-on-LAN would wake the wrong computer.
 */
fun pairingInfoOnConnect(saved: PairingInfo?, host: String, port: Int, token: String): PairingInfo {
    val knownMac = saved?.takeIf { it.host == host }?.mac
    return PairingInfo(host, port, token, knownMac)
}

/**
 * Applies a MAC address the PC just reported. Returns the pairing to save, or null if nothing needs
 * saving (nothing is paired, or the MAC is already what we have).
 */
fun withLearnedMac(current: PairingInfo?, mac: String): PairingInfo? {
    if (current == null || current.mac == mac) return null
    return current.copy(mac = mac)
}

/**
 * What to tell the user when the PC answered the connection check with an error instead of accepting
 * the pairing token. The server's own wording ("invalid pairing token") isn't much help on the pairing
 * screen, so the two cases that can actually happen here get a plain explanation.
 */
fun pairingErrorMessage(serverError: String?): String = when {
    serverError == null -> "The PC refused the connection."
    "invalid pairing token" in serverError ->
        "That pairing token isn't right - check it against the one in the PC's tray menu, or scan the QR code."
    "too many failed attempts" in serverError ->
        "Too many wrong tokens in a row - wait 30 seconds, then try again."
    else -> serverError
}
