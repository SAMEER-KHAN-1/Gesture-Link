"""Volume and media playback controls.

These are all "media keys" as far as Windows is concerned - the same virtual
keys a physical keyboard's volume/media buttons send - so simulating a key
press/release via the Win32 API covers every app that already responds to
those keys (Spotify, browsers, etc.) with no extra dependency needed.
"""

import ctypes

from server.actions import register

KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002

VK_VOLUME_MUTE = 0xAD
VK_VOLUME_DOWN = 0xAE
VK_VOLUME_UP = 0xAF
VK_MEDIA_NEXT_TRACK = 0xB0
VK_MEDIA_PREV_TRACK = 0xB1
VK_MEDIA_PLAY_PAUSE = 0xB3


def _press_key(vk_code: int) -> None:
    ctypes.windll.user32.keybd_event(vk_code, 0, KEYEVENTF_EXTENDEDKEY, 0)
    ctypes.windll.user32.keybd_event(vk_code, 0, KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP, 0)


@register("volume_up")
async def handle_volume_up(params: dict) -> dict:
    _press_key(VK_VOLUME_UP)
    return {}


@register("volume_down")
async def handle_volume_down(params: dict) -> dict:
    _press_key(VK_VOLUME_DOWN)
    return {}


@register("volume_mute_toggle")
async def handle_volume_mute_toggle(params: dict) -> dict:
    _press_key(VK_VOLUME_MUTE)
    return {}


@register("media_play_pause")
async def handle_media_play_pause(params: dict) -> dict:
    _press_key(VK_MEDIA_PLAY_PAUSE)
    return {}


@register("media_next")
async def handle_media_next(params: dict) -> dict:
    _press_key(VK_MEDIA_NEXT_TRACK)
    return {}


@register("media_previous")
async def handle_media_previous(params: dict) -> dict:
    _press_key(VK_MEDIA_PREV_TRACK)
    return {}
