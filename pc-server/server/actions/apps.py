"""App listing + launching, built off Start Menu shortcuts (.lnk files) - the
same list you'd see scrolling through the Start Menu. Launching just hands the
.lnk itself to os.startfile so Windows resolves the target, icon, and working
directory the normal way, instead of us trying to re-implement shortcut parsing.
"""

import os
from pathlib import Path

from server.actions import register

START_MENU_DIRS = [
    Path(os.environ.get("PROGRAMDATA", "")) / "Microsoft" / "Windows" / "Start Menu" / "Programs",
    Path(os.environ.get("APPDATA", "")) / "Microsoft" / "Windows" / "Start Menu" / "Programs",
]


def _app_id_for(shortcut: Path, base: Path) -> str:
    return str(shortcut.relative_to(base)).replace("\\", "/")


def _list_apps() -> list[dict]:
    apps = []
    seen_ids = set()
    for base in START_MENU_DIRS:
        if not base.exists():
            continue
        for shortcut in base.rglob("*.lnk"):
            app_id = _app_id_for(shortcut, base)
            if app_id in seen_ids:
                continue
            seen_ids.add(app_id)
            apps.append({"app_id": app_id, "name": shortcut.stem})
    apps.sort(key=lambda a: a["name"].lower())
    return apps


def _resolve_shortcut_path(app_id: str) -> Path:
    for base in START_MENU_DIRS:
        candidate = base / app_id
        if candidate.exists():
            return candidate
    raise FileNotFoundError(f"no app found for app_id '{app_id}'")


@register("apps_list")
async def handle_apps_list(params: dict) -> dict:
    return {"apps": _list_apps()}


@register("app_launch")
async def handle_app_launch(params: dict) -> dict:
    app_id = params.get("app_id")
    if not app_id:
        raise ValueError("app_id is required")

    shortcut_path = _resolve_shortcut_path(app_id)
    os.startfile(str(shortcut_path))  # noqa: S606 - intentional, this is the whole point of the feature
    return {"message": f"launched {app_id}"}
