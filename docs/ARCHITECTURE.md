# Architecture

## Overview

GestureLink has two halves that talk to each other over a plain local-network WebSocket connection — no cloud, no external server.

```
 ┌─────────────────────┐        LAN Wifi         ┌──────────────────────┐
 │   Android App        │  <──── WebSocket ────>  │   PC Tray Service      │
 │  (Kotlin + Compose)   │      (JSON messages)    │  (Python + FastAPI)    │
 └─────────────────────┘                          └──────────────────────┘
                                                              │
                                                  ctypes / PowerShell
                                                              │
                                                     Windows OS actions
                                              (power, radios, processes, apps)
```

- The **PC service** can start with Windows (opt-in, toggled from its own tray menu), sits in the system tray, and hosts a WebSocket server on port 8765 by default (`host` and `port` in the server's `config.json`; the host defaults to `0.0.0.0`, i.e. every network interface). The pairing QR code carries the LAN IP and the port.
- On first run it generates a random **pairing token** (8 hex characters) and shows it in the tray menu and as a QR code, so only a phone that has seen the token can issue commands.
- The **Android app** stores the PC's IP, port, token and MAC address after the first successful pairing and reconnects automatically after that.
- Every command from phone → PC is a small JSON object; every reply PC → phone is a small JSON object. See the protocol below.

## How the pieces fit

### PC server (`pc-server/server/`)

| module | role |
|--------|------|
| `tray.py` | the entry point's UI: tray icon and menu (token, port, show pairing QR, regenerate token, open config folder, start with Windows, quit); starts uvicorn on a background thread |
| `main.py` | FastAPI app: `/health` and the `/ws` endpoint. `handle_message` is the one place a request is parsed, authenticated and dispatched |
| `protocol.py` | pydantic models for requests, responses and pushes |
| `auth.py` | pairing-token check and the per-address lockout |
| `actions/` | one module per feature area; each handler registers itself under its action name with `@register(...)` in `actions/__init__.py`, so adding an action never touches `main.py` |
| `ws_manager.py` | the set of connected phones; `broadcast` is how the server pushes unprompted messages |
| `battery_watch.py` | background loop (every 60 s) that pushes `battery_low` |
| `config.py` | `config.json` and the pairing token, both in `%LOCALAPPDATA%\GestureLink\` (outside the repo) |
| `logging_setup.py` | rotating log file in `%LOCALAPPDATA%\GestureLink\logs\` (512 KB x 3 backups); the token is never written to it |
| `startup.py` | the "Start with Windows" toggle (the user's `Run` registry key) |
| `pairing_qr.py`, `net_utils.py` | the `gesturelink://<ip>:<port>?token=<token>` QR payload, and the LAN IP lookup (the MAC lookup is in `actions/wake_on_lan.py`) |

Windows is driven through `ctypes` (input, media keys, sleep/lock, clipboard, Recycle Bin, screen capture), `shutdown.exe` (shutdown/restart/cancel) and a few bundled PowerShell scripts (`actions/scripts/`: radios, brightness) - not through pywin32.

### Android app (`android-app/app/src/main/java/com/gesturelink/app/`)

| package | role |
|---------|------|
| `network/` | `Protocol.kt` mirrors `protocol.py`; `GestureLinkClient` wraps an OkHttp WebSocket: `sendCommand` suspends until the reply with the same `id` arrives, pushes (no `id`) go to a callback, and in-flight requests fail instead of hanging when the connection drops |
| `data/` | `PairingStore` (the paired PC) and `SettingsStore` (sensitivity, theme, bookmarks) in separate preference files, so "Forget this PC" doesn't reset settings; file save/read and clipboard helpers |
| `ui/` | one Compose screen per feature |
| `util/` | pure, unit-tested helpers (address parsing, pairing logic, path and format helpers, Wake-on-LAN packet, notification log) |
| `MainActivity.kt` | in-memory screen state machine; owns the client and turns screen callbacks into commands |

### Lifecycle of a connection

1. **Pair.** The phone gets the PC's IP, port and token by QR scan or by typing them, opens `ws://<ip>:<port>/ws`, sends a `ping` to check the token, and saves them once the PC accepts it (a rejected token returns to the pairing screen with an explanation).
2. **Use.** Every command carries the token. The server answers each request with a response carrying the same `id`. While the dashboard is open the phone also polls `ping` and `system_stats` every 5 seconds.
3. **Push.** The server may send a push at any time to every connected phone.
4. **Drop.** If the connection is lost (not a deliberate disconnect), the phone returns to the pairing screen with an explanation. On the PC, when the last phone disconnects any mouse button still held for a drag is released.
5. **Reconnect.** On the next launch the phone reconnects to the saved PC automatically. If the PC is off, the phone can send a Wake-on-LAN packet using the MAC address it learned while connected.

## Why WebSocket over plain HTTP

Commands are one-off, but the PC needs to be able to push things back to the phone unprompted (so far: "battery low"), and a persistent socket makes the connection status ("PC online/offline") trivial to show in the app without polling.

## Security model (v1)

- LAN-only by design, not by enforcement. The server listens on every interface (`0.0.0.0`) and is not meant to be exposed to the internet: don't port-forward it. Windows Firewall's "private networks" prompt is the practical boundary.
- Token-based auth: every message must include the token. There is no separate login step or timeout: an unauthenticated socket may stay open, but each message it sends is answered with an error and nothing is executed.
- The token is generated locally on the PC (not hardcoded) and stored in `config.json` in the local app data folder, outside the repo. It is shown in the tray menu and as a QR code for pairing, printed to the console on startup, and never written to the log file. "Regenerate pairing token" in the tray invalidates the old one.
- Brute-force guard: 5 wrong tokens from the same address locks that address out for 30 seconds before it can try again.
- Destructive file actions are limited on purpose: deletes go to the Recycle Bin, renames never overwrite (uploads do replace a file of the same name), drive roots are refused, and `process_kill` refuses the server itself and critical Windows processes.
- Traffic is plain `ws://`, not encrypted, so anyone who can sniff the local network can read the token. That is part of the "trusted home network" trade-off below.
- No remote/internet relay in v1. If remote control off-LAN is wanted later, that's a deliberate opt-in addition (e.g. via the user's own VPN), not a default.

This is intentionally a "trusted home network" threat model, not a hardened public-internet service — it's a personal remote for your own devices.

## Command protocol (v1)

All messages are JSON over the single WebSocket connection.

### Phone → PC (request)

```json
{
  "id": "3f9b2e",
  "token": "<pairing token>",
  "action": "shutdown",
  "params": {}
}
```

- `id`: random string set by the client, echoed back in the response so replies can be matched to requests.
- `token`: pairing token, required on every message in v1 (simplest to implement first; can move to "auth once per connection" later).
- `action`: one of the actions below.
- `params`: action-specific arguments (empty object if none).

### PC → Phone (response)

```json
{
  "id": "3f9b2e",
  "ok": true,
  "action": "shutdown",
  "result": {},
  "error": null
}
```

- `ok`: whether the action succeeded.
- `error`: human-readable message when `ok` is `false`, otherwise `null`.

### PC → Phone (push, unprompted)

Not every PC → phone message is a reply. A few things (so far: a low-battery
notice) are worth telling the phone about the moment they happen rather than
waiting for it to poll. These have no `id` - the phone tells them apart from
a response by the presence of the `push` key instead:

```json
{
  "push": "battery_low",
  "data": { "battery_percent": 12 }
}
```

| push          | data                        | sent when                                        |
|---------------|-----------------------------|---------------------------------------------------|
| `battery_low` | `{ "battery_percent": 12 }` | the battery drops to 15% or below while unplugged, once per episode (not repeated every check until it recovers) |

### Actions

| action           | params                     | description                                   |
|------------------|----------------------------|------------------------------------------------|
| `ping`           | –                          | connectivity check, PC replies with `pong`      |
| `shutdown`       | `{ "delay_seconds": 0 }`   | shuts the PC down                               |
| `restart`        | `{ "delay_seconds": 0 }`   | restarts the PC                                 |
| `cancel_shutdown`| –                          | cancels a pending shutdown/restart               |
| `sleep`          | –                          | puts the PC to sleep                            |
| `lock`           | –                          | locks the current session                       |
| `wifi_set`       | `{ "enabled": true }`      | turns the wifi radio on/off                     |
| `bluetooth_set`  | `{ "enabled": true }`      | turns the bluetooth radio on/off                |
| `radio_status`   | –                          | returns the wifi/bluetooth radios' actual on/off state |
| `apps_list`      | –                          | returns installed/known-launchable apps         |
| `app_launch`     | `{ "app_id": "..." }`      | launches an app returned by `apps_list`         |
| `system_stats`   | –                          | returns CPU %, RAM %, system-drive disk used % and free GB (`null` if unreadable), uptime in seconds, and battery info |
| `mac_address`    | –                          | returns `{ "mac": "AA:BB:CC:DD:EE:FF" }`, the MAC of the adapter on the LAN. The phone stores it so it can send a Wake-on-LAN magic packet (UDP broadcast, port 9) when the PC is off - the server can't help with the wake itself |
| `volume_up`      | –                          | presses the volume-up media key                 |
| `volume_down`    | –                          | presses the volume-down media key                |
| `volume_mute_toggle` | –                      | presses the mute media key                       |
| `media_play_pause` | –                        | presses the play/pause media key                 |
| `media_next`     | –                          | presses the next-track media key                 |
| `media_previous` | –                          | presses the previous-track media key             |
| `list_dir`       | `{ "path": "" }`           | lists a directory's contents (empty path = drives) |
| `download_file`  | `{ "path": "..." }`        | returns a file's bytes, base64-encoded (15MB limit) |
| `upload_file`    | `{ "dir": "...", "name": "...", "data_base64": "..." }` | writes a base64-encoded file into `dir` (15MB limit) |
| `create_folder`  | `{ "dir": "...", "name": "..." }` | creates a folder called `name` (a bare name, not a path) inside `dir`; returns its `path`. Errors if it already exists |
| `rename_path`    | `{ "path": "...", "new_name": "..." }` | renames a file or folder within its current folder (`new_name` is a bare name); returns the new `path`. Never overwrites, and won't rename a drive |
| `delete_path`    | `{ "path": "..." }`        | moves a file or folder to the Recycle Bin - never a permanent delete - and won't touch a drive. If Windows can't recycle something it prompts on the PC before deleting it permanently |
| `clipboard_get`  | –                          | returns the PC clipboard's text as `{ "text": "...", "truncated": false }` (empty text if it holds no text; capped at 100,000 characters) |
| `clipboard_set`  | `{ "text": "..." }`        | replaces the PC clipboard's contents with the given text (100,000 character limit) |
| `screenshot`     | `{ "max_width": 1280 }` (optional) | captures the primary monitor as a JPEG: `{ "width": 1280, "height": 720, "data_base64": "..." }`; downscaled to `max_width` (clamped to 320-1920) |
| `process_list`   | –                          | returns the running processes, biggest memory use first, as `{ "processes": [{ "pid": 1234, "name": "chrome.exe", "memory_mb": 210.5 }] }` (top 200) |
| `process_kill`   | `{ "pid": 1234, "name": "chrome.exe" }` (`name` optional) | force-ends a process. If `name` is given and the pid no longer has that name (stale list, recycled pid) it refuses. Also refuses the server itself and critical Windows processes (csrss, winlogon, lsass, ...) |
| `brightness_get` | –                          | returns the built-in display's brightness as `{ "brightness": 40 }` (0-100); errors on displays that don't expose WMI brightness (most external monitors) |
| `brightness_set` | `{ "level": 40 }`          | sets the built-in display's brightness (integer 0-100) |
| `keyboard_hotkey`| `{ "keys": ["ctrl", "c"] }` | presses the keys in order and releases them in reverse (1-4 keys). Names: `ctrl`, `alt`, `shift`, `win`, letters, digits, `f1`-`f12`, enter, backspace, tab, escape, space, delete, insert, home, end, pageup, pagedown, arrows, printscreen |
| `mouse_move`     | `{ "dx": 0, "dy": 0 }`     | moves the cursor relative to its current position |
| `mouse_click`    | `{ "button": "left", "count": 1 }` | clicks the left or right mouse button; `count` (1-3, optional) repeats the click on the PC so a double-click lands inside the OS's double-click interval |
| `mouse_button`   | `{ "button": "left", "state": "down" }` | presses (`down`) or releases (`up`) a mouse button on its own - down, `mouse_move`s, up is a drag. Any button still held when the last phone disconnects is released automatically |
| `mouse_scroll`   | `{ "ticks": 0 }`           | scrolls the wheel (positive = down, negative = up) |
| `keyboard_type`  | `{ "text": "..." }`        | types the given text (layout-independent)       |
| `keyboard_key`   | `{ "key": "enter" }`       | presses a named key (enter, backspace, tab, escape, space) |

This table is the contract both sides code against, so it's kept up to date whenever an action is added or changed. Adding one means a handler with `@register("name")` in a module under `pc-server/server/actions/` (imported in `actions/__init__.py` if the module is new), a row here, and the matching call on the Android side.

## Repo layout

```
GestureLink/
├── pc-server/       # Python service: FastAPI app, command handlers, tray icon, tests, packaging
├── android-app/     # Kotlin/Compose app: UI, WebSocket client, pairing/storage, unit tests
├── docs/            # this file
└── .github/         # CI: pc-server tests (pytest) and the Android build + unit tests
```
