"""Remote keyboard input: type arbitrary text, or press a single named key.

Typing goes through SendInput with KEYEVENTF_UNICODE, which injects the exact
character regardless of the PC's keyboard layout - unlike virtual-key codes,
which are layout-dependent. Named keys (enter, backspace, ...) don't have a
printable character, so those go through the same VK-based key press used for
media keys instead.
"""

import ctypes
from ctypes import wintypes

from server.actions import register

KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_KEYUP = 0x0002
INPUT_KEYBOARD = 1

# ULONG_PTR isn't in ctypes.wintypes, and getting its size wrong silently
# corrupts SendInput's calls on 64-bit Windows - it has to be pointer-sized.
ULONG_PTR = ctypes.c_size_t

VK_BY_NAME = {
    "enter": 0x0D,
    "backspace": 0x08,
    "tab": 0x09,
    "escape": 0x1B,
    "space": 0x20,
}


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wintypes.DWORD), ("ki", KEYBDINPUT)]


def _send_unicode_char(char: str, flags: int) -> None:
    inp = INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(0, ord(char), KEYEVENTF_UNICODE | flags, 0, 0))
    ctypes.windll.user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))


def _type_char(char: str) -> None:
    _send_unicode_char(char, 0)
    _send_unicode_char(char, KEYEVENTF_KEYUP)


def _press_vk(vk_code: int) -> None:
    ctypes.windll.user32.keybd_event(vk_code, 0, 0, 0)
    ctypes.windll.user32.keybd_event(vk_code, 0, KEYEVENTF_KEYUP, 0)


@register("keyboard_type")
async def handle_keyboard_type(params: dict) -> dict:
    text = params.get("text", "")
    for char in text:
        if char == "\n":
            _press_vk(VK_BY_NAME["enter"])
        else:
            _type_char(char)
    return {}


@register("keyboard_key")
async def handle_keyboard_key(params: dict) -> dict:
    key = params.get("key", "")
    vk_code = VK_BY_NAME.get(key)
    if vk_code is None:
        raise ValueError(f"unknown key '{key}'")
    _press_vk(vk_code)
    return {}
