import asyncio
import socket
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

from server.actions import wake_on_lan

AF_LINK = -1  # stand-in for psutil.AF_LINK, which differs per platform


def run(coro):
    return asyncio.run(coro)


def _addr(family, address):
    return SimpleNamespace(family=family, address=address)


@pytest.fixture
def adapters(monkeypatch):
    """Two adapters: the real LAN one (192.168.1.20) and a virtual one."""
    mock = MagicMock()
    mock.AF_LINK = AF_LINK
    mock.net_if_addrs.return_value = {
        "vEthernet (WSL)": [_addr(AF_LINK, "00-15-5D-00-00-01"), _addr(socket.AF_INET, "172.20.0.1")],
        "Ethernet": [_addr(AF_LINK, "a4-bb-6d-12-34-56"), _addr(socket.AF_INET, "192.168.1.20")],
    }
    monkeypatch.setattr(wake_on_lan, "psutil", mock)
    monkeypatch.setattr(wake_on_lan, "get_lan_ip", lambda: "192.168.1.20")
    return mock


def test_mac_address_comes_from_the_adapter_that_owns_the_lan_ip(adapters):
    assert run(wake_on_lan.handle_mac_address({})) == {"mac": "A4:BB:6D:12:34:56"}


def test_mac_address_ignores_other_adapters(adapters, monkeypatch):
    monkeypatch.setattr(wake_on_lan, "get_lan_ip", lambda: "172.20.0.1")

    assert run(wake_on_lan.handle_mac_address({})) == {"mac": "00:15:5D:00:00:01"}


def test_mac_address_errors_when_no_adapter_has_the_lan_ip(adapters, monkeypatch):
    monkeypatch.setattr(wake_on_lan, "get_lan_ip", lambda: "10.9.9.9")

    with pytest.raises(ValueError, match="MAC address"):
        run(wake_on_lan.handle_mac_address({}))


def test_mac_address_errors_when_the_adapter_reports_no_usable_mac(adapters):
    adapters.net_if_addrs.return_value = {
        "Ethernet": [_addr(AF_LINK, "not-a-mac"), _addr(socket.AF_INET, "192.168.1.20")],
    }

    with pytest.raises(ValueError, match="MAC address"):
        run(wake_on_lan.handle_mac_address({}))


def test_normalize_accepts_dashes_and_colons_in_any_case():
    assert wake_on_lan._normalize_mac("aa-bb-cc-dd-ee-ff") == "AA:BB:CC:DD:EE:FF"
    assert wake_on_lan._normalize_mac("aa:bb:cc:dd:ee:ff") == "AA:BB:CC:DD:EE:FF"


@pytest.mark.parametrize("bad", ["", "aa-bb-cc", "zz-bb-cc-dd-ee-ff", "aa-bb-cc-dd-ee-ff-00"])
def test_normalize_rejects_malformed_macs(bad):
    assert wake_on_lan._normalize_mac(bad) is None
