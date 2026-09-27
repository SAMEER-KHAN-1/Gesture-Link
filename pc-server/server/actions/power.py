"""Power control actions: shutdown, restart, sleep, lock.

shutdown/restart go through the built-in `shutdown.exe` so we get the delay +
cancel behaviour for free. sleep/lock go through ctypes since there's no CLI
equivalent worth shelling out for.
"""

import ctypes
import subprocess

from server.actions import register


@register("shutdown")
async def handle_shutdown(params: dict) -> dict:
    delay = int(params.get("delay_seconds", 0))
    subprocess.run(["shutdown", "/s", "/t", str(delay)], check=True)
    return {"message": f"shutting down in {delay}s"}


@register("restart")
async def handle_restart(params: dict) -> dict:
    delay = int(params.get("delay_seconds", 0))
    subprocess.run(["shutdown", "/r", "/t", str(delay)], check=True)
    return {"message": f"restarting in {delay}s"}


@register("cancel_shutdown")
async def handle_cancel_shutdown(params: dict) -> dict:
    """Cancels a pending shutdown/restart that still had a delay left - a safety net
    for when the phone triggers one by accident."""
    subprocess.run(["shutdown", "/a"], check=False)
    return {"message": "cancelled pending shutdown/restart"}


@register("sleep")
async def handle_sleep(params: dict) -> dict:
    # SetSuspendState(hibernate, forceCritical, disableWakeEvent)
    ctypes.windll.powrprof.SetSuspendState(0, 1, 0)
    return {"message": "sleeping"}


@register("lock")
async def handle_lock(params: dict) -> dict:
    ctypes.windll.user32.LockWorkStation()
    return {"message": "locked"}
