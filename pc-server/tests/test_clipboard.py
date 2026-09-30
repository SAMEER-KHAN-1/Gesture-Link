import asyncio
import ctypes
from unittest.mock import MagicMock

import pytest

from server.actions import clipboard


def run(coro):
    return asyncio.run(coro)


@pytest.fixture
def user32(monkeypatch):
    mock = MagicMock()
    mock.OpenClipboard.return_value = 1
    mock.SetClipboardData.return_value = 0xBEEF
    monkeypatch.setattr(clipboard, "_user32", mock)
    monkeypatch.setattr(clipboard.time, "sleep", lambda _s: None)
    return mock


@pytest.fixture
def kernel32(monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(clipboard, "_kernel32", mock)
    return mock


def test_clipboard_get_returns_the_clipboards_text(user32, kernel32):
    buffer = ctypes.create_unicode_buffer("héllo ✓")  # keep alive while "locked"
    user32.GetClipboardData.return_value = 0x1234
    kernel32.GlobalLock.return_value = ctypes.addressof(buffer)

    result = run(clipboard.handle_clipboard_get({}))

    assert result == {"text": "héllo ✓", "truncated": False}
    user32.GetClipboardData.assert_called_once_with(clipboard.CF_UNICODETEXT)
    kernel32.GlobalUnlock.assert_called_once_with(0x1234)
    user32.CloseClipboard.assert_called_once()


def test_clipboard_get_returns_empty_text_when_clipboard_holds_no_text(user32, kernel32):
    user32.GetClipboardData.return_value = None

    result = run(clipboard.handle_clipboard_get({}))

    assert result == {"text": "", "truncated": False}
    kernel32.GlobalLock.assert_not_called()
    user32.CloseClipboard.assert_called_once()


def test_clipboard_get_truncates_oversized_text(user32, kernel32, monkeypatch):
    monkeypatch.setattr(clipboard, "MAX_TEXT_CHARS", 5)
    buffer = ctypes.create_unicode_buffer("abcdefghij")
    user32.GetClipboardData.return_value = 0x1234
    kernel32.GlobalLock.return_value = ctypes.addressof(buffer)

    result = run(clipboard.handle_clipboard_get({}))

    assert result == {"text": "abcde", "truncated": True}


def test_clipboard_set_writes_utf16_text_and_hands_memory_to_the_clipboard(user32, kernel32):
    backing = ctypes.create_string_buffer(64)  # stands in for the GlobalAlloc'd block
    kernel32.GlobalAlloc.return_value = 0x9999
    kernel32.GlobalLock.return_value = ctypes.addressof(backing)

    result = run(clipboard.handle_clipboard_set({"text": "héllo"}))

    assert result == {}
    expected = "héllo\0".encode("utf-16-le")
    kernel32.GlobalAlloc.assert_called_once_with(clipboard.GMEM_MOVEABLE, len(expected))
    assert backing.raw[: len(expected)] == expected
    user32.EmptyClipboard.assert_called_once()
    user32.SetClipboardData.assert_called_once_with(clipboard.CF_UNICODETEXT, 0x9999)
    kernel32.GlobalFree.assert_not_called()  # the clipboard owns it now
    user32.CloseClipboard.assert_called_once()


def test_clipboard_set_frees_memory_and_closes_clipboard_if_set_fails(user32, kernel32):
    backing = ctypes.create_string_buffer(64)
    kernel32.GlobalAlloc.return_value = 0x9999
    kernel32.GlobalLock.return_value = ctypes.addressof(backing)
    user32.SetClipboardData.return_value = None

    with pytest.raises(RuntimeError):
        run(clipboard.handle_clipboard_set({"text": "x"}))

    kernel32.GlobalFree.assert_called_once_with(0x9999)
    user32.CloseClipboard.assert_called_once()


def test_clipboard_set_rejects_non_string_text_without_touching_the_clipboard(user32, kernel32):
    with pytest.raises(ValueError):
        run(clipboard.handle_clipboard_set({"text": 123}))
    with pytest.raises(ValueError):
        run(clipboard.handle_clipboard_set({}))

    user32.OpenClipboard.assert_not_called()
    kernel32.GlobalAlloc.assert_not_called()


def test_clipboard_set_rejects_oversized_text(user32, kernel32, monkeypatch):
    monkeypatch.setattr(clipboard, "MAX_TEXT_CHARS", 3)

    with pytest.raises(ValueError):
        run(clipboard.handle_clipboard_set({"text": "abcd"}))

    user32.OpenClipboard.assert_not_called()


def test_opening_the_clipboard_retries_when_another_program_holds_it(user32, kernel32):
    user32.OpenClipboard.side_effect = [0, 0, 1]
    user32.GetClipboardData.return_value = None

    run(clipboard.handle_clipboard_get({}))

    assert user32.OpenClipboard.call_count == 3


def test_opening_the_clipboard_gives_up_after_repeated_failures(user32, kernel32):
    user32.OpenClipboard.return_value = 0

    with pytest.raises(RuntimeError, match="in use"):
        run(clipboard.handle_clipboard_get({}))

    assert user32.OpenClipboard.call_count == clipboard._OPEN_ATTEMPTS
    user32.CloseClipboard.assert_not_called()  # never opened, so never closed
