# GestureLink PC Server

Small FastAPI service that runs on the Windows PC and does whatever the Android app asks it to.

## Setup

```
cd pc-server
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Run

```
python run.py
```

First run creates a config file (host, port, pairing token) in `%LOCALAPPDATA%\GestureLink\config.json`. Nothing about this file is committed to git; it's your machine's local copy.

Running it opens a tray icon (bottom-right, near the clock) instead of a plain console window. Right-click it to:
- see the current pairing token (needed once, in the Android app)
- open the config folder
- quit the server

Once it's running, check it's alive from any browser on the same network:

```
http://<pc-lan-ip>:8765/health
```

## What's implemented so far

- `/ws` — the WebSocket endpoint the Android app connects to, token-authenticated
- Power: `shutdown`, `restart`, `cancel_shutdown`, `sleep`, `lock`
- Network: `wifi_set`, `bluetooth_set`, `radio_status`
- Apps: `apps_list`, `app_launch`
- System: `system_stats`
- Media: `volume_up`, `volume_down`, `volume_mute_toggle`, `media_play_pause`, `media_next`, `media_previous`
- Files: `list_dir`, `download_file`
- Mouse: `mouse_move`, `mouse_click`, `mouse_scroll`
- Keyboard: `keyboard_type`, `keyboard_key`

Full protocol/action reference lives in [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## Tests

```
pip install -r requirements-dev.txt
pytest
```

Covers the action handlers in `server/actions/` and the websocket message handling in `server/main.py`, including auth (`server/auth.py`). Anything that touches real hardware (mouse/keyboard input, media keys, power, radios) is tested through a mocked `ctypes`/`subprocess` boundary rather than actually run - the tests move no mouse, press no keys, and shut nothing down.

## Packaging to a standalone .exe

For actually running this day-to-day you don't want to open a terminal and `python run.py` every time - PyInstaller bundles it into one `.exe` that runs on its own.

```
pip install -r requirements-build.txt
./build.ps1
```

That's `pyinstaller --clean gesturelink.spec` under the hood. Output lands at `dist/GestureLink.exe` — a windowed app (no console), still shows the same tray icon, still creates its config the same way on first run. Copy it into `shell:startup` (Win+R → `shell:startup`) if you want it to launch automatically when you log in.
