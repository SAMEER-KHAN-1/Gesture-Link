"""Wifi / Bluetooth radio toggling.

Windows doesn't expose a simple CLI flag that flips both of these reliably, so
we drive the WinRT Radio API (the same one Action Center's quick toggles use)
through a small PowerShell script instead of pulling in extra Python packages.
"""

import json
import subprocess
from pathlib import Path

from server.actions import register

SCRIPT_PATH = Path(__file__).parent / "scripts" / "toggle_radio.ps1"
STATUS_SCRIPT_PATH = Path(__file__).parent / "scripts" / "radio_status.ps1"


def _set_radio_state(kind: str, enabled: bool) -> dict:
    state = "On" if enabled else "Off"
    result = subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", str(SCRIPT_PATH),
            "-Kind", kind,
            "-State", state,
        ],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"failed to set {kind} to {state}")
    return {"kind": kind, "enabled": enabled}


@register("wifi_set")
async def handle_wifi_set(params: dict) -> dict:
    enabled = bool(params.get("enabled", True))
    return _set_radio_state("WiFi", enabled)


@register("bluetooth_set")
async def handle_bluetooth_set(params: dict) -> dict:
    enabled = bool(params.get("enabled", True))
    return _set_radio_state("Bluetooth", enabled)


@register("radio_status")
async def handle_radio_status(params: dict) -> dict:
    result = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(STATUS_SCRIPT_PATH)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "failed to read radio status")

    status = json.loads(result.stdout)
    return {
        "wifi_enabled": status.get("WiFi") == "On",
        "bluetooth_enabled": status.get("Bluetooth") == "On",
    }
