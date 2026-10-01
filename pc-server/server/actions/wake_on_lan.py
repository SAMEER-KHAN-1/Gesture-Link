"""Wake-on-LAN support: telling the phone this PC's MAC address.

The wake-up itself can't come from here - the PC is off, so the *phone* has to send
the magic packet. It just needs to learn the MAC while it's still connected, which
is all this action does.
"""

import re
import socket

import psutil

from server.actions import register
from server.net_utils import get_lan_ip

_MAC_PATTERN = re.compile(r"^([0-9A-F]{2}:){5}[0-9A-F]{2}$")


def _normalize_mac(raw: str) -> str | None:
    """Windows reports MACs like 'aa-bb-cc-dd-ee-ff'; the phone wants 'AA:BB:CC:DD:EE:FF'."""
    mac = raw.replace("-", ":").upper()
    return mac if _MAC_PATTERN.match(mac) else None


def find_mac_address(lan_ip: str) -> str | None:
    """MAC of the network adapter that owns `lan_ip` - the one the phone is reaching us
    through, which is also the one that has to be listening for the magic packet."""
    for addresses in psutil.net_if_addrs().values():
        if not any(a.family == socket.AF_INET and a.address == lan_ip for a in addresses):
            continue
        for address in addresses:
            if address.family == psutil.AF_LINK:
                return _normalize_mac(address.address)
    return None


@register("mac_address")
async def handle_mac_address(params: dict) -> dict:
    mac = find_mac_address(get_lan_ip())
    if mac is None:
        raise ValueError("couldn't determine this PC's MAC address")
    return {"mac": mac}
