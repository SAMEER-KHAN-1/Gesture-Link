"""Watches the battery and pushes a one-shot low-battery notice to connected
phones the moment it crosses the threshold, instead of on every stats poll.
"""

import asyncio

import psutil

from server.protocol import PushMessage
from server.ws_manager import manager

LOW_BATTERY_PERCENT = 15
CHECK_INTERVAL_SECONDS = 60

_was_low = False


async def battery_watch_loop() -> None:
    while True:
        await _check_once()
        await asyncio.sleep(CHECK_INTERVAL_SECONDS)


async def _check_once() -> None:
    global _was_low
    is_low = _is_battery_low()
    if is_low and not _was_low:
        battery = psutil.sensors_battery()
        await manager.broadcast(PushMessage(push="battery_low", data={"battery_percent": battery.percent}))
    _was_low = is_low


def _is_battery_low() -> bool:
    battery = psutil.sensors_battery()
    if battery is None:
        return False  # desktops with no battery
    return not battery.power_plugged and battery.percent <= LOW_BATTERY_PERCENT
