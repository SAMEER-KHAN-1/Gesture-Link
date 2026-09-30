"""Remote mouse control: relative move, click, press-and-hold (drag), and scroll.

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

MAX_CLICK_COUNT = 3

# Buttons currently held down by a `mouse_button` "down" that hasn't had its "up" yet.
# Tracked so they can be released if the phone disappears mid-drag - otherwise the
# PC would be left with a mouse button stuck down.
_held_buttons: set[str] = set()


def release_held_buttons() -> None:
    for button in list(_held_buttons):
        ctypes.windll.user32.mouse_event(_CLICK_FLAGS[button][1], 0, 0, 0, 0)
    _held_buttons.clear()


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

    # Several clicks are done here in one go rather than as separate commands: a
    # double-click has to land within the OS's double-click interval, which
    # network round-trips between separate messages can't reliably meet.
    count = params.get("count", 1)
    if not isinstance(count, int) or isinstance(count, bool) or not 1 <= count <= MAX_CLICK_COUNT:
        raise ValueError(f"'count' must be an integer from 1 to {MAX_CLICK_COUNT}")

    down_flag, up_flag = flags
    for _ in range(count):
        ctypes.windll.user32.mouse_event(down_flag, 0, 0, 0, 0)
        ctypes.windll.user32.mouse_event(up_flag, 0, 0, 0, 0)
    return {}


@register("mouse_button")
async def handle_mouse_button(params: dict) -> dict:
    """Press or release a button on its own - press, move the mouse, release is a drag."""
    button = params.get("button", "left")
    flags = _CLICK_FLAGS.get(button)
    if flags is None:
        raise ValueError(f"unknown button '{button}'")

    state = params.get("state")
    if state not in ("down", "up"):
        raise ValueError("'state' must be 'down' or 'up'")

    down_flag, up_flag = flags
    if state == "down":
        ctypes.windll.user32.mouse_event(down_flag, 0, 0, 0, 0)
        _held_buttons.add(button)
    else:
        ctypes.windll.user32.mouse_event(up_flag, 0, 0, 0, 0)
        _held_buttons.discard(button)
    return {}


@register("mouse_scroll")
async def handle_mouse_scroll(params: dict) -> dict:
    ticks = int(params.get("ticks", 0))
    ctypes.windll.user32.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, ticks * WHEEL_DELTA, 0)
    return {}
