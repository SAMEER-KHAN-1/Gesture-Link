import asyncio
from unittest.mock import MagicMock

import pytest

from server.actions import keyboard


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def keybd_event(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(keyboard.ctypes.windll.user32, "keybd_event", mock)
    return mock


@pytest.fixture
def send_input(monkeypatch):
    calls = []

    def fake_send_input(n_inputs, input_ptr, cb_size):
        inp = input_ptr._obj  # the INPUT struct ctypes.byref() wraps
        calls.append({"wVk": inp.ki.wVk, "wScan": inp.ki.wScan, "flags": inp.ki.dwFlags})
        return 1

    monkeypatch.setattr(keyboard.ctypes.windll.user32, "SendInput", fake_send_input)
    return calls


def test_keyboard_key_rejects_unknown_key_without_touching_hardware(keybd_event):
    with pytest.raises(ValueError):
        run(keyboard.handle_keyboard_key({"key": "bogus"}))
    keybd_event.assert_not_called()


def test_keyboard_key_presses_named_key_down_then_up(keybd_event):
    run(keyboard.handle_keyboard_key({"key": "enter"}))
    vk = keyboard.VK_BY_NAME["enter"]
    assert keybd_event.call_args_list == [
        ((vk, 0, 0, 0),),
        ((vk, 0, keyboard.KEYEVENTF_KEYUP, 0),),
    ]


def test_keyboard_type_sends_a_unicode_down_and_up_per_character(send_input):
    run(keyboard.handle_keyboard_type({"text": "hi"}))

    assert len(send_input) == 4
    assert [c["wScan"] for c in send_input] == [ord("h"), ord("h"), ord("i"), ord("i")]
    assert send_input[0]["flags"] == keyboard.KEYEVENTF_UNICODE
    assert send_input[1]["flags"] == keyboard.KEYEVENTF_UNICODE | keyboard.KEYEVENTF_KEYUP
    assert all(c["wVk"] == 0 for c in send_input)


def test_keyboard_type_treats_newline_as_an_enter_keypress(send_input, keybd_event):
    run(keyboard.handle_keyboard_type({"text": "a\nb"}))

    # 'a' and 'b' go through SendInput (2 chars * down/up = 4 calls); '\n' goes
    # through the VK-based enter press instead, not through SendInput at all.
    assert len(send_input) == 4
    assert [c["wScan"] for c in send_input] == [ord("a"), ord("a"), ord("b"), ord("b")]

    vk = keyboard.VK_BY_NAME["enter"]
    assert keybd_event.call_args_list == [
        ((vk, 0, 0, 0),),
        ((vk, 0, keyboard.KEYEVENTF_KEYUP, 0),),
    ]


def test_keyboard_type_with_empty_text_does_nothing(send_input, keybd_event):
    run(keyboard.handle_keyboard_type({}))
    assert send_input == []
    keybd_event.assert_not_called()
