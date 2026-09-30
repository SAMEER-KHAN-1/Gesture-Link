"""Connectivity checks and system stats - CPU/RAM/disk/battery/uptime for the dashboard."""

import asyncio
import os
import time

import psutil

from server.actions import register


_BYTES_PER_GB = 1024 ** 3


def _system_drive_root() -> str:
    return os.environ.get("SystemDrive", "C:") + "\\"


def _disk_stats() -> tuple[float | None, float | None]:
    """(used percent, free GB) of the drive Windows is installed on, or (None, None)
    if it can't be read."""
    try:
        usage = psutil.disk_usage(_system_drive_root())
    except OSError:
        return None, None
    return usage.percent, round(usage.free / _BYTES_PER_GB, 1)


@register("ping")
async def handle_ping(params: dict) -> dict:
    return {"message": "pong"}


@register("system_stats")
async def handle_system_stats(params: dict) -> dict:
    # psutil.cpu_percent blocks for the sampling interval, so hand it to a thread
    # rather than stalling the event loop (and every other connected client) for it.
    cpu_percent = await asyncio.to_thread(psutil.cpu_percent, 0.3)
    memory = psutil.virtual_memory()
    battery = psutil.sensors_battery()  # None on desktops with no battery
    disk_percent, disk_free_gb = _disk_stats()

    return {
        "cpu_percent": cpu_percent,
        "memory_percent": memory.percent,
        "disk_percent": disk_percent,
        "disk_free_gb": disk_free_gb,
        "uptime_seconds": int(time.time() - psutil.boot_time()),
        "battery_percent": battery.percent if battery else None,
        "battery_plugged": battery.power_plugged if battery else None,
    }
