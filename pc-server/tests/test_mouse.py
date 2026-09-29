import asyncio
from unittest.mock import MagicMock

import pytest

from server.actions import mouse


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def mouse_event(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(mouse.ctypes.windll.user32, "mouse_event", mock)
    return mock


def test_mouse_move_sends_relative_move(mouse_event):
    run(mouse.handle_mouse_move({"dx": 12, "dy": -7}))
    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_MOVE, 12, -7, 0, 0)


def test_mouse_move_defaults_to_zero(mouse_event):
    run(mouse.handle_mouse_move({}))
    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_MOVE, 0, 0, 0, 0)


def test_mouse_click_left_sends_down_then_up(mouse_event):
    run(mouse.handle_mouse_click({"button": "left"}))
    assert mouse_event.call_args_list == [
        ((mouse.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0),),
        ((mouse.MOUSEEVENTF_LEFTUP, 0, 0, 0, 0),),
    ]


def test_mouse_click_right_sends_down_then_up(mouse_event):
    run(mouse.handle_mouse_click({"button": "right"}))
    assert mouse_event.call_args_list == [
        ((mouse.MOUSEEVENTF_RIGHTDOWN, 0, 0, 0, 0),),
        ((mouse.MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0),),
    ]


def test_mouse_click_rejects_unknown_button_without_touching_hardware(mouse_event):
    with pytest.raises(ValueError):
        run(mouse.handle_mouse_click({"button": "middle"}))
    mouse_event.assert_not_called()


def test_mouse_scroll_multiplies_ticks_by_wheel_delta(mouse_event):
    run(mouse.handle_mouse_scroll({"ticks": 2}))
    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_WHEEL, 0, 0, 2 * mouse.WHEEL_DELTA, 0)
