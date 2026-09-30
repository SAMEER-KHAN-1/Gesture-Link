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


KEYEVENTF_EXTENDEDKEY = 0x0001

# Held down while another key is pressed, then released - the "Ctrl" in Ctrl+C.
MODIFIER_VKS = {
    "ctrl": 0x11,
    "alt": 0x12,
    "shift": 0x10,
    "win": 0x5B,
}

# Non-alphanumeric keys usable in a hotkey, on top of the ones in VK_BY_NAME.
HOTKEY_EXTRA_VKS = {
    "delete": 0x2E,
    "insert": 0x2D,
    "home": 0x24,
    "end": 0x23,
    "pageup": 0x21,
    "pagedown": 0x22,
    "left": 0x25,
    "up": 0x26,
    "right": 0x27,
    "down": 0x28,
    "printscreen": 0x2C,
}

# These share a virtual-key code with a numpad key unless flagged "extended", so
# without the flag e.g. "home" could arrive as numpad-7 with Num Lock off.
EXTENDED_VKS = {0x5B, 0x2E, 0x2D, 0x24, 0x23, 0x21, 0x22, 0x25, 0x26, 0x27, 0x28, 0x2C}

MAX_HOTKEY_KEYS = 4


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


def _hotkey_vk(name: str) -> int:
    key = name.lower()
    if key in MODIFIER_VKS:
        return MODIFIER_VKS[key]
    if key in VK_BY_NAME:
        return VK_BY_NAME[key]
    if key in HOTKEY_EXTRA_VKS:
        return HOTKEY_EXTRA_VKS[key]
    if len(key) == 1 and key.isascii() and key.isalnum():
        return ord(key.upper())  # VK codes for A-Z and 0-9 are just their ASCII codes
    if key.startswith("f") and key[1:].isdigit() and 1 <= int(key[1:]) <= 12:
        return 0x70 + int(key[1:]) - 1  # VK_F1 is 0x70
    raise ValueError(f"unknown key '{name}'")


def _send_vk(vk_code: int, key_up: bool) -> None:
    flags = KEYEVENTF_EXTENDEDKEY if vk_code in EXTENDED_VKS else 0
    if key_up:
        flags |= KEYEVENTF_KEYUP
    ctypes.windll.user32.keybd_event(vk_code, 0, flags, 0)


@register("keyboard_hotkey")
async def handle_keyboard_hotkey(params: dict) -> dict:
    keys = params.get("keys")
    if not isinstance(keys, list) or not 1 <= len(keys) <= MAX_HOTKEY_KEYS:
        raise ValueError(f"'keys' must be a list of 1 to {MAX_HOTKEY_KEYS} key names")
    if not all(isinstance(key, str) for key in keys):
        raise ValueError("'keys' must only contain strings")

    # Resolve every name before pressing anything, so a typo in the last key can't
    # leave the earlier ones pressed.
    vk_codes = [_hotkey_vk(key) for key in keys]

    pressed = []
    try:
        for vk in vk_codes:
            _send_vk(vk, key_up=False)
            pressed.append(vk)
    finally:
        # Always release, in reverse order, so a modifier can never be left stuck down.
        for vk in reversed(pressed):
            _send_vk(vk, key_up=True)
    return {}


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
