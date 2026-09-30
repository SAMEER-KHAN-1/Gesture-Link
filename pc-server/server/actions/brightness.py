"""Screen brightness get/set for the PC's built-in display.

Goes through a small PowerShell script over WMI, same approach as the radio
toggles. Windows only exposes brightness that way for laptop panels; on a
desktop with an external monitor the script fails and that error is passed
back to the phone as-is.
"""

import asyncio
import subprocess
from pathlib import Path

from server.actions import register

SCRIPT_PATH = Path(__file__).parent / "scripts" / "brightness.ps1"


def _run_script(*args: str) -> str:
    result = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(SCRIPT_PATH), *args],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "brightness control failed")
    return result.stdout.strip()


@register("brightness_get")
async def handle_brightness_get(params: dict) -> dict:
    output = await asyncio.to_thread(_run_script, "-Action", "Get")
    try:
        return {"brightness": int(output)}
    except ValueError:
        raise RuntimeError(f"unexpected brightness value '{output}'")


@register("brightness_set")
async def handle_brightness_set(params: dict) -> dict:
    level = params.get("level")
    if not isinstance(level, int) or isinstance(level, bool) or not 0 <= level <= 100:
        raise ValueError("'level' must be an integer from 0 to 100")

    await asyncio.to_thread(_run_script, "-Action", "Set", "-Level", str(level))
    return {"brightness": level}
