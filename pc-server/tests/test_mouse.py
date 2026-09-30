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


@pytest.fixture(autouse=True)
def no_buttons_held():
    mouse._held_buttons.clear()
    yield
    mouse._held_buttons.clear()


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


def test_mouse_click_with_a_count_repeats_the_click(mouse_event):
    run(mouse.handle_mouse_click({"button": "left", "count": 2}))
    assert mouse_event.call_args_list == [
        ((mouse.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0),),
        ((mouse.MOUSEEVENTF_LEFTUP, 0, 0, 0, 0),),
        ((mouse.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0),),
        ((mouse.MOUSEEVENTF_LEFTUP, 0, 0, 0, 0),),
    ]


@pytest.mark.parametrize("bad_count", [0, 4, -1, 1.5, "2", True, None])
def test_mouse_click_rejects_a_bad_count_without_touching_hardware(mouse_event, bad_count):
    with pytest.raises(ValueError):
        run(mouse.handle_mouse_click({"button": "left", "count": bad_count}))
    mouse_event.assert_not_called()


def test_mouse_button_down_presses_without_releasing(mouse_event):
    run(mouse.handle_mouse_button({"button": "left", "state": "down"}))
    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)


def test_mouse_button_up_releases(mouse_event):
    run(mouse.handle_mouse_button({"button": "right", "state": "up"}))
    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0)


def test_mouse_button_defaults_to_the_left_button(mouse_event):
    run(mouse.handle_mouse_button({"state": "down"}))
    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)


@pytest.mark.parametrize(
    "params",
    [
        {"button": "middle", "state": "down"},
        {"button": "left", "state": "sideways"},
        {"button": "left"},
    ],
)
def test_mouse_button_rejects_bad_params_without_touching_hardware(mouse_event, params):
    with pytest.raises(ValueError):
        run(mouse.handle_mouse_button(params))
    mouse_event.assert_not_called()


def test_release_held_buttons_lets_go_of_a_button_left_down(mouse_event):
    run(mouse.handle_mouse_button({"button": "left", "state": "down"}))
    mouse_event.reset_mock()

    mouse.release_held_buttons()

    mouse_event.assert_called_once_with(mouse.MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)
    assert mouse._held_buttons == set()


def test_release_held_buttons_does_nothing_after_a_normal_release(mouse_event):
    run(mouse.handle_mouse_button({"button": "left", "state": "down"}))
    run(mouse.handle_mouse_button({"button": "left", "state": "up"}))
    mouse_event.reset_mock()

    mouse.release_held_buttons()

    mouse_event.assert_not_called()
