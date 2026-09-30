"""Read and write the PC's clipboard as plain text.

Goes straight through the Win32 clipboard API via ctypes, same as the rest of
the actions. On 64-bit Windows the clipboard/global-memory calls hand back
pointer-sized handles, so these bindings get their own DLL instances with
explicit argtypes/restype - setting them on the shared `ctypes.windll` objects
would change how every other module's calls to the same functions behave.
"""

import ctypes
import time
from contextlib import contextmanager
from ctypes import wintypes

from server.actions import register

CF_UNICODETEXT = 13
GMEM_MOVEABLE = 0x0002

# Keeps a huge clipboard (or a huge paste from the phone) from turning into one
# enormous websocket frame.
MAX_TEXT_CHARS = 100_000

_OPEN_ATTEMPTS = 10
_OPEN_RETRY_DELAY_SECONDS = 0.01

_user32 = ctypes.WinDLL("user32", use_last_error=True)
_kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

_user32.OpenClipboard.argtypes = [wintypes.HWND]
_user32.OpenClipboard.restype = wintypes.BOOL
_user32.CloseClipboard.argtypes = []
_user32.CloseClipboard.restype = wintypes.BOOL
_user32.EmptyClipboard.argtypes = []
_user32.EmptyClipboard.restype = wintypes.BOOL
_user32.GetClipboardData.argtypes = [wintypes.UINT]
_user32.GetClipboardData.restype = wintypes.HANDLE
_user32.SetClipboardData.argtypes = [wintypes.UINT, wintypes.HANDLE]
_user32.SetClipboardData.restype = wintypes.HANDLE
_user32.GetDesktopWindow.argtypes = []
_user32.GetDesktopWindow.restype = wintypes.HWND

_kernel32.GlobalAlloc.argtypes = [wintypes.UINT, ctypes.c_size_t]
_kernel32.GlobalAlloc.restype = wintypes.HGLOBAL
_kernel32.GlobalLock.argtypes = [wintypes.HGLOBAL]
_kernel32.GlobalLock.restype = wintypes.LPVOID
_kernel32.GlobalUnlock.argtypes = [wintypes.HGLOBAL]
_kernel32.GlobalUnlock.restype = wintypes.BOOL
_kernel32.GlobalFree.argtypes = [wintypes.HGLOBAL]
_kernel32.GlobalFree.restype = wintypes.HGLOBAL


@contextmanager
def _open_clipboard():
    # Only one process can hold the clipboard open at a time, and clipboard
    # managers/other apps grab it briefly all the time - so retry a few times
    # before giving up. The desktop window is used as the owner because SetClipboardData
    # fails when the clipboard is opened with no owner window at all.
    owner = _user32.GetDesktopWindow()
    for attempt in range(_OPEN_ATTEMPTS):
        if _user32.OpenClipboard(owner):
            break
        if attempt < _OPEN_ATTEMPTS - 1:
            time.sleep(_OPEN_RETRY_DELAY_SECONDS)
    else:
        raise RuntimeError("clipboard is in use by another program, try again")
    try:
        yield
    finally:
        _user32.CloseClipboard()


def _read_text() -> str:
    with _open_clipboard():
        handle = _user32.GetClipboardData(CF_UNICODETEXT)
        if not handle:
            return ""  # empty, or holding something that isn't text (an image, files, ...)
        pointer = _kernel32.GlobalLock(handle)
        if not pointer:
            return ""
        try:
            return ctypes.wstring_at(pointer)
        finally:
            _kernel32.GlobalUnlock(handle)


def _write_text(text: str) -> None:
    data = (text + "\0").encode("utf-16-le")
    handle = _kernel32.GlobalAlloc(GMEM_MOVEABLE, len(data))
    if not handle:
        raise RuntimeError("could not allocate memory for the clipboard")
    pointer = _kernel32.GlobalLock(handle)
    if not pointer:
        _kernel32.GlobalFree(handle)
        raise RuntimeError("could not lock memory for the clipboard")
    ctypes.memmove(pointer, data, len(data))
    _kernel32.GlobalUnlock(handle)

    try:
        with _open_clipboard():
            _user32.EmptyClipboard()
            if not _user32.SetClipboardData(CF_UNICODETEXT, handle):
                raise RuntimeError("could not set the clipboard")
    except Exception:
        # The clipboard only takes ownership of the memory once SetClipboardData
        # succeeds; on any earlier failure it's still ours to free.
        _kernel32.GlobalFree(handle)
        raise


@register("clipboard_get")
async def handle_clipboard_get(params: dict) -> dict:
    text = _read_text()
    truncated = len(text) > MAX_TEXT_CHARS
    return {"text": text[:MAX_TEXT_CHARS], "truncated": truncated}


@register("clipboard_set")
async def handle_clipboard_set(params: dict) -> dict:
    text = params.get("text")
    if not isinstance(text, str):
        raise ValueError("'text' must be a string")
    if len(text) > MAX_TEXT_CHARS:
        raise ValueError(f"text is too long (limit is {MAX_TEXT_CHARS} characters)")
    _write_text(text)
    return {}
