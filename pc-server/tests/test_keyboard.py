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


def _hotkey_events(keybd_event):
    return [call.args for call in keybd_event.call_args_list]


def test_hotkey_presses_keys_in_order_then_releases_them_in_reverse(keybd_event):
    run(keyboard.handle_keyboard_hotkey({"keys": ["ctrl", "shift", "escape"]}))

    ctrl, shift, esc = 0x11, 0x10, 0x1B
    up = keyboard.KEYEVENTF_KEYUP
    assert _hotkey_events(keybd_event) == [
        (ctrl, 0, 0, 0),
        (shift, 0, 0, 0),
        (esc, 0, 0, 0),
        (esc, 0, up, 0),
        (shift, 0, up, 0),
        (ctrl, 0, up, 0),
    ]


def test_hotkey_letters_and_digits_map_to_their_ascii_virtual_key_codes(keybd_event):
    run(keyboard.handle_keyboard_hotkey({"keys": ["Ctrl", "c"]}))
    run(keyboard.handle_keyboard_hotkey({"keys": ["alt", "4"]}))

    downs = [args[0] for args in _hotkey_events(keybd_event) if args[2] == 0]
    assert downs == [0x11, ord("C"), 0x12, ord("4")]


def test_hotkey_function_keys_map_to_the_f_key_range(keybd_event):
    run(keyboard.handle_keyboard_hotkey({"keys": ["f1"]}))
    run(keyboard.handle_keyboard_hotkey({"keys": ["F12"]}))

    downs = [args[0] for args in _hotkey_events(keybd_event) if args[2] == 0]
    assert downs == [0x70, 0x7B]


def test_hotkey_flags_extended_keys_like_arrows_and_win(keybd_event):
    run(keyboard.handle_keyboard_hotkey({"keys": ["win", "left"]}))

    ext = keyboard.KEYEVENTF_EXTENDEDKEY
    up = keyboard.KEYEVENTF_KEYUP
    assert _hotkey_events(keybd_event) == [
        (0x5B, 0, ext, 0),
        (0x25, 0, ext, 0),
        (0x25, 0, ext | up, 0),
        (0x5B, 0, ext | up, 0),
    ]


def test_hotkey_does_not_flag_ordinary_keys_as_extended(keybd_event):
    run(keyboard.handle_keyboard_hotkey({"keys": ["alt", "tab"]}))

    assert all(not args[2] & keyboard.KEYEVENTF_EXTENDEDKEY for args in _hotkey_events(keybd_event))


@pytest.mark.parametrize(
    "keys",
    [
        None,
        "ctrl",
        [],
        ["ctrl", "shift", "alt", "win", "a"],  # over the length limit
        ["ctrl", 5],
        ["ctrl", "bogus"],
        ["f13"],
        ["é"],
    ],
)
def test_hotkey_rejects_bad_key_lists_without_pressing_anything(keybd_event, keys):
    with pytest.raises(ValueError):
        run(keyboard.handle_keyboard_hotkey({"keys": keys}))

    keybd_event.assert_not_called()


def test_hotkey_still_releases_everything_if_a_key_press_fails(monkeypatch):
    calls = []

    def flaky_keybd_event(vk, scan, flags, extra):
        calls.append((vk, flags))
        if vk == ord("C") and flags == 0:
            raise OSError("input blocked")

    monkeypatch.setattr(keyboard.ctypes.windll.user32, "keybd_event", flaky_keybd_event)

    with pytest.raises(OSError):
        run(keyboard.handle_keyboard_hotkey({"keys": ["ctrl", "c"]}))

    # ctrl went down, "c" failed, so only ctrl is released - and it is released.
    assert calls == [(0x11, 0), (ord("C"), 0), (0x11, keyboard.KEYEVENTF_KEYUP)]
