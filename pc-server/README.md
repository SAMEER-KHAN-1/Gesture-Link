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

The server also writes a log to `%LOCALAPPDATA%\GestureLink\logs\gesturelink.log` (phones connecting/disconnecting, rejected tokens, failed commands - never the token itself). It rotates at 512 KB and keeps 3 older copies, so it can't grow without bound. This is the place to look when the packaged exe misbehaves, since it has no console window.

Running it opens a tray icon (bottom-right, near the clock) instead of a plain console window. Right-click it to:
- see the current pairing token (needed once, in the Android app)
- show the pairing QR code
- regenerate the pairing token (asks first - every paired phone stops working until it's paired again with the new token, so use it if the token leaked)
- open the config folder
- toggle "Start with Windows" (adds/removes itself from the `HKCU\...\Run` registry key - no need to manually drop a shortcut in `shell:startup` anymore)
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
- Files: `list_dir`, `download_file`, `upload_file`
- Clipboard: `clipboard_get`, `clipboard_set` (plain text)
- Processes: `process_list`, `process_kill` (refuses critical Windows processes and the server itself)
- Display: `brightness_get`, `brightness_set` (laptop/built-in panels only - external monitors don't expose it)
- Screen: `screenshot` (primary monitor, downscaled JPEG)
- Mouse: `mouse_move`, `mouse_click` (with an optional repeat count for double-clicks), `mouse_button` (press/release, for dragging), `mouse_scroll`
- Keyboard: `keyboard_type`, `keyboard_key`, `keyboard_hotkey` (key combos like Ctrl+C or Alt+Tab)
- Push notifications (unprompted, not tied to a request): a `battery_low` notice broadcast to every connected phone when the battery drops to 15% or below while unplugged

Full protocol/action reference lives in [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## Tests

```
pip install -r requirements-dev.txt
pytest
```

Covers the action handlers in `server/actions/`, the websocket message handling in `server/main.py` (including auth in `server/auth.py`), and the battery-low push logic in `server/battery_watch.py`. Anything that touches real hardware (mouse/keyboard input, media keys, power, radios) is tested through a mocked `ctypes`/`subprocess` boundary rather than actually run - the tests move no mouse, press no keys, and shut nothing down.

## Packaging to a standalone .exe

For actually running this day-to-day you don't want to open a terminal and `python run.py` every time - PyInstaller bundles it into one `.exe` that runs on its own.

```
pip install -r requirements-build.txt
./build.ps1
```

That's `pyinstaller --clean gesturelink.spec` under the hood. Output lands at `dist/GestureLink.exe` — a windowed app (no console), still shows the same tray icon, still creates its config the same way on first run. Run it once and flip "Start with Windows" in the tray menu if you want it to launch automatically when you log in - no need to drop a shortcut in `shell:startup` by hand.
