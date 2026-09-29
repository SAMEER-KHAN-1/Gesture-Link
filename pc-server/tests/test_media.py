import asyncio
from unittest.mock import MagicMock

import pytest

from server.actions import media


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def keybd_event(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(media.ctypes.windll.user32, "keybd_event", mock)
    return mock


@pytest.mark.parametrize(
    ("handler", "vk"),
    [
        (media.handle_volume_up, media.VK_VOLUME_UP),
        (media.handle_volume_down, media.VK_VOLUME_DOWN),
        (media.handle_volume_mute_toggle, media.VK_VOLUME_MUTE),
        (media.handle_media_play_pause, media.VK_MEDIA_PLAY_PAUSE),
        (media.handle_media_next, media.VK_MEDIA_NEXT_TRACK),
        (media.handle_media_previous, media.VK_MEDIA_PREV_TRACK),
    ],
)
def test_media_key_presses_down_then_up(keybd_event, handler, vk):
    run(handler({}))
    assert keybd_event.call_args_list == [
        ((vk, 0, media.KEYEVENTF_EXTENDEDKEY, 0),),
        ((vk, 0, media.KEYEVENTF_EXTENDEDKEY | media.KEYEVENTF_KEYUP, 0),),
    ]
