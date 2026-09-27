"""Connectivity checks and system stats - CPU/RAM/battery for the dashboard."""

import asyncio

import psutil

from server.actions import register


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

    return {
        "cpu_percent": cpu_percent,
        "memory_percent": memory.percent,
        "battery_percent": battery.percent if battery else None,
        "battery_plugged": battery.power_plugged if battery else None,
    }
