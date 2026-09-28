"""File browser: lets the phone navigate the PC's filesystem.

Windows has no single root ("/"), so an empty path means "list drives" - the
app treats that as the top of the tree, same way My Computer / This PC does.
"""

import string
from pathlib import Path

from server.actions import register


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
