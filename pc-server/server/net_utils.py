"""Small network helpers shared by the pairing QR and the Wake-on-LAN support."""

import socket


def get_lan_ip() -> str:
    """Best-effort local LAN IP - the address actually reachable from the phone,
    not 127.0.0.1 or a virtual adapter's address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))  # doesn't actually send anything, just picks a route
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()
