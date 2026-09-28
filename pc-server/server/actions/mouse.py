"""Remote mouse control: relative move, click, and scroll.

Like the media keys, this drives the classic mouse_event Win32 call rather
than the newer SendInput - simpler for the handful of event types we need,
and consistent with how media.py drives the keyboard equivalent.
"""

import ctypes

from server.actions import register

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800

WHEEL_DELTA = 120  # one "notch" of scroll, per the Win32 docs

_CLICK_FLAGS = {
    "left": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
    "right": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
}


@register("mouse_move")
async def handle_mouse_move(params: dict) -> dict:
    dx = int(params.get("dx", 0))
    dy = int(params.get("dy", 0))
    ctypes.windll.user32.mouse_event(MOUSEEVENTF_MOVE, dx, dy, 0, 0)
    return {}


@register("mouse_click")
async def handle_mouse_click(params: dict) -> dict:
    button = params.get("button", "left")
    flags = _CLICK_FLAGS.get(button)
    if flags is None:
        raise ValueError(f"unknown button '{button}'")

    down_flag, up_flag = flags
    ctypes.windll.user32.mouse_event(down_flag, 0, 0, 0, 0)
    ctypes.windll.user32.mouse_event(up_flag, 0, 0, 0, 0)
    return {}


@register("mouse_scroll")
async def handle_mouse_scroll(params: dict) -> dict:
    ticks = int(params.get("ticks", 0))
    ctypes.windll.user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, ticks * WHEEL_DELTA, 0)
    return {}
