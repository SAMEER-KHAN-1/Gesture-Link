"""File browser: lets the phone navigate the PC's filesystem and pull files off it.

Windows has no single root ("/"), so an empty path means "list drives" - the
app treats that as the top of the tree, same way My Computer / This PC does.
"""

import asyncio
import base64
import ctypes
import os
import string
from ctypes import wintypes
from pathlib import Path

from server.actions import register

# Files go over the websocket as base64 inside a JSON message, which balloons
# them by ~33% - keep well clear of typical websocket/JSON message size limits.
# Applies to transfers in both directions (download and upload).
MAX_TRANSFER_BYTES = 15 * 1024 * 1024

# SHFileOperationW constants, for sending things to the Recycle Bin.
FO_DELETE = 0x0003
FOF_SILENT = 0x0004  # no progress dialog
FOF_ALLOWUNDO = 0x0040  # "delete" means "move to the Recycle Bin"
FOF_NOERRORUI = 0x0400  # no error dialogs on the PC's screen
# Deliberately NOT FOF_NOCONFIRMATION: if something can't go to the Recycle Bin (too
# big, or on a drive that has none) Windows then asks before deleting it permanently,
# instead of silently making a "recoverable" delete unrecoverable.


class _SHFILEOPSTRUCTW(ctypes.Structure):
    _fields_ = [
        ("hwnd", wintypes.HWND),
        ("wFunc", wintypes.UINT),
        ("pFrom", ctypes.c_void_p),  # double-null-terminated list of paths
        ("pTo", ctypes.c_void_p),
        ("fFlags", wintypes.WORD),
        ("fAnyOperationsAborted", wintypes.BOOL),
        ("hNameMappings", ctypes.c_void_p),
        ("lpszProgressTitle", ctypes.c_void_p),
    ]


def _require_bare_name(name: str) -> None:
    """A name must be a single file/folder name, not a path - otherwise "../../x" or an
    absolute path could reach outside the folder the caller chose."""
    if not name or Path(name).name != name or name in (".", ".."):
        raise ValueError(f"'{name}' is not a valid name")


def _is_drive_root(path: Path) -> bool:
    return path.parent == path


def _send_to_recycle_bin(path: str) -> None:
    # SHFileOperation wants a list of paths each ending in a null, with an extra null
    # closing the list. create_unicode_buffer supplies the closing one.
    buffer = ctypes.create_unicode_buffer(path + "\0")
    operation = _SHFILEOPSTRUCTW()
    operation.wFunc = FO_DELETE
    operation.pFrom = ctypes.cast(buffer, ctypes.c_void_p)
    operation.fFlags = FOF_ALLOWUNDO | FOF_NOERRORUI | FOF_SILENT

    result = ctypes.windll.shell32.SHFileOperationW(ctypes.byref(operation))
    if result != 0:
        raise OSError(f"couldn't move '{path}' to the Recycle Bin (error {result:#x})")
    if operation.fAnyOperationsAborted:
        raise OSError(f"moving '{path}' to the Recycle Bin was cancelled")


def _list_drives() -> list[dict]:
    drives = []
    for letter in string.ascii_uppercase:
        root = Path(f"{letter}:\\")
        if root.exists():
            drives.append({"name": f"{letter}:", "path": str(root), "is_dir": True, "size": None})
    return drives


def _list_directory(path: Path) -> list[dict]:
    entries = []
    for child in path.iterdir():
        try:
            is_dir = child.is_dir()
            size = None if is_dir else child.stat().st_size
        except OSError:
            # Broken junctions / permission-denied children shouldn't break the whole listing.
            continue
        entries.append({"name": child.name, "path": str(child), "is_dir": is_dir, "size": size})
    entries.sort(key=lambda e: (not e["is_dir"], e["name"].lower()))
    return entries


@register("list_dir")
async def handle_list_dir(params: dict) -> dict:
    raw_path = params.get("path") or ""
    if not raw_path:
        return {"path": "", "entries": _list_drives()}

    path = Path(raw_path)
    if not path.is_dir():
        raise NotADirectoryError(f"'{raw_path}' is not a directory")

    return {"path": str(path), "entries": _list_directory(path)}


@register("download_file")
async def handle_download_file(params: dict) -> dict:
    raw_path = params.get("path")
    if not raw_path:
        raise ValueError("path is required")

    path = Path(raw_path)
    if not path.is_file():
        raise FileNotFoundError(f"'{raw_path}' is not a file")

    size = path.stat().st_size
    if size > MAX_TRANSFER_BYTES:
        limit_mb = MAX_TRANSFER_BYTES // (1024 * 1024)
        raise ValueError(f"'{path.name}' is over the {limit_mb}MB transfer limit")

    # Reading could take a moment for a large-ish file, so don't stall the event
    # loop (and every other connected client) while it happens.
    data = await asyncio.to_thread(path.read_bytes)
    return {"name": path.name, "size": size, "data_base64": base64.b64encode(data).decode("ascii")}


@register("upload_file")
async def handle_upload_file(params: dict) -> dict:
    raw_dir = params.get("dir")
    name = params.get("name")
    data_b64 = params.get("data_base64")
    if not raw_dir:
        raise ValueError("dir is required")
    if not name:
        raise ValueError("name is required")
    if not data_b64:
        raise ValueError("data_base64 is required")

    _require_bare_name(name)  # not a path, or it could write outside the chosen directory

    directory = Path(raw_dir)
    if not directory.is_dir():
        raise NotADirectoryError(f"'{raw_dir}' is not a directory")

    data = base64.b64decode(data_b64)
    if len(data) > MAX_TRANSFER_BYTES:
        limit_mb = MAX_TRANSFER_BYTES // (1024 * 1024)
        raise ValueError(f"'{name}' is over the {limit_mb}MB transfer limit")

    target = directory / name
    await asyncio.to_thread(target.write_bytes, data)
    return {"name": name, "size": len(data)}


@register("create_folder")
async def handle_create_folder(params: dict) -> dict:
    raw_dir = params.get("dir")
    name = params.get("name")
    if not raw_dir:
        raise ValueError("dir is required")
    if not name:
        raise ValueError("name is required")
    _require_bare_name(name)

    directory = Path(raw_dir)
    if not directory.is_dir():
        raise NotADirectoryError(f"'{raw_dir}' is not a directory")

    target = directory / name
    target.mkdir()  # raises FileExistsError if it's already there
    return {"path": str(target)}


@register("rename_path")
async def handle_rename_path(params: dict) -> dict:
    raw_path = params.get("path")
    new_name = params.get("new_name")
    if not raw_path:
        raise ValueError("path is required")
    if not new_name:
        raise ValueError("new_name is required")
    _require_bare_name(new_name)

    path = Path(raw_path)
    if _is_drive_root(path):
        raise ValueError("can't rename a drive")
    if not path.exists():
        raise FileNotFoundError(f"'{raw_path}' doesn't exist")

    target = path.with_name(new_name)
    # Checked explicitly: on some platforms a rename silently overwrites an existing file.
    if target.exists():
        raise FileExistsError(f"'{new_name}' already exists in that folder")

    path.rename(target)
    return {"path": str(target)}


@register("delete_path")
async def handle_delete_path(params: dict) -> dict:
    """Moves a file or folder to the Recycle Bin - never a permanent delete."""
    raw_path = params.get("path")
    if not raw_path:
        raise ValueError("path is required")

    path = Path(raw_path)
    if _is_drive_root(path):
        raise ValueError("can't delete a drive")
    if not path.exists():
        raise FileNotFoundError(f"'{raw_path}' doesn't exist")

    await asyncio.to_thread(_send_to_recycle_bin, os.path.abspath(path))
    return {}
